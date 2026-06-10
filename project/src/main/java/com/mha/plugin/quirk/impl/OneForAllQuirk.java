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
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * One For All Quirk - Midoriya Izuku's ability.
 * Stockpiled power with devastating smash attacks and Delaware Smash.
 * Ultimate: United States of Smash.
 */
public final class OneForAllQuirk extends Quirk {

    private final double smashDamage;
    private final double smashRadius;
    private final double flickDamage;
    private final double dashMultiplier;
    private final double selfDamagePercent;

    public OneForAllQuirk(final ConfigManager config, final StaminaManager staminaManager) {
        super(QuirkType.ONE_FOR_ALL, config, staminaManager);

        this.smashDamage = config.getQuirkNestedDouble("one-for-all", "smash", "damage", 15.0);
        this.smashRadius = config.getQuirkNestedDouble("one-for-all", "smash", "radius", 5.0);
        this.flickDamage = config.getQuirkNestedDouble("one-for-all", "finger-flick", "damage", 10.0);
        this.dashMultiplier = config.getQuirkNestedDouble("one-for-all", "mobility", "dash-multiplier", 3.0);
        this.selfDamagePercent = config.getQuirkNestedDouble("one-for-all", "drawback", "self-damage-percent", 5.0);
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

        // Sneak for air force, normal for Detroit Smash
        if (player.isSneaking()) {
            activateFingerFlick(player);
        } else {
            activateDetroitSmash(player);
        }

        startCooldown(player);
        return true;
    }

    /**
     * Detroit Smash - powerful punch with area damage.
     */
    private void activateDetroitSmash(final Player player) {
        final Location target = getTargetLocation(player);
        if (target == null || target.getWorld() == null) {
            return;
        }

        // Apply self-damage (quirk drawback)
        final double selfDmg = player.getHealth() * (selfDamagePercent / 100.0);
        player.damage(selfDmg);

        // Create shockwave
        target.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, target, 3, 0, 0, 0, 0);
        target.getWorld().spawnParticle(Particle.SONIC_BOOM, target, 10, 2.0, 1.0, 2.0, 0);
        target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target, 100, 3.0, 2.0, 3.0, 0.5);

        // Sound effects
        target.getWorld().playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f);
        target.getWorld().playSound(target, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.0f);

        // Damage and knockback nearby entities
        for (final Entity entity : target.getWorld().getNearbyEntities(target, smashRadius, smashRadius, smashRadius)) {
            if (entity.equals(player)) continue;

            if (entity instanceof LivingEntity living) {
                final double distance = entity.getLocation().distance(target);
                final double scaledDamage = smashDamage * (1.0 - (distance / smashRadius));
                living.damage(Math.max(1.0, scaledDamage), player);

                // Knockback away from impact
                final Vector knockback = entity.getLocation().toVector()
                        .subtract(target.toVector())
                        .normalize()
                        .multiply(2.5)
                        .add(new Vector(0, 0.8, 0));
                entity.setVelocity(knockback);
            }
        }

        // Launch player forward (impact momentum)
        final Vector launch = player.getLocation().getDirection().normalize().multiply(1.5).add(new Vector(0, 0.5, 0));
        player.setVelocity(launch);

        TextUtil.actionBar(player, "§d§lDETROIT SMAAAAASH! §fPower: 100%!");
        player.sendMessage("§d>>> DETROIT SMASH! <<<");
    }

    /**
     * Delaware Smash - air projectile from finger flick.
     */
    private void activateFingerFlick(final Player player) {
        final Location eyeLoc = player.getEyeLocation();
        final Vector direction = eyeLoc.getDirection().normalize();

        // Self damage from finger pressure
        player.damage(player.getHealth() * (selfDamagePercent / 200.0)); // Half the self-damage

        // Create air projectile
        new BukkitRunnable() {
            int distance = 0;
            final Location projectileLoc = eyeLoc.clone();
            boolean hit = false;

            @Override
            public void run() {
                if (distance++ > 50 || hit || !player.isOnline()) {
                    cancel();
                    return;
                }

                projectileLoc.add(direction.clone().multiply(1.5));

                // Particle trail
                player.getWorld().spawnParticle(Particle.SQUID_INK, projectileLoc, 5, 0.2, 0.2, 0.2, 0);
                player.getWorld().spawnParticle(Particle.GUST, projectileLoc, 3, 0.1, 0.1, 0.1, 0);

                // Hit detection
                for (final Entity entity : projectileLoc.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player)) {
                        living.damage(flickDamage, player);
                        living.setVelocity(direction.clone().multiply(3.0));
                        entity.getWorld().spawnParticle(Particle.EXPLOSION, projectileLoc, 10, 0.5, 0.5, 0.5, 0);
                        entity.getWorld().playSound(projectileLoc, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.5f, 1.2f);
                        hit = true;
                        break;
                    }
                }

                // Block collision
                if (projectileLoc.getBlock().getType().isSolid()) {
                    hit = true;
                    projectileLoc.getWorld().spawnParticle(Particle.EXPLOSION, projectileLoc, 5, 0.3, 0.3, 0.3, 0);
                }
            }
        }.runTaskTimer(org.bukkit.plugin.java.JavaPlugin.getPlugin(com.mha.plugin.MHAPlugin.class), 0L, 1L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 2.0f, 1.5f);
        TextUtil.actionBar(player, "§b§lDELAWARE SMASH! §7Air Bullet!");
    }

    /**
     * Get the target location for smash attacks.
     */
    private Location getTargetLocation(final Player player) {
        final Location eyeLoc = player.getEyeLocation();
        final Vector direction = eyeLoc.getDirection();

        for (int i = 0; i < 15; i++) {
            final Location check = eyeLoc.clone().add(direction.clone().multiply(i));
            if (check.getBlock().getType().isSolid()) {
                return check.subtract(direction.clone().multiply(2));
            }
        }
        return eyeLoc.clone().add(direction.multiply(5));
    }
}
