package com.mha.plugin.reputation;

import java.util.UUID;

/**
 * Holds reputation data for a single player.
 */
public final class ReputationState {

    private final UUID playerId;
    private int heroPoints;
    private int villainPoints;
    private volatile boolean dirty;
    private ReputationRank lastRank;
    private Alignment alignment;

    public ReputationState(final UUID playerId) {
        this.playerId = playerId;
        this.heroPoints = 0;
        this.villainPoints = 0;
        this.dirty = false;
        this.lastRank = ReputationRank.UNKNOWN;
        this.alignment = Alignment.UNDECIDED;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getHeroPoints() {
        return heroPoints;
    }

    public void setHeroPoints(final int heroPoints) {
        this.heroPoints = heroPoints;
        this.dirty = true;
    }

    public void addHeroPoints(final int points) {
        this.heroPoints += points;
        this.dirty = true;
    }

    public int getVillainPoints() {
        return villainPoints;
    }

    public void setVillainPoints(final int villainPoints) {
        this.villainPoints = villainPoints;
        this.dirty = true;
    }

    public void addVillainPoints(final int points) {
        this.villainPoints += points;
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(final boolean dirty) {
        this.dirty = dirty;
    }

    /**
     * Get total reputation score (Hero - Villain).
     */
    public int getTotalScore() {
        return heroPoints - villainPoints;
    }

    public ReputationRank getLastRank() {
        return lastRank;
    }

    public void setLastRank(final ReputationRank rank) {
        this.lastRank = rank;
    }

    public Alignment getAlignment() {
        return alignment;
    }

    public void setAlignment(final Alignment alignment) {
        this.alignment = alignment == null ? Alignment.UNDECIDED : alignment;
        this.dirty = true;
    }
}
