package com.mha.plugin.reputation;

/**
 * Pure reputation rank logic (testable without Bukkit).
 */
public final class ReputationRankCalculator {

    private final int heroThreshold;
    private final int proHeroThreshold;
    private final int villainThreshold;
    private final int supervillainThreshold;

    public ReputationRankCalculator(final int heroThreshold, final int proHeroThreshold,
                                    final int villainThreshold, final int supervillainThreshold) {
        this.heroThreshold = heroThreshold;
        this.proHeroThreshold = proHeroThreshold;
        this.villainThreshold = villainThreshold;
        this.supervillainThreshold = supervillainThreshold;
    }

    public ReputationRank getRank(final int score) {
        if (score >= proHeroThreshold) {
            return ReputationRank.PRO_HERO;
        }
        if (score >= heroThreshold) {
            return ReputationRank.HERO;
        }
        if (score <= supervillainThreshold) {
            return ReputationRank.SUPERVILLAIN;
        }
        if (score <= villainThreshold) {
            return ReputationRank.VILLAIN;
        }
        return ReputationRank.NEUTRAL;
    }

    public boolean canUseUltimate(final int score) {
        final ReputationRank rank = getRank(score);
        return rank == ReputationRank.HERO || rank == ReputationRank.PRO_HERO;
    }
}
