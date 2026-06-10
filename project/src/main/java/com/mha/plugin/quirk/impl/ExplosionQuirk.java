package com.mha.plugin.quirk.impl;

import com.mha.plugin.MHAPlugin;
import com.mha.plugin.util.TextUtil;
import com.mha.plugin.quirk.Quirk;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.stamina.StaminaManager;
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
import org.bukkit.util.Vector;

import java.util.Collection;

/**
 * Explosion Quirk - Bakugo Katsuki's ability.
 * Creates explosions that damage entities and launch the user.
 */
public final class ExplosionQuirk extends Quirk {

    private final double damage;
    private final double range;
    private final boolean breakBlocks;
    private final double launchMultiplier;

    public ExplosionQuirk(final ConfigManager config, final StaminaManager staminaManager) {
        super(QuirkType.EXPLOSION, config, staminaManager);

        this.damage = getConfigDouble("damage", 6.0);
        this.range = getConfigDouble("range", 8.0);
        this.breakBlocks = getConfigBoolean("break-blocks", true);
        this.launchMultiplier = getConfigDouble("launch-multiplier", 1.5);
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

        final Location targetLocation = getTargetLocation(player);
        if (targetLocation == null || targetLocation.getWorld() == null) {
            return false;
        }

        createExplosion(player, targetLocation);

        startCooldown(player);
        return true;
    }

    /**
     * Create the explosion effect at the target location.
     */
    private void createExplosion(final Player player, final Location location) {
        if (location == null || location.getWorld() == null || player == null) {
            return;
        }

        // Visual and sound effects
        location.getWorld().spawnParticle(
                Particle.EXPLOSION_EMITTER,
                location,
                1,
                0, 0, 0, 0
        );
        location.getWorld().spawnParticle(
                Particle.FLAME,
                location,
                50,
                2.0, 2.0, 2.0, 0.5
        );
        location.getWorld().spawnParticle(
                Particle.LARGE_SMOKE,
                location,
                30,
                2.0, 2.0, 2.0, 0.2
        );

        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);

        // Damage nearby entities
        final Collection<Entity> nearby = location.getWorld().getNearbyEntities(
                location, range, range, range
        );

        for (final Entity entity : nearby) {
            if (entity.equals(player)) {
                continue;
            }

            if (entity instanceof LivingEntity living) {
                final double distance = entity.getLocation().distance(location);
                final double scaledDamage = damage * (1.0 - (distance / range));
                living.damage(Math.max(0.5, scaledDamage), player);

                // Knock entities away from explosion center
                final Vector direction = entity.getLocation().toVector()
                        .subtract(location.toVector())
                        .normalize()
                        .multiply(2.0);
                entity.setVelocity(entity.getVelocity().add(direction));
            }
        }

        // Launch player in opposite direction (rocket jump)
        final Vector launchDirection = player.getLocation().getDirection()
                .multiply(-launchMultiplier);
        player.setVelocity(player.getVelocity().add(launchDirection.add(new Vector(0, 0.5, 0))));

        // Break blocks around explosion (if enabled and not protected)
        if (breakBlocks) {
            destroyBlocks(player, location);
        }
    }

    /**
     * Determine the target location for the explosion.
     */
    private Location getTargetLocation(final Player player) {
        // Raycast to find target location
        final Location eyeLocation = player.getEyeLocation();
        final Vector direction = eyeLocation.getDirection();

        // Check up to 30 blocks ahead
        for (int i = 0; i < 30; i++) {
            final Location checkLoc = eyeLocation.clone().add(direction.clone().multiply(i));
            final Block block = checkLoc.getBlock();

            if (block.getType().isSolid()) {
                return checkLoc.subtract(1, 0, 0);
            }
        }

        // Default: explosion at player's position
        return eyeLocation.clone().add(direction.multiply(5));
    }

    /**
     * Destroy weak blocks around the explosion.
     */
    private void destroyBlocks(final Player player, final Location center) {
        final int radius = (int) (range / 2);
        final MHAPlugin plugin = JavaPlugin.getPlugin(MHAPlugin.class);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    final Block block = center.clone()
                            .add(x, y, z)
                            .getBlock();

                    // Only destroy weak blocks (stone, dirt, grass, etc.)
                    final Material type = block.getType();
                    if (isBreakable(type)) {
                        plugin.getDestructionManager().recordBlockChange(player, block);
                        block.breakNaturally(false);
                    }
                }
            }
        }
    }

    /**
     * Check if a block material is breakable by explosion.
     */
    private boolean isBreakable(final Material material) {
        return switch (material) {
            case STONE, COBBLESTONE, DIRT, GRASS_BLOCK, SAND, GRAVEL,
                 OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG, ACACIA_LOG,
                 DARK_OAK_LOG, GLASS, WHITE_WOOL,
                 OAK_LEAVES, SPRUCE_LEAVES, BIRCH_LEAVES, JUNGLE_LEAVES,
                 ACACIA_LEAVES, DARK_OAK_LEAVES -> true;
            default -> false;
        };
    }
}
