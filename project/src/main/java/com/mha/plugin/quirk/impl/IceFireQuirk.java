package com.mha.plugin.quirk.impl;

import com.mha.plugin.MHAPlugin;
import com.mha.plugin.protection.ProtectionManager;
import com.mha.plugin.util.TextUtil;
import com.mha.plugin.quirk.Quirk;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.util.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Collection;

/**
 * Ice/Fire Quirk - Todoroki Shoto's ability.
 * Right-click for ice, left-click for fire.
 */
public final class IceFireQuirk extends Quirk {

    private final int iceDuration;
    private final int iceSlowAmplifier;
    private final double iceRadius;
    private final int fireDuration;
    private final double fireRadius;
    private final int fireTicks;

    public IceFireQuirk(final ConfigManager config) {
        super(QuirkType.ICE_FIRE, config);

        // Ice configuration
        this.iceDuration = config.getQuirkNestedInt("ice-fire", "ice", "duration", 5000);
        this.iceSlowAmplifier = config.getQuirkNestedInt("ice-fire", "ice", "slow-amplifier", 3);
        this.iceRadius = config.getQuirkNestedDouble("ice-fire", "ice", "freeze-radius", 3.0);

        // Fire configuration
        this.fireDuration = config.getQuirkNestedInt("ice-fire", "fire", "duration", 3000);
        this.fireRadius = config.getQuirkNestedDouble("ice-fire", "fire", "fire-radius", 3.0);
        this.fireTicks = config.getQuirkNestedInt("ice-fire", "fire", "fire-ticks", 60);
    }

    /**
     * Activate ice or fire based on click type.
     */
    @Override
    public boolean activate(final Player player) {
        return activate(player, Action.ICE);
    }

    /**
     * Activate with specific action type.
     * @param action ICE or FIRE
     */
    public boolean activate(final Player player, final Action action) {
        if (!canUse(player)) {
            return false;
        }

        if (action == Action.ICE) {
            activateIce(player);
        } else {
            activateFire(player);
        }

        startCooldown(player);
        return true;
    }

