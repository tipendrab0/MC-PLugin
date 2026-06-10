package com.mha.plugin.listener;

import com.mha.plugin.MHAPlugin;
import com.mha.plugin.quirk.QuirkManager;
import com.mha.plugin.quirk.QuirkType;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class QuirkPassiveListener implements Listener {

    private final QuirkManager quirkManager;

    public QuirkPassiveListener(MHAPlugin plugin, QuirkManager quirkManager) {
        this.quirkManager = quirkManager;

        // Start a repeating task to apply permanent potion effects
        // It runs every 40 ticks (2 seconds) and applies a 3-second effect. 
        // This prevents milk or respawning from breaking the passives!
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                QuirkType quirk = quirkManager.getPlayerQuirkType(player);
                applyPassiveEffects(player, quirk);
            }
        }, 0L, 40L);
    }

    private void applyPassiveEffects(Player player, QuirkType quirk) {
        switch (quirk) {
            case FROG -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 1, false, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 60, 0, false, false, false));
            }
            case ENGINE -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, false, false));
            }
            case HARDENING -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, false, false, false));
            }
            case ZERO_GRAVITY -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false, false));
            }
            case ONE_FOR_ALL -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, false, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 1, false, false, false));
            }
            case PERMEATION -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false, false));
            }
            default -> {} // Quirks without passives do nothing
        }
    }

    @EventHandler
    public void onMeleeAttack(EntityDamageByEntityEvent event) {
        // Check if a player punched someone
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof LivingEntity target) {
            QuirkType quirk = quirkManager.getPlayerQuirkType(player);

            switch (quirk) {
                case CREMATION, ICE_FIRE -> {
                    // 30% chance to set enemy on fire for 4 seconds
                    if (Math.random() < 0.3) {
                        target.setFireTicks(80);
                    }
                }
                case ELECTRIFICATION -> {
                    // 20% chance to paralyze (slowness 10) enemy for 2 seconds
                    if (Math.random() < 0.2) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 10));
                        target.getWorld().strikeLightningEffect(target.getLocation());
                    }
                }
                case DECAY -> {
                    // Small chance to apply wither on punch
                    if (Math.random() < 0.25) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
                    }
                }
                case ONE_FOR_ALL -> {
                    // Increase punch damage permanently
                    event.setDamage(event.getDamage() + 4.0);
                }
                default -> {}
            }
        }
    }
}