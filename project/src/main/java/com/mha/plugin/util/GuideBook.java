package com.mha.plugin.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Builds the "Hero Society Handbook" written book that new players receive,
 * containing the how-to-play guide.
 */
public final class GuideBook {

    public static final String TITLE = "Hero Society Handbook";
    private static final String AUTHOR = "Hero Society";

    private GuideBook() {
    }

    /**
     * Build a fresh handbook item.
     */
    public static ItemStack create() {
        final ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        final BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle(TITLE);
            meta.setAuthor(AUTHOR);
            meta.setPages(pages());
            book.setItemMeta(meta);
        }
        return book;
    }

    /**
     * Give the handbook to a player, dropping it at their feet if the inventory
     * is full so it is never silently lost.
     */
    public static void give(final Player player) {
        final ItemStack book = create();
        final Map<Integer, ItemStack> leftover = player.getInventory().addItem(book);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), book);
        }
        player.sendMessage("§6§l★ §eYou received the §6Hero Society Handbook§e! §7(read it for tips)");
    }

    private static List<String> pages() {
        return Arrays.asList(
                "§0§lHERO SOCIETY\n§0§lHANDBOOK\n\n§8Welcome, hero (or villain).\n\n§8This handbook explains how to survive and thrive with your Quirk.\n\n§8Turn the page to begin. ->",

                "§1§l1. Your Quirk\n\n§0On first join you undergo an §1Awakening Ceremony§0 revealing your power.\n\n§0§lActivate:§r§0 Left-click with an empty hand.\n\n§0§lUltimate:§r§0 Sneak (Shift) + Right-click.",

                "§1§l1. Your Quirk\n\n§0§lCooldowns:§r§0 Every ability has one — watch the timer in your §1Action Bar§0. A sound plays when it's ready again.",

                "§4§l2. Stamina\n\n§0You can't spam powers. Your §4Stamina Bar§0 sits at the top of your screen.\n\n§0Each ability costs stamina. At §40§0 you become §4Exhausted§0 and must rest.",

                "§4§l2. Stamina\n\n§0§lPro tip:§r§0 Manage your energy. Don't waste stamina on small targets if a boss fight or PvP is near.",

                "§2§l3. Passives\n\n§0Some Quirks grant permanent buffs:\n\n§2Frog:§0 Jump Boost + Water Breathing.\n§2Hardening:§0 Resistance.\n§2Cremation/Electrification:§0 bonus effects on punches.",

                "§5§l4. Reputation\n§5& Bounties\n\n§0Pick a side on spawn:\n§9Heroes§0 wear a blue tag.\n§cVillains§0 wear a red tag.\n\n§0Use §5/mha reputation§0 to check standing.",

                "§5§l4. Reputation\n§5& Bounties\n\n§0§lThe Bounty System:§r§0 If a Hero defeats a Villain, they claim a §5bounty§0 — money deposited via the economy (or diamonds).",

                "§6§l5. Support Gear\n\n§0No powerful Quirk? Fight with gear.\n\n§0§lCapture Scarf:§r§0 Rename String to \"Capture Scarf\" on an anvil, then right-click to grapple enemies toward you.",

                "§8§l6. Commands\n\n§0/mha help\n§0/mha quirks\n§0/mha stamina\n§0/mha reputation\n§0/mha align\n§0/mha guide\n\n§8Power is earned, not given. Good luck!"
        );
    }
}
