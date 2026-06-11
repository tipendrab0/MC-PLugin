package com.mha.plugin.listener;

import com.mha.plugin.MHAPlugin;
import com.mha.plugin.util.TextUtil;
import com.mha.plugin.destruction.DestructionManager;
import com.mha.plugin.destruction.DestructionSession;
import com.mha.plugin.qte.QTEManager;
import com.mha.plugin.qte.QTESequence;
import com.mha.plugin.quirk.Quirk;
import com.mha.plugin.quirk.QuirkManager;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.quirk.impl.IceFireQuirk;
import com.mha.plugin.reputation.ReputationManager;
import com.mha.plugin.stamina.StaminaManager;
import com.mha.plugin.util.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for Quirk activation events.
 * Handles right-click and left-click triggers with QTE integration.
 */
public final class QuirkActivationListener implements Listener {

    private final QuirkManager quirkManager;
    private final StaminaManager staminaManager;
    private final ConfigManager config;
    private final QTEManager qteManager;
    private final DestructionManager destructionManager;
    private final ReputationManager reputationManager;
    private final MHAPlugin plugin;
    private final Map<UUID, Long> lastActivationTime;
    private final Map<UUID, DestructionSession> activeDestructionSessions;
    private final Map<UUID, QTESequence> pendingQTE;
    private final long cooldownDisplayThreshold;

