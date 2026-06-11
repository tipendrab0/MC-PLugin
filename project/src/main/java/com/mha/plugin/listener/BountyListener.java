package com.mha.plugin.listener;

import com.mha.plugin.MHAPlugin;
import com.mha.plugin.reputation.ReputationManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles bounty claims when heroes defeat villains.
 * Includes anti-exploit protections:
 * - Tracks recent deaths to prevent duplicate claims
 * - Clears bounty-eligible status on death (claimed once)
 */
public class BountyListener implements Listener {

    private final ReputationManager repManager;
    private final Economy econ;
    private final Map<UUID, Long> recentDeaths;
    private static final long DEATH_COOLDOWN_MS = 5000; // 5 second window to prevent duplicates

    public BountyListener(MHAPlugin plugin) {
        this.repManager = plugin.getReputationManager();
        this.econ = plugin.getEconomy();
        this.recentDeaths = new ConcurrentHashMap<>();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVillainDefeated(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Anti-exploit: Check for duplicate death processing
        final long now = System.currentTimeMillis();
        final Long lastDeath = recentDeaths.get(victim.getUniqueId());
        if (lastDeath != null && now - lastDeath < DEATH_COOLDOWN_MS) {
            return; // Already processed this death
        }
        recentDeaths.put(victim.getUniqueId(), now);

        // Clean up old death records periodically
        recentDeaths.entrySet().removeIf(entry -> now - entry.getValue() > DEATH_COOLDOWN_MS * 2);

        // Check if it was a PvP kill
        if (killer == null || killer.equals(victim)) {
            return;
        }

        // Verify killer is online (prevent offline exploitation)
        if (!killer.isOnline()) {
            return;
        }

        int victimScore = repManager.getReputationScore(victim);
        int killerScore = repManager.getReputationScore(killer);

        // If the victim is a Villain (negative score) and the killer is a Hero (positive score)
        if (victimScore < -50 && killerScore > 0) {
            // Calculate bounty based on how evil they are
            double bountyAmount = Math.abs(victimScore) * 10.0;

            // Cap bounty to prevent huge payouts
            bountyAmount = Math.min(bountyAmount, 10000.0);

            if (bountyAmount > 0) {
                // If Vault is installed, give them virtual money
                if (econ != null) {
                    econ.depositPlayer(killer, bountyAmount);
                    killer.sendTitle("§bBOUNTY CLAIMED!", "§7Earned " + econ.format(bountyAmount), 10, 40, 10);
                    killer.playSound(killer.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                } else {
                    // Fallback: Drop diamonds (capped at 32)
                    int diamondsToDrop = Math.min((int)(bountyAmount / 50), 32);
                    if (diamondsToDrop > 0) {
                        victim.getWorld().dropItemNaturally(victim.getLocation(), new ItemStack(Material.DIAMOND, diamondsToDrop));
                        killer.sendTitle("§bBOUNTY CLAIMED!", "§7" + diamondsToDrop + " diamonds earned!", 10, 40, 10);
                    }
                }

                // Reward the hero with reputation boost
                repManager.addHeroPoints(killer, 10);
            }
        }
    }

    /**
     * Clean up death records when player respawns.
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        recentDeaths.remove(event.getPlayer().getUniqueId());
    }
}