package com.mha.plugin.quirk.impl;

import com.mha.plugin.quirk.Quirk;
import com.mha.plugin.util.TextUtil;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.stamina.StaminaManager;
import com.mha.plugin.util.ConfigManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Permeation Quirk - Mirio Togata's ability.
 * Phase through solid matter and become intangible.
 * Drawback: Cannot attack or be attacked while active.
 */
public final class PermeationQuirk extends Quirk implements Listener {

    private final int maxDuration;
    private final int staminaPerSecond;
    private final int recoveryDelay;
    private final Map<UUID, PermeationState> activeStates;

    public PermeationQuirk(final ConfigManager config, final StaminaManager staminaManager) {
        super(QuirkType.PERMEATION, config, staminaManager);

        this.maxDuration = getConfigInt("max-duration-ticks", 100);
        this.staminaPerSecond = getConfigInt("stamina-per-second", 20);
        this.recoveryDelay = getConfigInt("recovery-delay-ticks", 20);
        this.activeStates = new ConcurrentHashMap<>();

    }

    @Override
    public boolean activate(final Player player) {
        if (!canUse(player)) {
            return false;
        }

        if (!consumeStamina(player)) {
            TextUtil.actionBar(player, "Not enough stamina!");
            return false;
        }

        // Toggle permeation on/off
        if (activeStates.containsKey(player.getUniqueId())) {
            deactivatePermeation(player);
        } else {
            activatePermeation(player);
        }

        startCooldown(player);
        return true;
    }

    /**
     * Activate intangibility phase.
     */
    private void activatePermeation(final Player player) {
        final PermeationState state = new PermeationState(maxDuration);
        activeStates.put(player.getUniqueId(), state);

        // Apply permeation effects
        player.setInvulnerable(true);
        player.setCollidable(false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, maxDuration, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, maxDuration, 0, false, false));

        // Allow clipping through walls
        player.setAllowFlight(true);
        player.setFlying(true);

        // Visual effect
        player.getWorld().spawnParticle(Particle.GUST, player.getLocation().add(0, 1, 0), 100, 0.5, 1.0, 0.5, 0.1);
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 2.0f, 1.5f);

        TextUtil.actionBar(player, "§d§lPERMEATION: §bON §7(Intangible)");
        player.sendTitle("§d", "§7Phasing through matter...", 0, 20, 5);

        // Stamina drain tick
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!activeStates.containsKey(player.getUniqueId()) || !player.isOnline() || ticks++ > maxDuration) {
                    if (activeStates.containsKey(player.getUniqueId())) {
                        deactivatePermeation(player);
                    }
                    cancel();
                    return;
                }

                // Drain stamina
                if (ticks % 20 == 0) {
                    if (!staminaManager.consumeStamina(player, staminaPerSecond)) {
                        deactivatePermeation(player);
                        cancel();
                    }
                }

                // Particle trail
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0);
                player.getWorld().spawnParticle(Particle.GUST_EMITTER_SMALL, player.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);

                state.tick();
            }
        }.runTaskTimer(org.bukkit.Bukkit.getPluginManager().getPlugin("MHAPlugin"), 0L, 1L);
    }

    /**
     * Deactivate intangibility.
     */
    private void deactivatePermeation(final Player player) {
        activeStates.remove(player.getUniqueId());

        player.setInvulnerable(false);
        player.setCollidable(true);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);

        // Visual effect
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0);
        player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 2.0f, 1.0f);

        // Brief vulnerability window
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, recoveryDelay, 2, false, false));

        TextUtil.actionBar(player, "§c§lPERMEATION: OFF §7(Solid)");
    }

    /**
     * Check if player is currently intangible.
     */
    public boolean isIntangible(final UUID playerId) {
        return activeStates.containsKey(playerId);
    }

    /**
     * Handle damage events - cancel if intangible.
     */
    @EventHandler
    public void onEntityDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (isIntangible(player.getUniqueId())) {
            event.setCancelled(true);
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0);
        }
    }

    @Override
    public void clearAllCooldowns() {
        activeStates.clear();
        super.clearAllCooldowns();
    }

    /**
     * Shutdown cleanup.
     */
    public void shutdown() {
        // Deactivate all active permeations
        for (final UUID playerId : activeStates.keySet()) {
            final Player player = org.bukkit.Bukkit.getPlayer(playerId);
            if (player != null) deactivatePermeation(player);
        }
        activeStates.clear();
    }

    /**
     * Permeation state tracking.
     */
    private static class PermeationState {
        private int remainingTicks;

        PermeationState(final int duration) {
            this.remainingTicks = duration;
        }

        void tick() {
            remainingTicks--;
        }

        int getRemainingTicks() {
            return remainingTicks;
        }
    }
}
