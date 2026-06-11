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

/**
 * Electrification Quirk - Kaminari Denki's ability.
 * Generate and discharge electricity to shock enemies.
 * Ultimate: Million Volt Attack (stuns everyone nearby including user)
 */
public final class ElectrificationQuirk extends Quirk {

    private final double shockDamage;
    private final double shockRadius;
    private final int stunDuration;
    private final double ultimateDamage;
    private final double ultimateRadius;
    private final int selfStunDuration;

    public ElectrificationQuirk(final ConfigManager config) {
        super(QuirkType.ELECTRIFICATION, config);

        this.shockDamage = getConfigDouble("shock-damage", 5.0);
        this.shockRadius = getConfigDouble("shock-radius", 5.0);
        this.stunDuration = getConfigInt("stun-duration-ticks", 60);
        this.ultimateDamage = getConfigDouble("ultimate-damage", 12.0);
        this.ultimateRadius = getConfigDouble("ultimate-radius", 15.0);
        this.selfStunDuration = getConfigInt("self-stun-duration-ticks", 100);
    }

    @Override
    public boolean activate(final Player player) {
        if (!canUse(player)) {
            return false;
        }

        // Sneak for ultimate, normal for standard shock
        if (player.isSneaking()) {
            activateMillionVolt(player);
        } else {
            activateShockwave(player);
        }

        startCooldown(player);
        return true;
    }

    /**
     * Standard electrical shockwave.
     */
    private void activateShockwave(final Player player) {
        final Location loc = player.getLocation();

        // Spawn electrical particles
        loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 100, shockRadius, 2, shockRadius, 0.5);
        loc.getWorld().spawnParticle(Particle.FLASH, loc.add(0, 1, 0), 5, 0, 0, 0, 0);

        player.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.5f);

        // Shock nearby entities
        for (final Entity entity : loc.getWorld().getNearbyEntities(loc, shockRadius, shockRadius, shockRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(shockDamage, player);
                living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOWNESS, stunDuration, 5, false, true));
                living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.WEAKNESS, stunDuration, 2, false, true));

                // Spark particles on target
                living.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, living.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.3);
            }
        }

        TextUtil.actionBar(player, "§e§lELECTRIFICATION! §fShocking pulse!");
    }

    /**
     * Million Volt Attack - ultimate but user becomes stunned.
     */
    private void activateMillionVolt(final Player player) {
        final Location loc = player.getLocation();

        // Dramatic charge-up
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > 40 || !player.isOnline()) {
                    // Final burst
                    executeMillionVolt(player);
                    cancel();
                    return;
                }

                // Charge particles
                player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation(), 20 + ticks, 2, 2, 2, 0.3);
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.5, 0), 10, 0.5, 0.5, 0.5, 0.05);

                if (ticks % 5 == 0) {
                    player.playSound(player.getLocation(), Sound.ENTITY_CREEPER_HURT, 0.5f, 1.0f + ticks * 0.02f);
                }

                TextUtil.actionBar(player, "§e§lCHARGING: " + (ticks * 100 / 40) + "%");
            }
        }.runTaskTimer(org.bukkit.Bukkit.getPluginManager().getPlugin("MHAPlugin"), 0L, 1L);
    }

    /**
     * Execute the million volt attack.
     */
    private void executeMillionVolt(final Player player) {
        final Location loc = player.getLocation();

        // Massive electrical burst
        loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 500, ultimateRadius, 5, ultimateRadius, 1.0);
        loc.getWorld().spawnParticle(Particle.FLASH, loc.add(0, 1, 0), 20, 0, 0, 0, 0);
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 3, 2, 2, 2, 0);

        loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0f, 0.5f);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);

        // Damage ALL entities in huge radius (including allies)
        for (final Entity entity : loc.getWorld().getNearbyEntities(loc, ultimateRadius, ultimateRadius, ultimateRadius)) {
            if (entity instanceof LivingEntity living) {
                if (entity.equals(player)) continue;

                living.damage(ultimateDamage, player);
                living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOWNESS, 200, 10, false, true));
                living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.BLINDNESS, 100, 0, false, true));
                living.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, living.getLocation(), 50, 0.5, 1, 0.5, 0.5);
            }
        }

        // User becomes brain-dead (stunned) for a while
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS, selfStunDuration, 10, false, false));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.WEAKNESS, selfStunDuration, 10, false, false));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.MINING_FATIGUE, selfStunDuration, 5, false, false));

        player.sendTitle("§c§lMILLION VOLT!", "§7...but you're short-circuited!", 10, 60, 10);
        TextUtil.actionBar(player, "§c§lBRAIN DEAD! §7Stunned for " + (selfStunDuration / 20) + "s");

        // Warning message
        player.sendMessage("§c§l>>> MILLION VOLT ATTACK! <<<");
        player.sendMessage("§7Warning: You've short-circuited your brain!");
    }
}