    /**
     * Execute ice ability - freeze entities and place ice blocks.
     */
    private void activateIce(final Player player) {
        if (player == null) {
            return;
        }

        final Location eyeLocation = player.getEyeLocation();
        if (eyeLocation == null || eyeLocation.getWorld() == null) {
            return;
        }

        final Vector direction = eyeLocation.getDirection();

        // Raycast to find target
        Location target = eyeLocation.clone();
        for (int i = 0; i < 30; i++) {
            target = eyeLocation.clone().add(direction.clone().multiply(i));
            final Block block = target.getBlock();
            if (block.getType().isSolid()) {
                break;
            }
        }

        target.add(0, 1, 0);

        // Ice particles
        target.getWorld().spawnParticle(
                Particle.SNOWFLAKE,
                target,
                100,
                2.0, 2.0, 2.0, 0.2
        );
        target.getWorld().spawnParticle(
                Particle.CLOUD,
                target,
                50,
                3.0, 3.0, 3.0, 0.05
        );

        target.getWorld().playSound(target, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
        target.getWorld().playSound(target, Sound.BLOCK_POWDER_SNOW_PLACE, 1.5f, 1.0f);

        // Place ice blocks
        freezeArea(player, target);

        // Freeze nearby entities
        final Collection<Entity> nearby = target.getWorld().getNearbyEntities(
                target, iceRadius, iceRadius, iceRadius
        );

        for (final Entity entity : nearby) {
            if (entity.equals(player)) {
                continue;
            }

            if (entity instanceof LivingEntity living) {
                living.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS,
                        iceDuration / 50,
                        iceSlowAmplifier,
                        false, true
                ));
                living.addPotionEffect(new PotionEffect(
                        PotionEffectType.MINING_FATIGUE,
                        iceDuration / 50,
                        2,
                        false, true
                ));
            }
        }
    }

    /**
     * Place ice blocks around the target location.
     */
    private void freezeArea(final Player player, final Location center) {
        final int radius = (int) iceRadius;
        final MHAPlugin plugin = JavaPlugin.getPlugin(MHAPlugin.class);

        // Check WorldGuard protection
        if (!ProtectionManager.canBreakBlock(player, center)) {
            TextUtil.actionBar(player, "§cCannot freeze blocks in protected regions!");
            return;
        }

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    final Location loc = center.clone().add(x, y, z);
                    final Block block = loc.getBlock();

                    // Skip if protected
                    if (!ProtectionManager.canBreakBlock(player, loc)) {
                        continue;
                    }

                    // Place ice on air blocks and turn water to ice
                    if (block.getType() == Material.WATER) {
                        plugin.getDestructionManager().recordBlockChange(player, block);
                        block.setType(Material.ICE);
                    } else if (block.getType() == Material.AIR && loc.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                        plugin.getDestructionManager().recordBlockChange(player, block);
                        block.setType(Material.FROSTED_ICE);
                    }
                }
            }
        }
    }

    /**
     * Execute fire ability - ignite entities and create fire trail.
     */
    private void activateFire(final Player player) {
        if (player == null) {
            return;
        }

        final Location eyeLocation = player.getEyeLocation();
        if (eyeLocation == null || eyeLocation.getWorld() == null) {
            return;
        }

        final Vector direction = eyeLocation.getDirection();

        // Raycast to find target
        Location target = eyeLocation.clone();
        for (int i = 0; i < 30; i++) {
            target = eyeLocation.clone().add(direction.clone().multiply(i));
            final Block block = target.getBlock();
            if (block.getType().isSolid()) {
                break;
            }
        }

        target.add(0, 1, 0);

        // Fire particles
        target.getWorld().spawnParticle(
                Particle.FLAME,
                target,
                150,
                2.0, 2.0, 2.0, 0.3
        );
        target.getWorld().spawnParticle(
                Particle.LAVA,
                target,
                20,
                1.5, 1.5, 1.5, 0.1
        );
        target.getWorld().spawnParticle(
                Particle.SMOKE,
                target,
                30,
                2.0, 2.0, 2.0, 0.1
        );

        target.getWorld().playSound(target, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 1.0f);
        target.getWorld().playSound(target, Sound.ITEM_FIRECHARGE_USE, 1.5f, 0.8f);

        // Ignite area
        igniteArea(player, target);

        // Ignite nearby entities
        final Collection<Entity> nearby = target.getWorld().getNearbyEntities(
                target, fireRadius, fireRadius, fireRadius
        );

        for (final Entity entity : nearby) {
            if (entity.equals(player)) {
                continue;
            }

            if (entity instanceof LivingEntity living) {
                living.setFireTicks(fireTicks);
                // Small knockback away from fire
                final Vector knockback = living.getLocation().toVector()
                        .subtract(target.toVector())
                        .normalize()
                        .multiply(0.5);
                living.setVelocity(living.getVelocity().add(knockback));
            }
        }
    }

    /**
     * Create fire blocks around the target location.
     */
    private void igniteArea(final Player player, final Location center) {
        final int radius = (int) fireRadius;
        final MHAPlugin plugin = JavaPlugin.getPlugin(MHAPlugin.class);

        // Check WorldGuard protection
        if (!ProtectionManager.canBreakBlock(player, center)) {
            TextUtil.actionBar(player, "§cCannot ignite blocks in protected regions!");
            return;
        }

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    final Location loc = center.clone().add(x, y, z);
                    final Block block = loc.getBlock();

                    // Skip if protected
                    if (!ProtectionManager.canBreakBlock(player, loc)) {
                        continue;
                    }

                    // Place on solid blocks
                    if (block.getType() == Material.AIR && loc.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                        plugin.getDestructionManager().recordBlockChange(player, block);
                        block.setType(Material.FIRE);
                    }
                }
            }
        }
    }

    /**
     * Enum for action type (ice or fire).
     */
    public enum Action {
        ICE, FIRE
    }
}
