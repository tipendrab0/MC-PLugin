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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Cremation Quirk - Dabi's ability.
 * Generate intensely hot blue flames that burn everything.
 */
public final class CremationQuirk extends Quirk {

    private final double damage;
    private final double range;
    private final int fireTicks;
    private final double pillarDamage;
    private final double pillarRadius;
    private final double selfDamagePercent;

    public CremationQuirk(final ConfigManager config, final StaminaManager staminaManager) {
        super(QuirkType.CREMATION, config, staminaManager);

        this.damage = getConfigDouble("damage", 10.0);
        this.range = getConfigDouble("range", 15.0);
        this.fireTicks = getConfigInt("fire-ticks", 100);
        this.pillarDamage = getConfigDouble("pillar-damage", 15.0);
        this.pillarRadius = getConfigDouble("pillar-radius", 3.0);
        this.selfDamagePercent = getConfigDouble("self-damage-percent", 3.0);
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

        // Self damage (quirk drawback - burns user)
        player.damage(player.getHealth() * (selfDamagePercent / 100.0));

        // Sneak for pillar, normal for projectile
        if (player.isSneaking()) {
            activateFlamePillar(player);
        } else {
            activateFlameProjectile(player);
        }

        startCooldown(player);
        return true;
    }

    /**
     * Blue flame projectile.
     */
    private void activateFlameProjectile(final Player player) {
        final Location eyeLoc = player.getEyeLocation();
        final Vector direction = eyeLoc.getDirection().normalize();

        new BukkitRunnable() {
            int ticks = 0;
            final Location projectileLoc = eyeLoc.clone();
            boolean hit = false;

            @Override
            public void run() {
                if (ticks++ > 60 || hit || !player.isOnline()) {
                    cancel();
                    return;
                }

                projectileLoc.add(direction);

                // Blue flame particles
                player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, projectileLoc, 15, 0.3, 0.3, 0.3, 0.05);
                player.getWorld().spawnParticle(Particle.FLAME, projectileLoc, 10, 0.2, 0.2, 0.2, 0);
                player.getWorld().spawnParticle(Particle.LARGE_SMOKE, projectileLoc, 5, 0.2, 0.2, 0.2, 0);

                // Hit entities
                for (final Entity entity : projectileLoc.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player)) {
                        living.damage(damage, player);
                        living.setFireTicks(fireTicks);
                        living.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, living.getLocation(), 30, 0.5, 0.5, 0.5, 0.2);
                        hit = true;
                        break;
                    }
                }

                // Hit blocks
                if (projectileLoc.getBlock().getType().isSolid()) {
                    projectileLoc.getBlock().getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, projectileLoc, 50, 1, 1, 1, 0.1);
                    player.playSound(projectileLoc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);
                    hit = true;
                }
            }
        }.runTaskTimer(org.bukkit.Bukkit.getPluginManager().getPlugin("MHAPlugin"), 0L, 1L);

        player.getWorld().playSound(eyeLoc, Sound.ENTITY_BLAZE_SHOOT, 2.0f, 0.7f);
        TextUtil.actionBar(player, "§3§lCREMATION! §bBlue flames!");
    }

    /**
     * Flame pillar - massive vertical fire blast.
     */
    private void activateFlamePillar(final Player player) {
        final Location loc = player.getLocation();

        // Rising flames
        new BukkitRunnable() {
            double y = 0;
            final double startY = loc.getY();

            @Override
            public void run() {
                y += 0.5;
                final double currentY = startY + y;

                if (y > 10 || !player.isOnline()) {
                    cancel();
                    return;
                }

                // Flame pillar column
                for (int i = 0; i < 10; i++) {
                    final double angle = i * 36;
                    final double x = Math.cos(Math.toRadians(angle)) * pillarRadius;
                    final double z = Math.sin(Math.toRadians(angle)) * pillarRadius;

                    final Location pillarLoc = loc.clone().add(x, y, z);
                    player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, pillarLoc, 10, 0.3, 0.1, 0.3, 0.05);
                    player.getWorld().spawnParticle(Particle.FLAME, pillarLoc, 5, 0.2, 0, 0.2, 0);
                }

                // Damage at ground level
                if (y < 2) {
                    for (final Entity entity : loc.getWorld().getNearbyEntities(loc, pillarRadius, 3, pillarRadius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(pillarDamage / 2, player);
                            living.setFireTicks(fireTicks);
                        }
                    }
                }
            }
        }.runTaskTimer(org.bukkit.Bukkit.getPluginManager().getPlugin("MHAPlugin"), 0L, 2L);

        player.getWorld().playSound(loc, Sound.ENTITY_GHAST_SHOOT, 2.0f, 0.6f);
        player.getWorld().playSound(loc, Sound.ITEM_FIRECHARGE_USE, 2.0f, 0.5f);

        TextUtil.actionBar(player, "§3§lCREMATION PILLAR! §bRising inferno!");
    }
}
