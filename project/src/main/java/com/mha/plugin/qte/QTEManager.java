package com.mha.plugin.qte;

import com.mha.plugin.util.ConfigManager;
import com.mha.plugin.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quick Time Event Manager for Ultimate Moves.
 */
public final class QTEManager implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Map<UUID, QTEState> activeQTEs;
    private final Map<UUID, QTESequence> completedQTEs;
    private final Random random;

    public QTEManager(final JavaPlugin plugin, final ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.activeQTEs = new ConcurrentHashMap<>();
        this.completedQTEs = new ConcurrentHashMap<>();
        this.random = new Random();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean isEnabled() {
        return config.getBoolean("qte.enabled", true);
    }

    public long getDurationMs() {
        return config.getInt("qte.duration-ms", 1500);
    }

    public int getMinKeys() {
        return Math.max(1, config.getInt("qte.min-keys", 3));
    }

    public int getMaxKeys() {
        return Math.max(getMinKeys(), config.getInt("qte.max-keys", 5));
    }

    public QTESequence startQTE(final Player player) {
        if (!isEnabled()) {
            return null;
        }

        final int minKeys = getMinKeys();
        final int maxKeys = getMaxKeys();
        final int keyCount = minKeys + random.nextInt(maxKeys - minKeys + 1);
        final List<QTEKey> keys = generateSequence(keyCount);
        final long startTime = System.currentTimeMillis();
        final long duration = getDurationMs();

        final QTESequence sequence = new QTESequence(keys, startTime, duration);
        activeQTEs.put(player.getUniqueId(), new QTEState(sequence, 0));

        displayQTE(player, sequence);
        return sequence;
    }

    private List<QTEKey> generateSequence(final int count) {
        final List<QTEKey> keys = new ArrayList<>();
        final QTEKey[] available = QTEKey.values();

        for (int i = 0; i < count; i++) {
            keys.add(available[random.nextInt(available.length)]);
        }

        return keys;
    }

    private void displayQTE(final Player player, final QTESequence sequence) {
        final StringBuilder display = new StringBuilder();
        display.append("\n§6§l=== ULTIMATE MOVE ===\n");
        display.append("§eSequence: §f");

        for (final QTEKey key : sequence.getKeys()) {
            display.append(key.getDisplay()).append(" ");
        }

        display.append("\n§7(Match the sequence with WASD, Jump, Sneak!)");
        player.sendMessage(display.toString());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
    }

    public boolean handleKeyPress(final Player player, final QTEKey key) {
        final QTEState state = activeQTEs.get(player.getUniqueId());
        if (state == null) {
            return false;
        }

        final QTESequence sequence = state.getSequence();
        final List<QTEKey> keys = sequence.getKeys();
        final int currentIndex = state.getCurrentIndex();

        if (System.currentTimeMillis() > sequence.getEndTime()) {
            endQTE(player, false);
            return false;
        }

        if (keys.get(currentIndex) == key) {
            state.incrementIndex();
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.2f);
            TextUtil.actionBar(player, "§a" + "★".repeat(state.getCurrentIndex()) + "§7" + "☆".repeat(keys.size() - state.getCurrentIndex()));

            if (state.isComplete()) {
                endQTE(player, true);
                return true;
            }
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            endQTE(player, false);
        }

        return true;
    }

    public boolean hasActiveQTE(final Player player) {
        final QTEState state = activeQTEs.get(player.getUniqueId());
        return state != null && System.currentTimeMillis() <= state.getSequence().getEndTime();
    }

    public boolean getQTEResult(final Player player) {
        final QTEState state = activeQTEs.remove(player.getUniqueId());
        if (state == null) {
            final QTESequence completed = completedQTEs.remove(player.getUniqueId());
            return completed != null;
        }
        return state.isComplete();
    }

    private void endQTE(final Player player, final boolean success) {
        final QTEState state = activeQTEs.remove(player.getUniqueId());

        if (success) {
            player.sendMessage("§a§lQTE SUCCESS! §eUltimate Move powered up!");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            if (state != null) {
                completedQTEs.put(player.getUniqueId(), state.getSequence());
            }
        } else {
            player.sendMessage("§c§lQTE FAILED! §7Normal ability used.");
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 1.0f, 0.5f);
        }
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        activeQTEs.remove(event.getPlayer().getUniqueId());
        completedQTEs.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerMove(final PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        final QTEState state = activeQTEs.get(player.getUniqueId());

        if (state == null || !state.isActive()) {
            return;
        }

        final var from = event.getFrom();
        final var to = event.getTo();
        if (to == null) {
            return;
        }

        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        final double dx = to.getX() - from.getX();
        final double dz = to.getZ() - from.getZ();
        if (Math.abs(dx) < 0.05 && Math.abs(dz) < 0.05) {
            return;
        }

        final QTEKey movement = getMovementKeyRelativeToPlayer(player, dx, dz);
        if (movement != null) {
            handleKeyPress(player, movement);
        }
    }

    private QTEKey getMovementKeyRelativeToPlayer(final Player player, final double dx, final double dz) {
        final float yaw = player.getLocation().getYaw();
        final double yawRad = Math.toRadians(yaw < 0 ? yaw + 360 : yaw);
        final double forwardX = -Math.sin(yawRad);
        final double forwardZ = Math.cos(yawRad);
        final double rightX = Math.cos(yawRad);
        final double rightZ = Math.sin(yawRad);
        final double forwardAmount = dx * forwardX + dz * forwardZ;
        final double rightAmount = dx * rightX + dz * rightZ;

        if (Math.abs(forwardAmount) > Math.abs(rightAmount)) {
            return forwardAmount > 0 ? QTEKey.FORWARD : QTEKey.BACKWARD;
        }
        return rightAmount > 0 ? QTEKey.RIGHT : QTEKey.LEFT;
    }

    @EventHandler
    public void onPlayerJump(final PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        final QTEState state = activeQTEs.get(player.getUniqueId());
        if (state == null || !state.isActive()) {
            return;
        }

        final var from = event.getFrom();
        final var to = event.getTo();
        if (to == null) {
            return;
        }

        if (to.getY() > from.getY() + 0.5 && (from.getBlock().isLiquid() || from.getBlock().getType().isAir())) {
            if (!player.isFlying() && !player.isGliding()) {
                handleKeyPress(player, QTEKey.JUMP);
            }
        }
    }

    @EventHandler
    public void onPlayerSneak(final PlayerToggleSneakEvent event) {
        final Player player = event.getPlayer();
        if (activeQTEs.containsKey(player.getUniqueId()) && event.isSneaking()) {
            handleKeyPress(player, QTEKey.SNEAK);
        }
    }

    @EventHandler
    public void onPlayerSprint(final PlayerToggleSprintEvent event) {
        final Player player = event.getPlayer();
        if (activeQTEs.containsKey(player.getUniqueId()) && event.isSprinting()) {
            handleKeyPress(player, QTEKey.FORWARD);
        }
    }

    public void shutdown() {
        activeQTEs.clear();
        completedQTEs.clear();
    }
}
