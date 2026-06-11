package com.mha.plugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * The "Choose Your Path" menu shown to new players so they can pick the Hero or
 * Villain alignment.
 */
public final class AlignmentGui {

    public static final String TITLE = "§8» §lChoose Your Path";
    public static final int HERO_SLOT = 11;
    public static final int VILLAIN_SLOT = 15;

    private AlignmentGui() {
    }

    /**
     * Marker holder so the click/close listeners can recognise this menu.
     */
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void setInventory(final Inventory inventory) {
            this.inventory = inventory;
        }
    }

    /**
     * Build the selection inventory.
     */
    public static Inventory build() {
        final Holder holder = new Holder();
        final Inventory inv = Bukkit.createInventory(holder, 27, TITLE);
        holder.setInventory(inv);

        final ItemStack filler = pane();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(HERO_SLOT, named(Material.SHIELD, "§9§lHERO",
                "§7Protect the innocent and uphold justice.",
                "",
                "§7• Name tag turns §9blue",
                "§7• Hunt villains to claim §bbounties",
                "",
                "§eClick to become a Hero"));

        inv.setItem(VILLAIN_SLOT, named(Material.NETHERITE_SWORD, "§c§lVILLAIN",
                "§7Spread chaos and rule through fear.",
                "",
                "§7• Name tag turns §cred",
                "§7• Heroes can claim a §cbounty on you",
                "",
                "§eClick to become a Villain"));

        return inv;
    }

    /**
     * Open the menu for a player.
     */
    public static void open(final Player player) {
        player.openInventory(build());
    }

    private static ItemStack pane() {
        return named(Material.BLACK_STAINED_GLASS_PANE, "§r");
    }

    private static ItemStack named(final Material material, final String name, final String... lore) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                final List<String> loreList = Arrays.asList(lore);
                meta.setLore(loreList);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
