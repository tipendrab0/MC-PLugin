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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zero Gravity Quirk - Uraraka Ochaco's ability.
 * Removes gravity from targets causing them to float.
 */
public final class ZeroGravityQuirk extends Quirk implements Listener {

    private final int duration;
    private final int maxTargets;
    private final boolean fallDamageImmune;
    private final Map<UUID, Long> floatingTargets;
    private final Map<UUID, UUID> activators;
    private final Map<UUID, BukkitRunnable> activeTasks;

    public ZeroGravityQuirk(final ConfigManager config, final StaminaManager staminaManager) {
        super(QuirkType.ZERO_GRAVITY, config, staminaManager);

        this.duration = getConfigInt("duration", 10000);
        this.maxTargets = getConfigInt("max-targets", 3);
        this.fallDamageImmune = getConfigBoolean("fall-damage-immune", true);
        this.floatingTargets = new ConcurrentHashMap<>();
        this.activators = new ConcurrentHashMap<>();
        this.activeTasks = new ConcurrentHashMap<>();
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

        final Entity target = getTargetEntity(player);

        if (target == null) {
            TextUtil.actionBar(player, "No target found!");
            resetCooldown(player);
            return false;
        }

        if (floatingTargets.containsKey(target.getUniqueId())) {
            TextUtil.actionBar(player, "Target is already floating!");
            resetCooldown(player);
            return false;
        }

        makeTargetFloat(player, target);

        startCooldown(player);
        return true;
    }

    /**
     * Make the target entity float.
     */
    private void makeTargetFloat(final Player activator, final Entity target) {
        floatingTargets.put(target.getUniqueId(), System.currentTimeMillis() + duration);
        activators.put(target.getUniqueId(), activator.getUniqueId());

        // Visual effect
        target.getWorld().spawnParticle(
                Particle.END_ROD,
                target.getLocation().add(0, target.getHeight() / 2, 0),
                50,
                0.5, 0.5, 0.5, 0.1
        );
        target.getWorld().spawnParticle(
                Particle.REVERSE_PORTAL,
                target.getLocation().add(0, target.getHeight() / 2, 0),
                30,
                0.3, 0.3, 0.3, 0.05
        );

        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_SHULKER_TELEPORT, 1.0f, 1.5f);

        // Schedule float effect
        final BukkitRunnable floatTask = new BukkitRunnable() {
            @Override
            public void run() {
                final Long endTime = floatingTargets.get(target.getUniqueId());
                if (endTime == null || !target.isValid() || System.currentTimeMillis() > endTime) {
                    if (target instanceof LivingEntity living) {
                        endLevitation(living);
                    }
                    floatingTargets.remove(target.getUniqueId());
                    activators.remove(target.getUniqueId());
                    activeTasks.remove(target.getUniqueId());
                    cancel();
                    return;
                }

                if (target instanceof LivingEntity living) {
                    applyFloatEffect(living);
                }
            }
        };
        activeTasks.put(target.getUniqueId(), floatTask);
        floatTask.runTaskTimer(Bukkit.getPluginManager().getPlugin("MHAPlugin"), 0L, 1L);

        TextUtil.actionBar(activator, "Levitated: " + (target.getName() != null ? target.getName() : target.getType().name()));
    }

    /**
     * Apply the floating effect to an entity.
     */
    private void applyFloatEffect(final LivingEntity entity) {
        final long remaining = floatingTargets.getOrDefault(entity.getUniqueId(), 0L) - System.currentTimeMillis();
        if (remaining <= 0) {
            return;
        }

        // Cancel fall damage while floating
        if (fallDamageImmune) {
            entity.setFallDistance(0);
        }

        // Hover effect
        final Vector velocity = entity.getVelocity();
        if (velocity.getY() < 0) {
            entity.setVelocity(velocity.setY(velocity.getY() * 0.5 + 0.02));
        }

        // Subtle particle effect
        entity.getWorld().spawnParticle(
                Particle.WITCH,
                entity.getLocation().add(0, entity.getHeight() / 2, 0),
                2,
                0.2, 0.2, 0.2, 0
        );
    }

    /**
     * End the levitation effect on an entity.
     */
    private void endLevitation(final LivingEntity entity) {
        entity.getWorld().spawnParticle(
                Particle.POOF,
                entity.getLocation(),
                20,
                0.5, 0.5, 0.5, 0.1
        );
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.2f);
    }

    /**
     * Find the target entity the player is looking at.
     */
    private Entity getTargetEntity(final Player player) {
        final Location eyeLocation = player.getEyeLocation();
        final Vector direction = eyeLocation.getDirection();
        final double maxDistance = 15.0;

        Entity closestEntity = null;
        double closestDistance = maxDistance;

        for (final Entity entity : player.getNearbyEntities(maxDistance, maxDistance, maxDistance)) {
            if (entity.equals(player)) {
                continue;
            }

            // Check if entity is in the player's line of sight
            final Vector toEntity = entity.getLocation().toVector().subtract(eyeLocation.toVector());
            final double dot = direction.dot(toEntity.normalize());

            if (dot > 0.98) {
                final double distance = eyeLocation.distance(entity.getLocation());
                if (distance < closestDistance) {
                    closestEntity = entity;
                    closestDistance = distance;
                }
            }
        }

        return closestEntity;
    }

    /**
     * Check if an entity is currently floating.
     */
    public boolean isFloating(final UUID entityId) {
        final Long endTime = floatingTargets.get(entityId);
        return endTime != null && System.currentTimeMillis() < endTime;
    }

    /**
     * Manually stop levitation.
     */
    public void stopLevitation(final UUID entityId) {
        floatingTargets.remove(entityId);
        activators.remove(entityId);

        // Cancel the active task
        final BukkitRunnable task = activeTasks.remove(entityId);
        if (task != null) {
            task.cancel();
        }

        // Handle entity fall
        final Entity entity = Bukkit.getEntity(entityId);
        if (entity instanceof LivingEntity living) {
            endLevitation(living);
        }
    }

    @Override
    public void onRemove(final Player player) {
        // Remove all targets started by this player
        final List<UUID> toRemove = new ArrayList<>();
        for (final Map.Entry<UUID, UUID> entry : activators.entrySet()) {
            if (entry.getValue().equals(player.getUniqueId())) {
                toRemove.add(entry.getKey());
            }
        }
        for (final UUID targetId : toRemove) {
            stopLevitation(targetId);
        }

        super.onRemove(player);
    }

    /**
     * Shutdown the quirk - cancel all floating effects.
     */
    public void shutdown() {
        // Cancel all active tasks
        for (final BukkitRunnable task : activeTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        activeTasks.clear();

        // End levitation for all floating entities
        for (final UUID entityId : new HashSet<>(floatingTargets.keySet())) {
            final Entity entity = Bukkit.getEntity(entityId);
            if (entity instanceof LivingEntity living) {
                endLevitation(living);
            }
        }
        floatingTargets.clear();
        activators.clear();
        clearAllCooldowns();
    }

    @Override
    public void clearAllCooldowns() {
        super.clearAllCooldowns();
    }

    /**
     * Event handler to cancel fall damage for floating entities.
     */
    @EventHandler
    public void onEntityDamage(final EntityDamageEvent event) {
        if (!fallDamageImmune) {
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (isFloating(event.getEntity().getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
}
