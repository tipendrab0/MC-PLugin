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
import org.bukkit.entity.Snowball;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

/**
 * Pop Off Quirk - Mineta Minoru's ability.
 * Ejects sticky balls that can trap entities and create bounce pads.
 */
public final class PopOffQuirk extends Quirk {

    private final double damage;
    private final double slowDuration;
    private final int maxBalls;
    private final double bounceMultiplier;

    public PopOffQuirk(final ConfigManager config, final StaminaManager staminaManager) {
        super(QuirkType.POP_OFF, config, staminaManager);

        this.damage = getConfigDouble("damage", 3.0);
        this.slowDuration = getConfigDouble("slow-duration", 5.0);
        this.maxBalls = getConfigInt("max-balls", 5);
        this.bounceMultiplier = getConfigDouble("bounce-multiplier", 1.8);
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

        // Launch sticky ball projectile
        final Snowball ball = player.launchProjectile(Snowball.class);
        ball.setVelocity(player.getEyeLocation().getDirection().multiply(1.5));
        ball.setMetadata("popoff_ball", new FixedMetadataValue(
                org.bukkit.Bukkit.getPluginManager().getPlugin("MHAPlugin"), player.getUniqueId().toString()));
        ball.setShooter(player);

        // Visual effect
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getEyeLocation(), 10, 0.2, 0.2, 0.2, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1.0f, 1.5f);

        TextUtil.actionBar(player, "§d§lPOP OFF! §aSticky ball launched!");
        startCooldown(player);
        return true;
    }

    /**
     * Handle sticky ball hit on entity.
     */
    public void applyStickyEffect(final LivingEntity target, final Player source) {
        target.damage(damage, source);

        // Apply slowness
        target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS,
                (int) (slowDuration * 20), 4, false, true));

        // Visual
        target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_HONEY_BLOCK_PLACE, 1.5f, 1.0f);
    }

    /**
     * Apply bounce effect when stepped on.
     */
    public void applyBounceEffect(final Player player, final Location location) {
        player.setVelocity(new Vector(0, bounceMultiplier, 0));
        player.getWorld().spawnParticle(Particle.ITEM_SLIME, location, 20, 0.3, 0.3, 0.3, 0);
        player.playSound(location, Sound.BLOCK_SLIME_BLOCK_FALL, 1.0f, 1.2f);
    }
}
