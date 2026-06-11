package com.mha.plugin.bounty;

import com.mha.plugin.reputation.ReputationManager;
import com.mha.plugin.reputation.ReputationRank;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active bounties on villains.
 * Heroes can claim bounties by defeating villains.
 */
public final class BountyManager {

    private final ReputationManager repManager;
    private final Map<UUID, BountyInfo> activeBounties;
    private final Map<UUID, List<BountyRecord>> bountyHistory;

    public BountyManager(final ReputationManager repManager) {
        this.repManager = repManager;
        this.activeBounties = new ConcurrentHashMap<>();
        this.bountyHistory = new ConcurrentHashMap<>();
    }

    /**
     * Update bounties for all online players.
     */
    public void updateBounties() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            final int score = repManager.getReputationScore(player);
            final ReputationRank rank = repManager.getRank(player);

            // Villains have active bounties
            if (rank == ReputationRank.VILLAIN || rank == ReputationRank.SUPERVILLAIN) {
                final double bounty = calculateBounty(score);
                activeBounties.put(player.getUniqueId(), new BountyInfo(
                        player.getUniqueId(),
                        player.getName(),
                        score,
                        bounty,
                        System.currentTimeMillis()
                ));
            } else {
                activeBounties.remove(player.getUniqueId());
            }
        }
    }

    /**
     * Calculate bounty value based on villain score.
     */
    private double calculateBounty(final int villainScore) {
        // More negative = higher bounty
        return Math.min(Math.abs(villainScore) * 10.0, 10000.0);
    }

    /**
     * Get all active bounties sorted by value (highest first).
     */
    public List<BountyInfo> getActiveBounties() {
        updateBounties();
        final List<BountyInfo> bounties = new ArrayList<>(activeBounties.values());
        bounties.sort((a, b) -> Double.compare(b.bounty(), a.bounty()));
        return bounties;
    }

    /**
     * Get bounty info for a specific player.
     */
    public BountyInfo getBounty(final UUID playerId) {
        return activeBounties.get(playerId);
    }

    /**
     * Record a bounty claim.
     */
    public void recordClaim(final UUID hunterId, final UUID villainId, final double amount) {
        bountyHistory.computeIfAbsent(hunterId, k -> new ArrayList<>())
                .add(new BountyRecord(villainId, amount, System.currentTimeMillis()));
    }

    /**
     * Get total bounty earnings for a player.
     */
    public double getTotalEarnings(final UUID hunterId) {
        final List<BountyRecord> records = bountyHistory.get(hunterId);
        if (records == null) return 0;
        return records.stream().mapToDouble(BountyRecord::amount).sum();
    }

    /**
     * Get total bounty claims for a player.
     */
    public int getTotalClaims(final UUID hunterId) {
        final List<BountyRecord> records = bountyHistory.get(hunterId);
        return records == null ? 0 : records.size();
    }

    /**
     * Get top bounty hunters by total earnings.
     */
    public List<Map.Entry<UUID, Double>> getTopHunters(final int limit) {
        final Map<UUID, Double> totals = new HashMap<>();
        bountyHistory.forEach((hunterId, records) ->
                totals.put(hunterId, records.stream().mapToDouble(BountyRecord::amount).sum()));

        final List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    /**
     * Bounty information.
     */
    public record BountyInfo(UUID playerId, String name, int villainScore, double bounty, long timestamp) {}

    /**
     * Bounty claim record.
     */
    private record BountyRecord(UUID villainId, double amount, long timestamp) {}
}
