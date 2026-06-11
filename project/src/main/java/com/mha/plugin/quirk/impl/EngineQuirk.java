package com.mha.plugin.quirk.impl;

import com.mha.plugin.quirk.Quirk;
import com.mha.plugin.util.TextUtil;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.util.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Engine Quirk - Iida Tenya's ability.
 * Grants super speed and powerful kicks with reciprocating engines.
 */
public final class EngineQuirk extends Quirk {

    private final int speedDuration;
    private final int speedAmplifier;
    private final double kickDamage;
    private final double dashDistance;
    private final int staminaPerSecond;

    public EngineQuirk(final ConfigManager config) {
        super(QuirkType.ENGINE, config);

        this.speedDuration = config.getQuirkNestedInt("engine", "speed", "duration-ticks", 200);
        this.speedAmplifier = config.getQuirkNestedInt("engine", "speed", "amplifier", 3);
        this.kickDamage = config.getQuirkNestedDouble("engine", "kick", "damage", 8.0);
        this.dashDistance = config.getQuirkNestedDouble("engine", "dash", "distance", 15.0);
        this.staminaPerSecond = config.getQuirkNestedInt("engine", "stamina", "per-second", 5);
    }

    @Override
    public boolean activate(final Player player) {
        if (!canUse(player)) {
            return false;
        }

        // Check if shifting - dash mode, normal - speed mode
        if (player.isSneaking()) {
            activateKickDash(player);
        } else {
            activateSpeedBoost(player);
        }

        startCooldown(player);
        return true;
    }

    /**
     * Activate speed boost mode - dash forward with extreme speed.
     */
    private void activateSpeedBoost(final Player player) {
        // Apply speed and jump boost
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, speedDuration, speedAmplifier, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, speedDuration, speedAmplifier - 1, false, true));

        // Visual - engine exhaust particles
        final Location loc = player.getLocation();
        loc.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0, 0.2, 0).subtract(player.getLocation().getDirection()), 30, 0.3, 0.1, 0.3, 0.05);
        loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 0.2, 0).subtract(player.getLocation().getDirection()), 20, 0.2, 0.1, 0.2, 0.02);
        loc.getWorld().playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.5f);

        // Show engine activation
        TextUtil.actionBar(player, "§e§lENGINE ACTIVATED! §6Sprint ready!");
        player.sendMessage("§e>>> Reciprocating Burst Engines - ACTIVATED <<<");
    }

    /**
     * Activate kick dash - powerful flying kick attack.
     */
    private void activateKickDash(final Player player) {
        final Vector direction = player.getLocation().getDirection().normalize();

        // Launch forward
        player.setVelocity(direction.multiply(dashDistance).setY(0.3));

        // Visual - exhaust behind
        final Location behind = player.getLocation().subtract(direction.clone().multiply(2));
        behind.getWorld().spawnParticle(Particle.EXPLOSION, behind, 5, 0.5, 0.5, 0.5, 0);
        behind.getWorld().spawnParticle(Particle.LARGE_SMOKE, behind, 50, 1.0, 0.5, 1.0, 0.1);
        behind.getWorld().playSound(behind, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.2f);

        // Create damage trail
        new BukkitRunnable() {
            int ticks = 0;
            final Location startLoc = player.getLocation();

            @Override
            public void run() {
                if (ticks++ > 30 || !player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }

                final Location currentLoc = player.getLocation();

                // Particle trail
                currentLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, currentLoc, 10, 0.5, 0.3, 0.5, 0.05);

                // Damage entities in front
                player.getNearbyEntities(2, 2, 2).forEach(entity -> {
                    if (entity instanceof org.bukkit.entity.LivingEntity living && !entity.equals(player)) {
                        living.damage(kickDamage, player);
                        living.getWorld().spawnParticle(Particle.CRIT, living.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5);
                    }
                });
            }
        }.runTaskTimer(org.bukkit.Bukkit.getPluginManager().getPlugin("MHAPlugin"), 0L, 1L);

        TextUtil.actionBar(player, "§d§lRECIPRO BURST! §cKicking!");
    }
}
