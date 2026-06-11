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
import org.bukkit.util.Vector;

/**
 * Wave Motion Quirk - Nejire Hado's ability.
 * Convert stamina into powerful energy waves for offense and flight.
 */
public final class WaveMotionQuirk extends Quirk {

    private final double waveDamage;
    private final double waveRange;
    private final double waveWidth;
    private final double flightStaminaDrain;
    private final int staminaDrainInterval;

    public WaveMotionQuirk(final ConfigManager config) {
        super(QuirkType.WAVE_MOTION, config);

        this.waveDamage = getConfigDouble("wave-damage", 8.0);
        this.waveRange = getConfigDouble("wave-range", 20.0);
        this.waveWidth = getConfigDouble("wave-width", 2.0);
        this.flightStaminaDrain = getConfigDouble("flight-stamina-drain", 2.0);
        this.staminaDrainInterval = getConfigInt("stamina-drain-interval", 10);
    }

    @Override
    public boolean activate(final Player player) {
        if (!canUse(player)) {
            return false;
        }

        // Sneak for concentrated blast, normal for wave
        if (player.isSneaking()) {
            activateConcentratedBlast(player);
        } else {
            activateWave(player);
        }

        startCooldown(player);
        return true;
    }

    /**
     * Energy wave attack - wide cone projectile.
     */
    private void activateWave(final Player player) {
        final Location eyeLoc = player.getEyeLocation();
        final Vector direction = eyeLoc.getDirection().normalize();

        // Fire expanding wave
        for (double d = 0; d < waveRange; d += 0.8) {
            final Location wavePoint = eyeLoc.clone().add(direction.clone().multiply(d));
            final double waveWidthAtDistance = waveWidth + d * 0.1;

            // Wave particles
            player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, wavePoint, 10, waveWidthAtDistance, waveWidthAtDistance, waveWidthAtDistance, 0.05);
            player.getWorld().spawnParticle(Particle.END_ROD, wavePoint, 5, waveWidthAtDistance * 0.5, waveWidthAtDistance * 0.5, waveWidthAtDistance * 0.5, 0);

            // Damage entities in wave path
            for (final Entity entity : wavePoint.getNearbyEntities(waveWidthAtDistance, waveWidthAtDistance, waveWidthAtDistance)) {
                if (entity instanceof LivingEntity living && !entity.equals(player)) {
                    final double distDamage = waveDamage * (1.0 - d / waveRange * 0.3);
                    living.damage(Math.max(1.0, distDamage), player);
                    living.setVelocity(living.getVelocity().add(direction.clone().multiply(1.5)));
                }
            }
        }

        player.playSound(eyeLoc, Sound.ENTITY_PHANTOM_FLAP, 2.0f, 1.5f);
        TextUtil.actionBar(player, "§d§lWAVE MOTION! §fEnergized wave!");
    }

    /**
     * Concentrated blast - focused beam for more damage.
     */
    private void activateConcentratedBlast(final Player player) {
        final Location eyeLoc = player.getEyeLocation();
        final Vector direction = eyeLoc.getDirection().normalize();

        // Concentrated beam
        for (double d = 0; d < waveRange * 1.5; d += 0.3) {
            final Location beamPoint = eyeLoc.clone().add(direction.clone().multiply(d));

            // Beam particles (thinner but more intense)
            player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, beamPoint, 15, 0.2, 0.2, 0.2, 0.01);
            player.getWorld().spawnParticle(Particle.FLASH, beamPoint, 1, 0, 0, 0, 0);

            // Heavy damage
            for (final Entity entity : beamPoint.getNearbyEntities(1.0, 1.0, 1.0)) {
                if (entity instanceof LivingEntity living && !entity.equals(player)) {
                    living.damage(waveDamage * 1.5, player);
                    living.getWorld().spawnParticle(Particle.EXPLOSION, living.getLocation(), 3, 0.3, 0.3, 0.3, 0);
                }
            }
        }

        player.playSound(eyeLoc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 2.0f, 1.0f);
        TextUtil.actionBar(player, "§d§lCONCENTRATED BLAST! §fMaximum output!");
    }
}
