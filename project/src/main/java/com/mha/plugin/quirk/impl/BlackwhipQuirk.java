package com.mha.plugin.quirk.impl;

import com.mha.plugin.quirk.Quirk;
import com.mha.plugin.util.TextUtil;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.util.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Blackwhip Quirk - Daigoro Banjo / Izuku Midoriya's ability.
 * Manifest shadowy tendrils for mobility and combat.
 * One of the quirks within One For All.
 */
public final class BlackwhipQuirk extends Quirk {

    private final double damage;
    private final double range;
    private final double grappleRange;
    private final int maxTargets;
    private final double holdDuration;

    public BlackwhipQuirk(final ConfigManager config) {
        super(QuirkType.BLACKWHIP, config);

        this.damage = getConfigDouble("damage", 18.0);
        this.range = getConfigDouble("range", 25.0);
        this.grappleRange = getConfigDouble("grapple-range", 20.0);
        this.maxTargets = getConfigInt("max-targets", 8);
        this.holdDuration = getConfigDouble("hold-duration", 3.0);
    }

    @Override
    public boolean activate(final Player player) {
        if (!canUse(player)) {
            return false;
        }

        // Sneak for grapple, normal for combat tendrils
        if (player.isSneaking()) {
            activateGrapple(player);
        } else {
            activateCombatTendrils(player);
        }

        startCooldown(player);
        return true;
    }

    /**
     * Combat whirl - attack multiple nearby enemies.
     */
    private void activateCombatTendrils(final Player player) {
        final Location center = player.getLocation();

        // Spawn black tendrils in all directions
        new BukkitRunnable() {
            int ticks = 0;
            final double[] directions = {0, 45, 90, 135, 180, 225, 270, 315};

            @Override
            public void run() {
                if (ticks++ > 30 || !player.isOnline()) {
                    cancel();
                    return;
                }

                for (final double angle : directions) {
                    final double rad = Math.toRadians(angle + player.getLocation().getYaw());
                    final double distance = ticks * 0.5;

                    final Vector direction = new Vector(Math.cos(rad), 0, Math.sin(rad));
                    final Location tendrilLoc = center.clone().add(direction.multiply(distance)).add(0, 1.0, 0);

                    // Blackwhip particles
                    player.getWorld().spawnParticle(Particle.SMOKE, tendrilLoc, 10, 0.3, 0.3, 0.3, 0.01);
                    player.getWorld().spawnParticle(Particle.SCULK_CHARGE, tendrilLoc, 5, 0.2, 0.5, 0.2, 0);

                    // Damage entities in path
                    for (final Entity entity : tendrilLoc.getNearbyEntities(1.5, 1.5, 1.5)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(damage * 0.3, player);
                            living.setVelocity(new Vector(0, 0.5, 0));
                        }
                    }
                }
            }
        }.runTaskTimer(org.bukkit.Bukkit.getPluginManager().getPlugin("MHAPlugin"), 0L, 2L);

        player.getWorld().playSound(center, Sound.ENTITY_WARDEN_TENDRIL_CLICKS, 2.0f, 0.8f);
        player.getWorld().playSound(center, Sound.ENTITY_PHANTOM_FLAP, 1.5f, 1.2f);

        TextUtil.actionBar(player, "§8§lBLACKWHIP! §0Shadow tendrils attack!");
        player.sendMessage("§8>>> BLACKWHIP ACTIVATED <<<");
    }

    /**
     * Grapple to target location or pull enemy.
     */
    private void activateGrapple(final Player player) {
        final Location eyeLoc = player.getEyeLocation();
        final Vector direction = eyeLoc.getDirection();

        Entity targetEntity = null;
        Location targetLoc = null;

        // Find grapple target
        for (double d = 2; d <= grappleRange; d += 0.5) {
            final Location checkLoc = eyeLoc.clone().add(direction.clone().multiply(d));

            // Check for entities first
            for (final Entity entity : checkLoc.getNearbyEntities(1.5, 1.5, 1.5)) {
                if (entity instanceof LivingEntity && !entity.equals(player)) {
                    targetEntity = entity;
                    break;
                }
            }

            if (targetEntity != null) break;

            // Check for blocks
            if (checkLoc.getBlock().getType().isSolid()) {
                targetLoc = checkLoc;
                break;
            }
        }

        if (targetEntity != null) {
            pullEntity(player, (LivingEntity) targetEntity);
        } else if (targetLoc != null) {
            grappleToLoc(player, targetLoc);
        } else {
            TextUtil.actionBar(player, "§cNo target found!");
            resetCooldown(player);
        }
    }

    /**
     * Pull entity toward player with tendril.
     */
    private void pullEntity(final Player player, final LivingEntity target) {
        // Draw tendril line
        drawTendril(player.getEyeLocation(), target.getLocation().add(0, 1, 0));

        // Pull toward player
        final Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(2.0);
        target.setVelocity(pull);
        target.damage(damage, player);

        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.5f, 1.0f);
        TextUtil.actionBar(player, "§8§lBLACKWHIP GRAPPLE! §7Pulled target!");
    }

    /**
     * Grapple player to block location.
     */
    private void grappleToLoc(final Player player, final Location loc) {
        drawTendril(player.getEyeLocation(), loc);

        // Launch player
        final Vector launch = loc.toVector().subtract(player.getLocation().toVector()).normalize().multiply(2.0).add(new Vector(0, 0.3, 0));
        player.setVelocity(launch);

        player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.5f, 1.0f);
        TextUtil.actionBar(player, "§8§lBLACKWHIP GRAPPLE! §7Swinging!");
    }

    /**
     * Draw tendril particle line.
     */
    private void drawTendril(final Location start, final Location end) {
        final Vector direction = end.toVector().subtract(start.toVector());
        final double distance = direction.length();
        direction.normalize();

        for (double d = 0; d < distance; d += 0.4) {
            final Location point = start.clone().add(direction.clone().multiply(d));
            start.getWorld().spawnParticle(Particle.SMOKE, point, 5, 0.1, 0.1, 0.1, 0.01);
            start.getWorld().spawnParticle(Particle.SCULK_CHARGE, point, 2, 0.05, 0.1, 0.05, 0);
        }
    }
}
