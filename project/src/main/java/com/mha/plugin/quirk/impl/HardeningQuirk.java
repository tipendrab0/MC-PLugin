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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hardening Quirk - Kirishima Eijiro's ability.
 * Grants damage resistance and powerful offensive strikes with Red Riot mode.
 */
public final class HardeningQuirk extends Quirk implements Listener {

    private final int armorDuration;
    private final int armorBonus;
    private final double punchDamage;
    private final double redRiotDamageMultiplier;
    private final int redRiotDuration;
    private final Map<UUID, Long> activeArmors;
    private final Map<UUID, Long> redRiotMode;

    public HardeningQuirk(final ConfigManager config, final StaminaManager staminaManager) {
        super(QuirkType.HARDENING, config, staminaManager);

        this.armorDuration = config.getQuirkNestedInt("hardening", "armor", "duration-ticks", 200);
        this.armorBonus = config.getQuirkNestedInt("hardening", "armor", "bonus-level", 6);
        this.punchDamage = config.getQuirkNestedDouble("hardening", "punch", "damage", 6.0);
        this.redRiotDamageMultiplier = config.getQuirkNestedDouble("hardening", "red-riot", "damage-multiplier", 2.5);
        this.redRiotDuration = config.getQuirkNestedInt("hardening", "red-riot", "duration-ticks", 100);

        this.activeArmors = new ConcurrentHashMap<>();
        this.redRiotMode = new ConcurrentHashMap<>();
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

        // Sneak for Red Riot Unbreakable, normal for Hardening armor
        if (player.isSneaking()) {
            activateRedRiot(player);
        } else {
            activateHardening(player);
        }

        startCooldown(player);
        return true;
    }

    /**
     * Activate Hardening armor - damage resistance.
     */
    private void activateHardening(final Player player) {
        // Apply resistance
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, armorDuration, armorBonus, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, armorDuration, 1, false, true));

        activeArmors.put(player.getUniqueId(), System.currentTimeMillis() + (armorDuration * 50L));

        // Visual - crystalline armor effect
        player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation().add(0, 1, 0), 50, 0.5, 0.8, 0.5, 0.1, org.bukkit.Material.QUARTZ_BLOCK.createBlockData());
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.5f);

        TextUtil.actionBar(player, "§7§lHARDENING! §8Steel armor active!");
        player.sendMessage("§7»» Hardening - ACTIVATED ««");

        // Armor particle trail
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > armorDuration || !player.isOnline()) {
                    activeArmors.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
            }
        }.runTaskTimer(org.bukkit.Bukkit.getPluginManager().getPlugin("MHAPlugin"), 0L, 5L);
    }

    /**
     * Activate Red Riot Unbreakable - ultimate defense mode.
     */
    private void activateRedRiot(final Player player) {
        redRiotMode.put(player.getUniqueId(), System.currentTimeMillis() + (redRiotDuration * 50L));

        // Maximum resistance
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, redRiotDuration, 10, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, redRiotDuration, 2, false, true));

        // Visual - fiery red aura
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > redRiotDuration || !player.isOnline()) {
                    redRiotMode.remove(player.getUniqueId());
                    TextUtil.actionBar(player, "§7Red Riot mode ended.");
                    cancel();
                    return;
                }

                // Fiery red particles
                player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0, 1.5, 0), 30, 0.6, 0.8, 0.6, 0.1);
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1.2, 0), 20, 0.5, 0.5, 0.5, 0.05);

                // Damage nearby enemies each tick
                if (ticks % 10 == 0) {
                    for (final Entity entity : player.getNearbyEntities(3, 3, 3)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(punchDamage * redRiotDamageMultiplier, player);
                            living.getWorld().spawnParticle(Particle.EXPLOSION, living.getLocation(), 3, 0.5, 0.5, 0.5, 0);
                        }
                    }
                }
            }
        }.runTaskTimer(org.bukkit.Bukkit.getPluginManager().getPlugin("MHAPlugin"), 0L, 2L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1.5f, 1.0f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 2.0f, 0.9f);

        TextUtil.actionBar(player, "§4§lRED RIOT UNBREAKABLE! §cMAXIMUM DEFENSE!");
        player.sendMessage("§4§l>>> RED RIOT UNBREAKABLE - ACTIVATED <<<");
        player.sendTitle("§4RED RIOT!", "§cUnbreakable Mode!", 5, 50, 10);
    }

    /**
     * Check if player has active hardening armor.
     */
    public boolean hasArmor(final UUID playerId) {
        final Long expiry = activeArmors.get(playerId);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    /**
     * Check if player is in Red Riot mode.
     */
    public boolean isInRedRiot(final UUID playerId) {
        final Long expiry = redRiotMode.get(playerId);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    /**
     * Handle damage reduction for hardening armor.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Additional damage reduction for Red Riot
        if (isInRedRiot(player.getUniqueId())) {
            event.setDamage(event.getDamage() * 0.1); // 90% damage reduction
        }

        // Spawn armor crack particles when taking damage with hardening
        if (hasArmor(player.getUniqueId())) {
            player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1, org.bukkit.Material.QUARTZ_BLOCK.createBlockData());
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.2f);
        }
    }

    @Override
    public void clearAllCooldowns() {
        activeArmors.clear();
        redRiotMode.clear();
        super.clearAllCooldowns();
    }

    /**
     * Cleanup for shutdown.
     */
    public void shutdown() {
        activeArmors.clear();
        redRiotMode.clear();
        clearAllCooldowns();
    }
}
