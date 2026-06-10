package com.mha.plugin.listener;

import com.mha.plugin.MHAPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {
    
    private final MHAPlugin plugin;

    public PlayerQuitListener(MHAPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 1. Save data before removing them
        plugin.getQuirkManager().saveQuirkAssignments();
        plugin.getReputationManager().saveAllReputation();

        // 2. Clear them from RAM to prevent memory leaks
        plugin.getQuirkManager().onPlayerQuit(event.getPlayer().getUniqueId());
    }
}