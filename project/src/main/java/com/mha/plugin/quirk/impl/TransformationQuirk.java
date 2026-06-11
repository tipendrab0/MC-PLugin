package com.mha.plugin.quirk.impl;

import com.mha.plugin.quirk.Quirk;
import com.mha.plugin.util.TextUtil;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.stamina.StaminaManager;
import com.mha.plugin.util.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transformation Quirk - Toga Himiko's ability.
 * Disguise as another entity and gain their abilities temporarily.
 *
 * FULL MODEL TRANSFORMATION:
 * To enable actual 3D model transformation (turning into a spider, zombie, etc.),
 * install LibsDisguises plugin on your server (requires ProtocolLib).
 *
 * Without LibsDisguises, this quirk only changes the display name and creates particles.
 * With LibsDisguises installed, players will visually transform into the target mob.
 */
public final class TransformationQuirk extends Quirk {

    private final int transformDuration;
    private final double healthRequired;
    private final double maxTargetRange;
    private final Map<UUID, DisguiseInfo> activeDisguises;
    private static boolean libsDisguisesAvailable = false;

    static {
        // Check if LibsDisguises is available at runtime
        try {
            Class.forName("me.libraryaddict.disguise.DisguiseAPI");
            libsDisguisesAvailable = true;
        } catch (ClassNotFoundException e) {
            libsDisguisesAvailable = false;
        }
    }

    public TransformationQuirk(final ConfigManager config, final StaminaManager staminaManager) {
        super(QuirkType.TRANSFORMATION, config, staminaManager);

        this.transformDuration = getConfigInt("duration-ticks", 600);
        this.healthRequired = getConfigDouble("health-required", 3.0);
        this.maxTargetRange = getConfigDouble("max-target-range", 10.0);
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
     * Find a nearby entity that the player is looking at (line of sight targeting).
     * This ensures players transform into the intended target, not random mobs.
     */
    private Entity findTarget(final Player player) {
        final Location eyeLocation = player.getEyeLocation();
        final Vector direction = eyeLocation.getDirection();
        final double maxDistance = maxTargetRange;

        Entity closestEntity = null;
        double closestDistance = maxDistance;
        double bestDot = 0.95; // Must be looking nearly directly at target

        for (final Entity entity : player.getNearbyEntities(maxDistance, maxDistance, maxDistance)) {
            if (!(entity instanceof LivingEntity) || entity.equals(player)) {
                continue;
            }

            // Check if entity is in the player's line of sight
            final Vector toEntity = entity.getLocation().toVector().subtract(eyeLocation.toVector());
            final double distance = toEntity.length();
            final double dot = direction.dot(toEntity.normalize());

            // Prefer the entity the player is most directly looking at
            if (dot > bestDot && distance < closestDistance) {
                closestEntity = entity;
                closestDistance = distance;
                bestDot = dot;
            }
        }

        return closestEntity;
    }

    /**
     * Transform into disguise.
     * Uses LibsDisguises if available, otherwise just changes display name.
     */
    private void transform(final Player player, final Entity target) {
        final String disguiseName = target.getName() != null ? target.getName() : target.getType().name();
        final EntityType disguiseType = target.getType();

        activeDisguises.put(player.getUniqueId(), new DisguiseInfo(disguiseType, disguiseName, transformDuration));

        // Apply LibsDisguises transformation if available
        if (libsDisguisesAvailable) {
            applyLibsDisguise(player, target);
        }

        // Visual transformation effect
        player.getWorld().spawnParticle(Particle.GUST, player.getLocation().add(0, 1, 0), 50, 0.5, 1.0, 0.5, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1.0f, 0.7f);

        // Send disguise message
        player.sendTitle("§dTRANSFORMED!", "§7Now appearing as: §f" + disguiseName, 5, 40, 5);
        player.sendMessage("§d>>> TRANSFORMED into " + disguiseName + " (" + disguiseType.name() + ") <<<");

        if (libsDisguisesAvailable) {
            player.sendMessage("§aYour physical form has changed! Others see you as a " + disguiseType.name());
        } else {
            player.sendMessage("§7(Note: Install LibsDisguises plugin for full visual transformation)");
        }

        // Timer to end transformation
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeDisguises.containsKey(player.getUniqueId())) {
                    endTransform(player);
                }
            }
        }.runTaskLater(Bukkit.getPluginManager().getPlugin("MHAPlugin"), transformDuration);

        // Periodic disguise particles and name update
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

                // Update display name (fallback for servers without LibsDisguises)
                if (!libsDisguisesAvailable) {
                    player.setDisplayName(disguiseName);
                }
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("MHAPlugin"), 0L, 20L);
    }

    /**
     * Apply LibsDisguises transformation if the plugin is available.
     */
    private void applyLibsDisguise(final Player player, final Entity target) {
        try {
            // Use reflection to avoid compile-time dependency
            Class<?> disguiseTypeClass = Class.forName("me.libraryaddict.disguise.disguisetypes.DisguiseType");
            Class<?> mobDisguiseClass = Class.forName("me.libraryaddict.disguise.disguisetypes.MobDisguise");
            Class<?> disguiseAPIClass = Class.forName("me.libraryaddict.disguise.DisguiseAPI");

            // Convert Bukkit EntityType to DisguiseType
            Object[] disguiseTypes = (Object[]) disguiseTypeClass.getMethod("values").invoke(null);
            Object targetDisguiseType = null;

            for (Object dt : disguiseTypes) {
                org.bukkit.entity.EntityType bukkitType = (org.bukkit.entity.EntityType)
                        disguiseTypeClass.getMethod("getEntityType").invoke(dt);
                if (bukkitType == target.getType()) {
                    targetDisguiseType = dt;
                    break;
                }
            }

            if (targetDisguiseType != null) {
                // Create mob disguise
                Object disguise = mobDisguiseClass.getConstructor(disguiseTypeClass).newInstance(targetDisguiseType);

                // Disguise to all players
                disguiseAPIClass.getMethod("disguiseToAll", Player.class, mobDisguiseClass.getSuperclass())
                        .invoke(null, player, disguise);
            }
        } catch (Exception e) {
            player.sendMessage("§cWarning: LibsDisguises transformation failed: " + e.getMessage());
        }
    }

    /**
     * Remove LibsDisguises transformation.
     */
    private void removeLibsDisguise(final Player player) {
        if (!libsDisguisesAvailable) return;

        try {
            Class<?> disguiseAPIClass = Class.forName("me.libraryaddict.disguise.DisguiseAPI");
            disguiseAPIClass.getMethod("undisguiseToAll", Player.class).invoke(null, player);
        } catch (Exception ignored) {
        }
    }

    /**
     * End transformation and restore player's original appearance.
     */
    private void endTransform(final Player player) {
        final DisguiseInfo info = activeDisguises.remove(player.getUniqueId());
        if (info != null) {
            // Remove LibsDisguises disguise if active
            removeLibsDisguise(player);

            // Revert display name
            player.setDisplayName(player.getName());

            // Also reset player list name
            player.playerListName(net.kyori.adventure.text.Component.text(player.getName()));

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
