package com.mha.plugin.quirk.impl;

import com.mha.plugin.MHAPlugin;
import com.mha.plugin.quirk.Quirk;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.stamina.StaminaManager;
import com.mha.plugin.util.ConfigManager;
import com.mha.plugin.util.TextUtil;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BloodcurdleQuirk extends Quirk implements Listener {

    private final int paralysisDuration;
    private final double maxRange;
    private final Map<UUID, LivingEntity> bloodSamples = new ConcurrentHashMap<>();

    public BloodcurdleQuirk(final ConfigManager config, final StaminaManager staminaManager) {
        super(QuirkType.BLOODCURDLE, config, staminaManager);
        this.paralysisDuration = getConfigInt("paralysis-duration-ticks", 100);
        this.maxRange = getConfigDouble("max-range", 30.0);
    }

    @EventHandler
    public void onAttack(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof LivingEntity target) {
            final MHAPlugin plugin = JavaPlugin.getPlugin(MHAPlugin.class);

            if (plugin.getQuirkManager().getPlayerQuirkType(player) == QuirkType.BLOODCURDLE) {
                bloodSamples.put(player.getUniqueId(), target);
                TextUtil.actionBar(player, "§4[!] Blood sample acquired from " + target.getName());
            }
        }
    }

    @Override
    public boolean activate(final Player player) {
        if (!canUse(player)) {
            return false;
        }

        if (!consumeStamina(player)) {
            TextUtil.actionBar(player, "§cNot enough stamina!");
            return false;
        }

        final LivingEntity target = bloodSamples.get(player.getUniqueId());

        // Check for valid target
        if (target == null || target.isDead()) {
            player.sendMessage("§cYou don't have a fresh blood sample!");
            resetCooldown(player);
            staminaManager.restoreStamina(player, getStaminaCost()); // Refund stamina
            return false;
        }

        // Check if target is in same world before calculating distance
        if (!target.getWorld().equals(player.getWorld())) {
            player.sendMessage("§cTarget is in a different dimension!");
            bloodSamples.remove(player.getUniqueId());
            resetCooldown(player);
            staminaManager.restoreStamina(player, getStaminaCost()); // Refund stamina
            return false;
        }

        // Now safe to calculate distance
        if (target.getLocation().distance(player.getLocation()) > maxRange) {
            player.sendMessage("§cTarget is too far away!");
            resetCooldown(player);
            staminaManager.restoreStamina(player, getStaminaCost()); // Refund stamina
            return false;
        }

        // SLOWNESS at max amplifier immobilizes targets (Paper 1.21+ registry name)
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, paralysisDuration, 127, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, paralysisDuration, 127, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, paralysisDuration / 2, 0, false, true, true));
        target.setVelocity(target.getVelocity().zero());

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.0f, 1.0f);
        player.sendMessage("§4§lBLOODCURDLE! §cYou paralyzed " + target.getName() + "!");

        bloodSamples.remove(player.getUniqueId());
        startCooldown(player);
        return true;
    }

    @Override
    public void onRemove(final Player player) {
        bloodSamples.remove(player.getUniqueId());
        super.onRemove(player);
    }
}
