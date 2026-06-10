package com.mha.plugin.listener;

import com.mha.plugin.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class SupportGearListener implements Listener {

    private final NamespacedKey captureScarfKey;

    public SupportGearListener(final JavaPlugin plugin) {
        this.captureScarfKey = new NamespacedKey(plugin, "capture-scarf");
    }

    @EventHandler
    public void onUseGear(final PlayerInteractEvent event) {
        final Player player = event.getPlayer();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        final ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.STRING || !isCaptureScarf(held)) {
            return;
        }

        useCaptureScarf(player);
        event.setCancelled(true);
    }

    private boolean isCaptureScarf(final ItemStack item) {
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        if (meta.getPersistentDataContainer().has(captureScarfKey, PersistentDataType.BYTE)) {
            return true;
        }

        final Component displayName = meta.displayName();
        return displayName != null && TextUtil.plainText(displayName).contains("Capture Scarf");
    }

    private void useCaptureScarf(final Player player) {
        if (player.hasCooldown(Material.STRING)) {
            return;
        }

        final Entity target = getTargetEntity(player);
        if (target != null) {
            drawLine(player.getEyeLocation(), target.getLocation().add(0, 1, 0));

            final Vector pull = player.getLocation().toVector()
                    .subtract(target.getLocation().toVector())
                    .normalize()
                    .multiply(1.5)
                    .setY(0.5);
            target.setVelocity(pull);

            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 1.0f);
            player.setCooldown(Material.STRING, 60);
        } else {
            player.sendMessage("§cNo target in range!");
        }
    }

    private Entity getTargetEntity(final Player player) {
        return player.getNearbyEntities(15, 15, 15).stream()
                .filter(e -> e instanceof LivingEntity)
                .filter(e -> e.getLocation().toVector().subtract(player.getEyeLocation().toVector())
                        .dot(player.getEyeLocation().getDirection()) > 0.9)
                .findFirst()
                .orElse(null);
    }

    private void drawLine(final org.bukkit.Location loc1, final org.bukkit.Location loc2) {
        final double distance = loc1.distance(loc2);
        final Vector p1 = loc1.toVector();
        final Vector p2 = loc2.toVector();
        final Vector vector = p2.clone().subtract(p1).normalize().multiply(0.5);
        double length = 0;
        for (; length < distance; p1.add(vector)) {
            loc1.getWorld().spawnParticle(Particle.CRIT, p1.getX(), p1.getY(), p1.getZ(), 1, 0, 0, 0, 0);
            length += 0.5;
        }
    }
}
