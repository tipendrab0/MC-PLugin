package com.mha.plugin.destruction;

import com.mha.plugin.util.ConfigManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks environmental destruction from Quirks and restores blocks over time.
 */
public final class DestructionManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Map<UUID, DestructionSession> sessionsById;
    private final Map<UUID, UUID> activeSessionByPlayer;
    private final Set<String> protectedLocations;
    private BukkitRunnable restoreTask;

    public DestructionManager(final JavaPlugin plugin, final ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.sessionsById = new ConcurrentHashMap<>();
        this.activeSessionByPlayer = new ConcurrentHashMap<>();
        this.protectedLocations = ConcurrentHashMap.newKeySet();

        startRestoreTask();
    }

    public boolean isEnabled() {
        return config.getBoolean("destruction.enabled", true);
    }

    /**
     * Start a destruction tracking session for a Quirk activation.
     */
    public DestructionSession startSession(final Player player) {
        final DestructionSession session = new DestructionSession(player.getUniqueId());
        sessionsById.put(session.getSessionId(), session);
        activeSessionByPlayer.put(player.getUniqueId(), session.getSessionId());
        return session;
    }

    /**
     * Get the active session for a player, if any.
     */
    public DestructionSession getActiveSession(final Player player) {
        final UUID sessionId = activeSessionByPlayer.get(player.getUniqueId());
        return sessionId != null ? sessionsById.get(sessionId) : null;
    }

    /**
     * Record a block before it is changed during an active session.
     */
    public void recordBlockChange(final Player player, final Block block) {
        if (!isEnabled() || block == null || isProtected(block.getLocation())) {
            return;
        }

        final DestructionSession session = getActiveSession(player);
        if (session != null) {
            session.recordBlock(block.getState());
        }
    }

    /**
     * End a session and schedule restoration of its snapshots.
     */
    public void endSession(final UUID sessionId) {
        final DestructionSession session = sessionsById.remove(sessionId);
        if (session == null) {
            return;
        }

        activeSessionByPlayer.remove(session.getPlayerId());

        if (!isEnabled() || session.isEmpty()) {
            return;
        }

        scheduleRestore(session);
    }

    /**
     * Immediately restore all pending destruction.
     */
    public int restoreAll() {
        int restored = 0;

        for (final DestructionSession session : new ArrayList<>(sessionsById.values())) {
            restored += restoreSession(session);
            sessionsById.remove(session.getSessionId());
            activeSessionByPlayer.remove(session.getPlayerId());
        }

        return restored;
    }

    public boolean protectLocation(final Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return protectedLocations.add(locationKey(location));
    }

    public boolean unprotectLocation(final Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return protectedLocations.remove(locationKey(location));
    }

    public boolean isProtected(final Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        if (protectedLocations.contains(locationKey(location))) {
            return true;
        }

        return location.getWorld().getSpawnLocation().distanceSquared(location) < 100;
    }

    public void shutdown() {
        if (restoreTask != null) {
            restoreTask.cancel();
        }
        restoreAll();
        sessionsById.clear();
        activeSessionByPlayer.clear();
    }

    private void scheduleRestore(final DestructionSession session) {
        final long delayMinutes = config.getInt("destruction.restore-delay-minutes", 5);
        final long delayTicks = delayMinutes * 60L * 20L;

        new BukkitRunnable() {
            @Override
            public void run() {
                restoreSession(session);
            }
        }.runTaskLater(plugin, delayTicks);
    }

    private int restoreSession(final DestructionSession session) {
        int restored = 0;
        for (final BlockSnapshot snapshot : session.getSnapshots()) {
            snapshot.restore();
            restored++;
        }
        return restored;
    }

    private void startRestoreTask() {
        final int blocksPerTick = config.getInt("destruction.blocks-per-restore-tick", 5);
        if (blocksPerTick <= 0) {
            return;
        }

        restoreTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Periodic task reserved for gradual restoration if needed later.
            }
        };
        restoreTask.runTaskTimer(plugin, 20L, 20L);
    }

    private static String locationKey(final Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
