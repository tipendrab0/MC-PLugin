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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DecayQuirk extends Quirk {

    private final double witherDamage;
    private final int witherDuration;
    private final int blockDecayRadius;
    private final double entityDecayRadius;
    private final int decaySpreadDelay;
    private final Map<UUID, Integer> decayedTiers;
    private final Set<Location> activelyDecaying;

    public DecayQuirk(final ConfigManager config, final StaminaManager staminaManager) {
        super(QuirkType.DECAY, config, staminaManager);

        this.witherDamage = config.getQuirkNestedDouble("decay", "wither", "damage", 4.0);
        this.witherDuration = config.getQuirkNestedInt("decay", "wither", "duration-ticks", 100);
        this.blockDecayRadius = config.getQuirkNestedInt("decay", "blocks", "radius", 3);
        this.entityDecayRadius = config.getQuirkNestedDouble("decay", "entity", "radius", 4.0);
        this.decaySpreadDelay = config.getQuirkNestedInt("decay", "spread", "delay-ticks", 5);

        this.decayedTiers = new ConcurrentHashMap<>();
        this.activelyDecaying = ConcurrentHashMap.newKeySet();
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

        if (player.isSneaking()) {
            activateSpreadingDecay(player);
        } else {
            activateTouchDecay(player);
        }

        startCooldown(player);
        return true;
    }

    private void activateTouchDecay(final Player player) {
        final Entity target = getTargetEntity(player);

        if (target instanceof LivingEntity living) {
            applyDecay(player, living);
        } else {
            final Block targetBlock = player.getTargetBlockExact(8);
            if (targetBlock != null && targetBlock.getWorld() != null) {
                decayBlocks(player, targetBlock.getLocation(), blockDecayRadius);
            } else {
                TextUtil.actionBar(player, "§cNo target in range!");
                resetCooldown(player);
            }
        } // Fixed braces here!
    }

    private void activateSpreadingDecay(final Player player) {
        final Location origin = player.getLocation().clone();

        player.getWorld().playSound(origin, Sound.ENTITY_WITHER_SHOOT, 2.0f, 0.5f);
        TextUtil.actionBar(player, "§4§lDECAY SPREADING! §cEverything crumbles!");
        player.sendMessage("§4§l>>> DECAY - SPREADING WAVE <<<");

        final int maxRadius = 12;
        new BukkitRunnable() {
            int currentRadius = 0;

            @Override
            public void run() {
                if (currentRadius++ > maxRadius || !player.isOnline()) {
                    cancel();
                    return;
                }

                final Location center = origin.clone();
                for (int angle = 0; angle < 360; angle += 15) {
                    final double rad = Math.toRadians(angle);
                    final double x = Math.cos(rad) * currentRadius;
                    final double z = Math.sin(rad) * currentRadius;

                    final Location ringLoc = center.clone().add(x, 0, z);
                    decayBlocks(player, ringLoc, 1);
                }

                for (final Entity entity : origin.getWorld().getNearbyEntities(origin, currentRadius, 3, currentRadius)) {
                    if (entity.equals(player)) {
                        continue;
                    }
                    if (entity.getLocation().distance(origin) > currentRadius) {
                        continue;
                    }
                    if (entity instanceof LivingEntity living) {
                        applyDecay(player, living);
                    }
                }

                for (int angle = 0; angle < 360; angle += 10) {
                    final double rad = Math.toRadians(angle);
                    final double x = Math.cos(rad) * currentRadius;
                    final double z = Math.sin(rad) * currentRadius;
                    final Location ringLoc = origin.clone().add(x, 0.5, z);
                    origin.getWorld().spawnParticle(Particle.ASH, ringLoc, 5, 0.3, 0.3, 0.3, 0.02);
                }
            }
        }.runTaskTimer(JavaPlugin.getPlugin(MHAPlugin.class), 0L, decaySpreadDelay); // Fixed Plugin Instance!
    }

    private void applyDecay(final Player source, final LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, witherDuration, 2, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, witherDuration, 3, false, true));
        target.damage(witherDamage, source);

        final int tier = decayedTiers.getOrDefault(target.getUniqueId(), 0) + 1;
        decayedTiers.put(target.getUniqueId(), tier);

        target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.05);
        target.getWorld().spawnParticle(Particle.ASH, target.getLocation().add(0, 0.5, 0), 50, 0.5, 0.5, 0.5, 0.02);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_SAND_BREAK, 1.5f, 0.8f);

        TextUtil.actionBar(source, "§4§lDECAY! §7Target withering...");

        if (tier >= 5) {
            target.damage(1000.0, source); 
            decayedTiers.remove(target.getUniqueId());
            target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, target.getLocation(), 100, 0.5, 1.0, 0.5, 0.1);
        }
    }

    private void decayBlocks(final Player source, final Location center, final int radius) {
        if (center.getWorld() == null) return;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    final Block block = center.clone().add(x, y, z).getBlock();

                    if (!block.getType().isAir() && block.getType() != Material.BEDROCK) {
                        final Material originalType = block.getType();
                        JavaPlugin.getPlugin(MHAPlugin.class).getDestructionManager().recordBlockChange(source, block);
                        block.setType(Material.AIR);
                        center.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 20, 0.2, 0.2, 0.2, 0.1, originalType.createBlockData());
                        center.getWorld().playSound(block.getLocation(), Sound.BLOCK_SAND_BREAK, 0.5f, 0.7f);
                    }
                }
            }
        }
    }

    private Entity getTargetEntity(final Player player) {
        return player.getNearbyEntities(6, 6, 6).stream()
                .filter(e -> e instanceof LivingEntity)
                .filter(e -> e.getLocation().toVector().subtract(player.getEyeLocation().toVector())
                        .dot(player.getEyeLocation().getDirection()) > 0.8)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void clearAllCooldowns() {
        decayedTiers.clear();
        activelyDecaying.clear();
        super.clearAllCooldowns();
    }
}