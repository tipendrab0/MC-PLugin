package com.mha.plugin.quirk.impl;

import com.mha.plugin.quirk.Quirk;
import com.mha.plugin.util.TextUtil;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.stamina.StaminaManager;
import com.mha.plugin.util.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transformation Quirk - Toga Himiko's ability.
 * Disguise as another entity and gain their abilities temporarily.
 */
public final class TransformationQuirk extends Quirk {

    private final int transformDuration;
    private final double healthRequired;
    private final Map<UUID, DisguiseInfo> activeDisguises;

    public TransformationQuirk(final ConfigManager config, final StaminaManager staminaManager) {
        super(QuirkType.TRANSFORMATION, config, staminaManager);

        this.transformDuration = getConfigInt("duration-ticks", 600);
        this.healthRequired = getConfigDouble("health-required", 3.0);
        this.activeDisguises = new ConcurrentHashMap<>();
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

        // Check if already transformed
        if (activeDisguises.containsKey(player.getUniqueId())) {
            endTransform(player);
            return true;
        }

        // Find target entity to copy
        final Entity target = findTarget(player);

        if (target == null) {
            TextUtil.actionBar(player, "§cNo target nearby to copy!");
            resetCooldown(player);
            return false;
        }

        // Consume health (blood drinking)
        if (player.getHealth() < healthRequired) {
            TextUtil.actionBar(player, "§cNot enough health to transform!");
            resetCooldown(player);
            return false;
        }

        player.setHealth(player.getHealth() - healthRequired);

        // Start transformation
        transform(player, target);

        startCooldown(player);
        return true;
    }

    /**
     * Find a nearby entity to copy.
     */
    private Entity findTarget(final Player player) {
        return player.getNearbyEntities(5, 5, 5).stream()
                .filter(e -> e instanceof LivingEntity)
                .filter(e -> e != player)
                .findFirst()
                .orElse(null);
    }

    /**
     * Transform into disguise.
     */
    private void transform(final Player player, final Entity target) {
        final String disguiseName = target.getName() != null ? target.getName() : target.getType().name();
        final EntityType disguiseType = target.getType();

        activeDisguises.put(player.getUniqueId(), new DisguiseInfo(disguiseType, disguiseName, transformDuration));

        // Visual transformation effect
        player.getWorld().spawnParticle(Particle.GUST, player.getLocation().add(0, 1, 0), 50, 0.5, 1.0, 0.5, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1.0f, 0.7f);

        // Send disguise message
        player.sendTitle("§dTRANSFORMED!", "§7Now appearing as: §f" + disguiseName, 5, 40, 5);
        player.sendMessage("§d>>> TRANSFORMED into " + disguiseName + " (" + disguiseType.name() + ") <<<");

        // Timer to end transformation
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeDisguises.containsKey(player.getUniqueId())) {
                    endTransform(player);
                }
            }
        }.runTaskLater(org.bukkit.Bukkit.getPluginManager().getPlugin("MHAPlugin"), transformDuration);

        // Periodic disguise particles
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!activeDisguises.containsKey(player.getUniqueId()) || !player.isOnline() || ticks++ > transformDuration) {
                    cancel();
                    return;
                }

                // Subtle disguise particles
                if (ticks % 20 == 0) {
                    player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 0.5, 0), 5, 0.3, 0.5, 0.3, 0);
                }

                // Show disguise name to others
                player.setDisplayName(disguiseName);
            }
        }.runTaskTimer(org.bukkit.Bukkit.getPluginManager().getPlugin("MHAPlugin"), 0L, 20L);
    }

    /**
     * End transformation.
     */
    private void endTransform(final Player player) {
        final DisguiseInfo info = activeDisguises.remove(player.getUniqueId());
        if (info != null) {
            // Revert display name
            player.setDisplayName(player.getName());

            // Visual effect
            player.getWorld().spawnParticle(Particle.POOF, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            player.playSound(player.getLocation(), Sound.ENTITY_FOX_SNIFF, 1.0f, 1.0f);

            player.sendMessage("§7>>> Transformation ended <<<");
            TextUtil.actionBar(player, "§eTransformation ended!");
        }
    }

    /**
     * Check if player is transformed.
     */
    public boolean isTransformed(final UUID playerId) {
        return activeDisguises.containsKey(playerId);
    }

    @Override
    public void clearAllCooldowns() {
        activeDisguises.clear();
        super.clearAllCooldowns();
    }

    /**
     * Disguise information.
     */
    private record DisguiseInfo(EntityType type, String name, int duration) {}
}
