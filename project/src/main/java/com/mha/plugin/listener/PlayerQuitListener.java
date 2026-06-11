package com.mha.plugin.listener;

import com.mha.plugin.MHAPlugin;
import com.mha.plugin.quirk.QuirkManager;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.quirk.impl.ZeroGravityQuirk;
import com.mha.plugin.stamina.StaminaManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final MHAPlugin plugin;
    private final StaminaManager staminaManager;

    public PlayerQuitListener(MHAPlugin plugin) {
        this.plugin = plugin;
        this.staminaManager = null;
    }

    public PlayerQuitListener(MHAPlugin plugin, StaminaManager staminaManager) {
        this.plugin = plugin;
        this.staminaManager = staminaManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final QuirkManager quirkManager = plugin.getQuirkManager();

        // 1. Save data before removing them
        quirkManager.saveQuirkAssignments();
        plugin.getReputationManager().saveAllReputation();

        // 2. Clean up any Floating effects this player caused on others
        if (quirkManager.getPlayerQuirkType(player) == QuirkType.ZERO_GRAVITY) {
            final var zeroGravity = quirkManager.getQuirk(QuirkType.ZERO_GRAVITY);
            if (zeroGravity instanceof ZeroGravityQuirk zgQuirk) {
                // Remove all floating targets that this player created
                zgQuirk.removeAllTargetsByActivator(player.getUniqueId());
            }
        }

        // 3. If this player was floating (victim of someone else's quirk), restore them
        final var zeroGravity = quirkManager.getQuirk(QuirkType.ZERO_GRAVITY);
        if (zeroGravity instanceof ZeroGravityQuirk zgQuirk) {
            zgQuirk.stopLevitation(player.getUniqueId());
        }

        // 4. Clear stamina data and other RAM to prevent memory leaks
        quirkManager.onPlayerQuit(player.getUniqueId());
        if (staminaManager != null) {
            staminaManager.removePlayer(player.getUniqueId());
        }
    }
}