    public QuirkActivationListener(final QuirkManager quirkManager, final StaminaManager staminaManager,
                                    final ConfigManager config) {
        this.quirkManager = quirkManager;
        this.staminaManager = staminaManager;
        this.config = config;
        this.plugin = (MHAPlugin) Bukkit.getPluginManager().getPlugin("MHAPlugin");
        this.qteManager = plugin.getQteManager();
        this.destructionManager = plugin.getDestructionManager();
        this.reputationManager = plugin.getReputationManager();
        this.lastActivationTime = new ConcurrentHashMap<>();
        this.activeDestructionSessions = new ConcurrentHashMap<>();
        this.pendingQTE = new ConcurrentHashMap<>();
        this.cooldownDisplayThreshold = 50;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        final Player player = event.getPlayer();

        if (!player.hasPermission("mha.use.quirk")) {
            return;
        }

        // Check if holding any item besides air
        if (event.getItem() != null && !event.getItem().getType().isAir()) {
            return;
        }

        final Action action = event.getAction();

        // Sneak + click for ultimate move (QTE-enabled)
        if (player.isSneaking()) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                handleUltimateMove(player);
                event.setCancelled(true);
            }
            return;
        }

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            handleAction(player, IceFireQuirk.Action.FIRE, event);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            handleAction(player, null, event);
        }
    }

    /**
     * Handle melee attacks on entities - triggers quirks that require hitting a target.
     * This allows quirks to activate when punching mobs/players directly.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerAttackEntity(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        if (!player.hasPermission("mha.use.quirk")) {
            return;
        }

        // Only trigger quirks on empty hand attacks
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            return;
        }

        // Don't process if already handled or cancelled
        if (event.isCancelled()) {
            return;
        }

        final Quirk quirk = quirkManager.getPlayerQuirk(player);
        if (quirk == null) {
            return;
        }

        // Some quirks should trigger on punch (Zero Gravity, Transformation, etc.)
        // Others are activated via click - this is for contact-based quirks
        switch (quirk.getType()) {
            case ZERO_GRAVITY:
            case TRANSFORMATION:
            case BLOODCURDLE:
            case DECAY:
            case HARDENING:
                // These quirks have their own EntityDamageByEntityEvent handlers
                // Don't interfere - let their internal listeners handle it
                break;
            case ONE_FOR_ALL:
                // One For All gets passive punch damage boost from QuirkPassiveListener
                // But here we can trigger an active ability punch
                if (player.isSneaking()) {
                    // Ultimate punch - handled elsewhere
                }
                break;
            default:
                break;
        }
    }

    /**
     * Handle ultimate move activation with QTE.
     */
    private void handleUltimateMove(final Player player) {
        if (isRateLimited(player)) {
            return;
        }

        final Quirk quirk = quirkManager.getPlayerQuirk(player);
        if (quirk == null) {
            TextUtil.actionBar(player, config.getString("messages.prefix", "") + config.getString("messages.no-quirk", "No Quirk assigned"));
            lastActivationTime.put(player.getUniqueId(), System.currentTimeMillis());
            return;
        }

        // Check if ultimate is enabled for this Quirk
        if (!config.getBoolean("quirks." + quirk.getType().getId() + ".ultimate.enabled", true)) {
            TextUtil.actionBar(player, "§cUltimate move not available for this Quirk!");
            return;
        }

        // Check reputation requirement
        if (!reputationManager.canUseUltimateQuirk(player)) {
            if (!player.hasPermission("mha.admin.bypass-rank")) {
                TextUtil.actionBar(player, "§cYou need Hero rank to use Ultimate moves!");
                return;
            }
        }

        final double ultimateStaminaMultiplier = config.getDouble("qte.ultimate-stamina-multiplier", 2.0);
        final int ultimateStaminaCost = (int) Math.ceil(quirk.getStaminaCost() * ultimateStaminaMultiplier);

        if (staminaManager.isExhausted(player)) {
            TextUtil.actionBar(player, config.getString("messages.prefix", "") + config.getString("messages.exhausted", "Exhausted!"));
            return;
        }

        if (!staminaManager.canAfford(player, ultimateStaminaCost)) {
            TextUtil.actionBar(player, config.getString("messages.prefix", "") + "§cNeed " + ultimateStaminaCost + " stamina for Ultimate!");
            return;
        }

        final long cooldownRemaining = quirk.getCooldownRemaining(player);
        if (cooldownRemaining > 0) {
            final String message = config.getString("messages.cooldown", "Cooldown: %time%s")
                    .replace("%time%", String.valueOf(cooldownRemaining / 1000 + 1));
            TextUtil.actionBar(player, config.getString("messages.prefix", "") + message);
            return;
        }

        if (!config.getBoolean("qte.enabled", true)) {
            activateUltimateDirect(player, quirk, ultimateStaminaCost);
            lastActivationTime.put(player.getUniqueId(), System.currentTimeMillis());
            return;
        }

        final QTESequence sequence = qteManager.startQTE(player);
        if (sequence == null) {
            return;
        }
        pendingQTE.put(player.getUniqueId(), sequence);

        // Schedule QTE completion check
        final Quirk finalQuirk = quirk;
        final UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Remove pending QTE
            final QTESequence seq = pendingQTE.remove(playerId);
            if (seq == null) return;

            // Check if player is still online
            final Player onlinePlayer = Bukkit.getPlayer(playerId);
            if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                return; // Player disconnected during QTE
            }

            final boolean success = qteManager.getQTEResult(onlinePlayer);
            final double multiplier = config.getDouble("qte.success-multiplier", 2.0);

            // Start destruction session for environmental effects
            final DestructionSession session = destructionManager.startSession(onlinePlayer);
            activeDestructionSessions.put(playerId, session);

            // Activate with multiplier
            onlinePlayer.setMetadata("mha_ultimate_multiplier", new org.bukkit.metadata.FixedMetadataValue(plugin, success ? multiplier : 1.0));

            try {
                if (finalQuirk.getType() == QuirkType.ICE_FIRE) {
                    final IceFireQuirk iceFire = (IceFireQuirk) finalQuirk;
                    iceFire.activate(onlinePlayer, IceFireQuirk.Action.ICE);
                } else {
                    finalQuirk.activate(onlinePlayer);
                }

                if (success) {
                    final int bonusCost = Math.max(0, ultimateStaminaCost - finalQuirk.getStaminaCost());
                    if (bonusCost > 0) {
                        staminaManager.consumeStamina(onlinePlayer, bonusCost);
                    }
                    if (config.getBoolean("qte.no-cooldown-on-success", true)) {
                        finalQuirk.resetCooldown(onlinePlayer);
                    }
                    TextUtil.actionBar(onlinePlayer, "§6§lULTIMATE SUCCESS! §e2x power unleashed!");
                }
            } finally {
                onlinePlayer.removeMetadata("mha_ultimate_multiplier", plugin);
            }

        }, (config.getInt("qte.duration-ms", 1500) / 50) + 1);

        lastActivationTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void activateUltimateDirect(final Player player, final Quirk quirk, final int ultimateStaminaCost) {
        final DestructionSession session = destructionManager.startSession(player);
        activeDestructionSessions.put(player.getUniqueId(), session);

        if (quirk.getType() == QuirkType.ICE_FIRE) {
            ((IceFireQuirk) quirk).activate(player, IceFireQuirk.Action.ICE);
        } else {
            quirk.activate(player);
        }

        final int bonusCost = Math.max(0, ultimateStaminaCost - quirk.getStaminaCost());
        if (bonusCost > 0) {
            staminaManager.consumeStamina(player, bonusCost);
        }
        quirk.resetCooldown(player);
        TextUtil.actionBar(player, "§6§lULTIMATE! §e2x power unleashed!");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            destructionManager.endSession(session.getSessionId());
            activeDestructionSessions.remove(player.getUniqueId());
        }, 20L);
    }

    /**
     * Handle a click action for a player.
     */
    private void handleAction(final Player player, final IceFireQuirk.Action fireAction, final PlayerInteractEvent event) {
        // Rate limit messages
        if (isRateLimited(player)) {
            return;
        }

        final Quirk quirk = quirkManager.getPlayerQuirk(player);

        if (quirk == null) {
            TextUtil.actionBar(player, config.getString("messages.prefix", "") + config.getString("messages.no-quirk", "No Quirk assigned"));
            event.setCancelled(true);
            lastActivationTime.put(player.getUniqueId(), System.currentTimeMillis());
            return;
        }

        if (staminaManager.isExhausted(player)) {
            TextUtil.actionBar(player, config.getString("messages.prefix", "") + config.getString("messages.exhausted", "Exhausted!"));
            event.setCancelled(true);
            lastActivationTime.put(player.getUniqueId(), System.currentTimeMillis());
            return;
        }

        final long cooldownRemaining = quirk.getCooldownRemaining(player);
        if (cooldownRemaining > 0) {
            if (config.getBoolean("settings.cooldown-display", true)) {
                final String message = config.getString("messages.cooldown", "Cooldown: %time%s")
                        .replace("%time%", String.valueOf(cooldownRemaining / 1000 + 1));
                TextUtil.actionBar(player, config.getString("messages.prefix", "") + message);
            }
            event.setCancelled(true);
            lastActivationTime.put(player.getUniqueId(), System.currentTimeMillis());
            return;
        }

        // Start destruction session
        final DestructionSession session = destructionManager.startSession(player);
        activeDestructionSessions.put(player.getUniqueId(), session);

        // Activate the Quirk
        boolean success;
        if (quirk.getType() == QuirkType.ICE_FIRE && fireAction != null) {
            final IceFireQuirk iceFireQuirk = (IceFireQuirk) quirk;
            success = iceFireQuirk.activate(player, fireAction);
        } else {
            success = quirk.activate(player);
        }

        // End destruction session after a short delay
        final UUID sessionId = session.getSessionId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            destructionManager.endSession(sessionId);
            activeDestructionSessions.remove(player.getUniqueId());
        }, 20L);

        if (success) {
            event.setCancelled(true);
            lastActivationTime.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    /**
     * Check if player is rate-limited for messages.
     */
    private boolean isRateLimited(final Player player) {
        final Long lastTime = lastActivationTime.get(player.getUniqueId());
        return lastTime != null && System.currentTimeMillis() - lastTime < cooldownDisplayThreshold;
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        quirkManager.onPlayerQuit(event.getPlayer().getUniqueId());
        lastActivationTime.remove(event.getPlayer().getUniqueId());
        pendingQTE.remove(event.getPlayer().getUniqueId());

        final DestructionSession session = activeDestructionSessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            destructionManager.endSession(session.getSessionId());
        }
    }
}
