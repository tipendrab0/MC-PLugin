package com.mha.plugin.quirk.impl;

import com.mha.plugin.quirk.Quirk;
import com.mha.plugin.util.TextUtil;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.util.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Frog Quirk - Tsuyu Asui's ability.
 * Grants frog-like abilities including tongue grab, wall climbing, and swimming.
 */
public final class FrogQuirk extends Quirk {

    private final double tongueRange;
    private final double tongueDamage;
    private final int invisibilityDuration;
    private final double jumpMultiplier;
    private final int swimSpeedDuration;

    public FrogQuirk(final ConfigManager config) {
        super(QuirkType.FROG, config);

        this.tongueRange = config.getQuirkNestedDouble("frog", "tongue", "range", 20.0);
        this.tongueDamage = config.getQuirkNestedDouble("frog", "tongue", "damage", 4.0);
        this.invisibilityDuration = config.getQuirkNestedInt("frog", "camouflage", "duration-ticks", 100);
        this.jumpMultiplier = config.getQuirkNestedDouble("frog", "mobility", "jump-multiplier", 2.0);
        this.swimSpeedDuration = config.getQuirkNestedInt("frog", "mobility", "swim-duration-ticks", 400);
    }

    @Override
    public boolean activate(final Player player) {
        if (!canUse(player)) {
            return false;
        }

        // Check if in water - swimming mode
        if (player.isInWater()) {
            activateSwimBoost(player);
        } else if (player.isSneaking()) {
            activateCamouflage(player);
        } else {
            activateTongueGrab(player);
        }

        startCooldown(player);
        return true;
    }

    /**
     * Tongue grab - pulls enemies toward the player or pulls player to walls.
     */
    private void activateTongueGrab(final Player player) {
        final Location eyeLoc = player.getEyeLocation();
        final Vector direction = eyeLoc.getDirection();

        Entity targetEntity = null;
        Location targetLoc = null;

        // Raycast for entities and blocks
        for (double d = 2; d <= tongueRange; d += 0.5) {
            final Location checkLoc = eyeLoc.clone().add(direction.clone().multiply(d));

            // Check for entities first
            for (final Entity entity : checkLoc.getNearbyEntitiesByType(LivingEntity.class, 1.5, 1.5, 1.5)) {
                if (!entity.equals(player)) {
                    targetEntity = entity;
                    break;
                }
            }

            if (targetEntity != null) {
                break;
            }

            // Check for blocks
            if (checkLoc.getBlock().getType().isSolid()) {
                targetLoc = checkLoc;
                break;
            }
        }

        // Execute tongue action
        if (targetEntity != null && targetEntity instanceof LivingEntity living) {
            // Pull entity toward player
            pullEntity(player, living);
        } else if (targetLoc != null) {
            // Grapple to wall
            grappleToWall(player, targetLoc);
        } else {
            TextUtil.actionBar(player, "§cNo target found!");
            resetCooldown(player);
            return;
        }
    }

    /**
     * Pull an entity toward the player with the tongue.
     */
    private void pullEntity(final Player player, final LivingEntity target) {
        final Vector pullDirection = player.getLocation().toVector()
                .subtract(target.getLocation().toVector())
                .normalize()
                .multiply(1.5);

        // Launch target toward player
        target.setVelocity(pullDirection.add(new Vector(0, 0.3, 0)));
        target.damage(tongueDamage, player);

        // Visual - tongue particle effect
        drawTongueLine(player, target.getLocation());

        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_FROG_TONGUE, 1.5f, 1.0f);
        TextUtil.actionBar(player, "§a§lTONGUE GRAB! §ePulled " + target.getName());
    }

    /**
     * Grapple player to wall.
     */
    private void grappleToWall(final Player player, final Location targetLoc) {
        final Vector launchDir = targetLoc.toVector()
                .subtract(player.getLocation().toVector())
                .normalize()
                .multiply(1.8);

        player.setVelocity(launchDir.add(new Vector(0, 0.2, 0)));

        // Visual - tongue line
        drawTongueLine(player, targetLoc);

        player.getWorld().playSound(targetLoc, Sound.ENTITY_FROG_TONGUE, 1.5f, 0.8f);
        TextUtil.actionBar(player, "§a§lGRAPPLE! §bFrog mobility!");
    }

    /**
     * Draw a particle line representing the tongue.
     */
    private void drawTongueLine(final Player player, final Location target) {
        final Location start = player.getEyeLocation();
        final Vector direction = target.toVector().subtract(start.toVector());
        final double distance = direction.length();
        direction.normalize();

        for (double d = 0; d < distance; d += 0.5) {
            final Location point = start.clone().add(direction.clone().multiply(d));
            player.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Camouflage - invisibility and slow fall.
     */
    private void activateCamouflage(final Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, invisibilityDuration, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, invisibilityDuration, 0, false, false));

        // Visual - fade effect
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 30, 0.5, 1.0, 0.5, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FROG_AMBIENT, 1.0f, 0.7f);

        TextUtil.actionBar(player, "§a§lCAMOUFLAGE! §7Vanished into shadows...");
    }

    /**
     * Swim boost - enhanced underwater mobility.
     */
    private void activateSwimBoost(final Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, swimSpeedDuration, 2, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, swimSpeedDuration, 0, false, true));

        // Launch forward in water
        final Vector swimDir = player.getLocation().getDirection().multiply(2.0);
        player.setVelocity(swimDir);

        // Visual - bubble trail
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > 60 || !player.isInWater() || !player.isOnline()) {
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.BUBBLE, player.getLocation(), 5, 0.3, 0.3, 0.3, 0.1);
            }
        }.runTaskTimer(org.bukkit.Bukkit.getPluginManager().getPlugin("MHAPlugin"), 0L, 2L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 1.5f, 1.2f);
        TextUtil.actionBar(player, "§b§lFISH MODE! §3Swimming at full speed!");
    }
}
