package com.mha.plugin.quirk.impl;

import com.mha.plugin.quirk.Quirk;
import com.mha.plugin.util.TextUtil;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.util.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Creation Quirk - Yaoyorozu Momo's ability.
 * Create items and blocks from body lipids (stamina drain).
 */
public final class CreationQuirk extends Quirk {

    private final int creationTime;
    private final int staminaPerItem;
    private final List<Material> craftableItems;
    private final Random random;

    public CreationQuirk(final ConfigManager config) {
        super(QuirkType.CREATION, config);

        this.creationTime = getConfigInt("creation-time-ticks", 40);
        this.staminaPerItem = getConfigInt("stamina-per-item", 15);
        this.craftableItems = new ArrayList<>();
        this.random = new Random();

        // Add craftable items
       Collections.addAll(craftableItems,
                Material.IRON_SWORD, Material.IRON_PICKAXE, Material.IRON_AXE,
                Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                Material.BOW, Material.ARROW, Material.TNT, Material.ENDER_PEARL,
                Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND,
                Material.GOLDEN_APPLE, Material.SHIELD, Material.FLINT_AND_STEEL,
                Material.COMPASS, Material.CLOCK, Material.TORCH, Material.OAK_PLANKS
        );
    }

    @Override
    public boolean activate(final Player player) {
        if (!canUse(player)) {
            return false;
        }

        // Sneak for random item, normal for useful combat item
        if (player.isSneaking()) {
            createRandomItem(player);
        } else {
            createCombatItem(player);
        }

        startCooldown(player);
        return true;
    }

    /**
     * Create a combat-focused item.
     */
    private void createCombatItem(final Player player) {
        final List<Material> combatItems = Arrays.asList(
                Material.IRON_SWORD, Material.BOW, Material.ARROW,
                Material.TNT, Material.ENDER_PEARL, Material.SHIELD,
                Material.GOLDEN_APPLE
        );

        final Material item = combatItems.get(random.nextInt(combatItems.size()));
        startCreationProcess(player, item, true);
    }

    /**
     * Create a random item from full list.
     */
    private void createRandomItem(final Player player) {
        final Material item = craftableItems.get(random.nextInt(craftableItems.size()));
        startCreationProcess(player, item, false);
    }

    /**
     * Start the creation process with visual effects.
     */
    private void startCreationProcess(final Player player, final Material material, final boolean isCombat) {
        final Location loc = player.getLocation().add(0, 1, 0);

        TextUtil.actionBar(player, "§b§lCREATING: §f" + formatMaterialName(material));
        player.getWorld().playSound(loc, Sound.BLOCK_BAMBOO_BREAK, 1.0f, 0.8f);

        // Creation particles
        final int ticks = creationTime / 2;
        for (int i = 0; i < ticks; i++) {
            final int progress = i + 1;
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                    org.bukkit.Bukkit.getPluginManager().getPlugin("MHAPlugin"),
                    () -> {
                        if (!player.isOnline()) return;
                        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0);
                        player.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1.2, 0), 5, 0.3, 0.3, 0.3, 0);

                    },
                    i * 2L
            );
        }

        // Final delivery
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("MHAPlugin"),
                () -> {
                    if (!player.isOnline()) return;

                    final ItemStack itemStack = new ItemStack(material, isCombat ? 16 : 4);
                    player.getInventory().addItem(itemStack);

                    player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);
                    player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    player.playSound(loc, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0f, 1.5f);

                    player.sendTitle("§bCREATED!", "§f" + formatMaterialName(material) + " x" + itemStack.getAmount(), 5, 30, 5);
                },
                creationTime
        );

    }

    /**
     * Format material name for display.
     */
    private String formatMaterialName(final Material material) {
        final String[] words = material.name().split("_");
        final StringBuilder sb = new StringBuilder();
        for (final String word : words) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }
}
