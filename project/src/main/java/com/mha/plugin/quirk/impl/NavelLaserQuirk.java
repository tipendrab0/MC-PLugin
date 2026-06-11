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
 * Navel Laser Quirk - Aoyama Yuuga's ability.
 * Fires a powerful laser beam from the navel.
 */
public final class NavelLaserQuirk extends Quirk {

    private final double damage;
    private final double range;
    private final double beamWidth;
    private final int chargeTicks;

    public NavelLaserQuirk(final ConfigManager config) {
        super(QuirkType.NAVEL_LASER, config);

        this.damage = getConfigDouble("damage", 7.0);
        this.range = getConfigDouble("range", 25.0);
        this.beamWidth = getConfigDouble("beam-width", 1.0);
        this.chargeTicks = getConfigInt("charge-ticks", 20);
    }

    @Override
    public boolean activate(final Player player) {
        if (!canUse(player)) {
            return false;
        }

        // Charge effect
        TextUtil.actionBar(player, "§e§lCharging laser...");
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 0.7, 0), 5, 0.1, 0.1, 0.1, 0);

        // Delayed fire
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                fireLaser(player);
            }
        }.runTaskLater(org.bukkit.Bukkit.getPluginManager().getPlugin("MHAPlugin"), chargeTicks);

        startCooldown(player);
        return true;
    }

    /**
     * Fire the laser beam.
     */
    private void fireLaser(final Player player) {
        final Location start = player.getLocation().add(0, 0.7, 0);
        final Vector direction = player.getLocation().getDirection().normalize();

        player.playSound(start, Sound.ENTITY_GUARDIAN_ATTACK, 2.0f, 1.2f);

        // Raycast beam
        for (double d = 0; d < range; d += 0.5) {
            final Location point = start.clone().add(direction.clone().multiply(d));

            // Beam particles
            player.getWorld().spawnParticle(Particle.END_ROD, point, 5, 0.1, 0.1, 0.1, 0);
            player.getWorld().spawnParticle(Particle.FLASH, point, 1, 0, 0, 0, 0);

            // Damage entities in beam path
            for (final Entity entity : point.getNearbyEntities(beamWidth, beamWidth, beamWidth)) {
                if (entity instanceof LivingEntity living && !entity.equals(player)) {
                    final double distDamage = damage * (1.0 - d / range * 0.5);
                    living.damage(Math.max(1.0, distDamage), player);

                    // Burn effect
                    living.setFireTicks(40);
                }
            }
        }

        TextUtil.actionBar(player, "§b§lNAVEL LASER! §fFired!");
    }
}
