package com.mha.plugin.listener;

import com.mha.plugin.MHAPlugin;
import com.mha.plugin.reputation.Alignment;
import com.mha.plugin.reputation.ReputationManager;
import com.mha.plugin.util.ConfigManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

public class BountyListener implements Listener {

    private final ReputationManager repManager;
    private final ConfigManager config;
    private final Economy econ;

    public BountyListener(MHAPlugin plugin) {
        this.repManager = plugin.getReputationManager();
        this.config = plugin.getConfigManager();
        this.econ = plugin.getEconomy(); // Grab the economy hook
    }

    @EventHandler
    public void onVillainDefeated(PlayerDeathEvent event) {
        if (!config.getBoolean("bounty.enabled", true)) {
            return;
        }

        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }

        // Eligibility is driven by the player's chosen side, with the score-based
        // rank as a fallback so unaligned-but-notorious players still count.
        final boolean victimIsVillain = repManager.getAlignment(victim) == Alignment.VILLAIN
                || repManager.getRank(victim).isVillainous();
        final boolean killerIsHero = repManager.getAlignment(killer) == Alignment.HERO
                || repManager.getRank(killer).isHeroic();
        if (!victimIsVillain || !killerIsHero) {
            return;
        }

        // Base reward plus a bonus scaled by how deep into villain territory the
        // victim's score is (0 if they're a fresh villain).
        final double base = config.getDouble("bounty.base-amount", 100.0);
        final double perPoint = config.getDouble("bounty.per-villain-point", 10.0);
        final int villainScore = repManager.getReputationScore(victim);
        final int negativeMagnitude = Math.max(0, -villainScore);
        final double bountyAmount = base + negativeMagnitude * perPoint;
        if (bountyAmount <= 0) {
            return;
        }

        if (econ != null) {
            econ.depositPlayer(killer, bountyAmount);
            killer.sendMessage("§b§lBOUNTY CLAIMED! §7You earned " + econ.format(bountyAmount)
                    + " for defeating " + victim.getName() + "!");
        } else {
            // Fallback: If no Vault/Economy plugin, drop physical diamonds instead
            final int maxDiamonds = config.getInt("bounty.max-diamonds", 32);
            final int diamondsToDrop = Math.min((int) (bountyAmount / 50), maxDiamonds);
            if (diamondsToDrop > 0) {
                victim.getWorld().dropItemNaturally(victim.getLocation(), new ItemStack(Material.DIAMOND, diamondsToDrop));
                killer.sendMessage("§b§lBOUNTY CLAIMED! §7You defeated " + victim.getName() + " and earned diamonds!");
            }
        }

        // Reward the hero's reputation for taking down a villain.
        final int heroReward = config.getInt("bounty.hero-points-reward", 10);
        if (heroReward > 0) {
            repManager.addHeroPoints(killer, heroReward);
        }
    }
}
