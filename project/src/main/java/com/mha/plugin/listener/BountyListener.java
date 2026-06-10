package com.mha.plugin.listener;

import com.mha.plugin.MHAPlugin;
import com.mha.plugin.reputation.ReputationManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

public class BountyListener implements Listener {

    private final ReputationManager repManager;
    private final Economy econ;

    public BountyListener(MHAPlugin plugin) {
        this.repManager = plugin.getReputationManager();
        this.econ = plugin.getEconomy(); // Grab the economy hook
    }

    @EventHandler
    public void onVillainDefeated(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Check if it was a PvP kill
        if (killer != null) {
            int victimScore = repManager.getReputationScore(victim);
            int killerScore = repManager.getReputationScore(killer);

            // If the victim is a Villain (negative score) and the killer is a Hero (positive score)
            if (victimScore < -50 && killerScore > 0) {
                
                // Calculate bounty based on how evil they are. 
                // Let's say $10 for every negative point they have.
                double bountyAmount = Math.abs(victimScore) * 10.0; 

                if (bountyAmount > 0) {
                    // If Vault is installed, give them virtual money
                    if (econ != null) {
                        econ.depositPlayer(killer, bountyAmount);
                        killer.sendMessage("§b§lBOUNTY CLAIMED! §7You earned " + econ.format(bountyAmount) + " for defeating a villain!");
                    } 
                    // Fallback: If no Vault/Economy plugin, drop physical diamonds instead
                    else {
                        int diamondsToDrop = Math.min((int)(bountyAmount / 50), 32); // Max 32 diamonds
                        if (diamondsToDrop > 0) {
                            victim.getWorld().dropItemNaturally(victim.getLocation(), new ItemStack(Material.DIAMOND, diamondsToDrop));
                            killer.sendMessage("§b§lBOUNTY CLAIMED! §7You defeated a villain and earned diamonds!");
                        }
                    }
                    
                    // Boost the hero's reputation slightly for doing good
                    // repManager.addReputation(killer, 10); 
                }
            }
        }
    }
}