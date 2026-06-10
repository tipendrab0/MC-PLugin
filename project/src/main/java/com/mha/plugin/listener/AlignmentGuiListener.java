package com.mha.plugin.listener;

import com.mha.plugin.gui.AlignmentGui;
import com.mha.plugin.reputation.Alignment;
import com.mha.plugin.reputation.ReputationManager;
import com.mha.plugin.util.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles clicks in the Hero/Villain selection menu and (optionally) re-opens it
 * if a new player tries to dismiss it without choosing a side.
 */
public final class AlignmentGuiListener implements Listener {

    private final JavaPlugin plugin;
    private final ReputationManager reputationManager;
    private final boolean forceChoice;

    public AlignmentGuiListener(final JavaPlugin plugin, final ReputationManager reputationManager,
                                final ConfigManager config) {
        this.plugin = plugin;
        this.reputationManager = reputationManager;
        this.forceChoice = config.getBoolean("alignment.force-choice", true);
    }

    @EventHandler
    public void onClick(final InventoryClickEvent event) {
        final InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof AlignmentGui.Holder)) {
            return;
        }

        // It's our menu: never let items be moved around.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof AlignmentGui.Holder)) {
            return;
        }

        final Alignment choice;
        if (event.getSlot() == AlignmentGui.HERO_SLOT) {
            choice = Alignment.HERO;
        } else if (event.getSlot() == AlignmentGui.VILLAIN_SLOT) {
            choice = Alignment.VILLAIN;
        } else {
            return;
        }

        reputationManager.setAlignment(player, choice);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        player.closeInventory();
    }

    @EventHandler
    public void onClose(final InventoryCloseEvent event) {
        if (!forceChoice) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof AlignmentGui.Holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (reputationManager.hasChosenAlignment(player)) {
            return;
        }

        // Re-open on the next tick so undecided players must pick a side.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !reputationManager.hasChosenAlignment(player)) {
                player.sendMessage("§eYou must choose your path before you can begin!");
                AlignmentGui.open(player);
            }
        }, 2L);
    }
}
