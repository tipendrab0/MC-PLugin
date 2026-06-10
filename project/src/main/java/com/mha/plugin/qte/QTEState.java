package com.mha.plugin.qte;

/**
 * Tracks player progress through a QTE sequence.
 */
public final class QTEState {

    private final QTESequence sequence;
    private int currentIndex;

    public QTEState(final QTESequence sequence, final int currentIndex) {
        this.sequence = sequence;
        this.currentIndex = currentIndex;
    }

    public QTESequence getSequence() {
        return sequence;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void incrementIndex() {
        currentIndex++;
    }

    /**
     * Check if the QTE has been fully completed.
     */
    public boolean isComplete() {
        return currentIndex >= sequence.getKeyCount();
    }

    /**
     * Check if the QTE is still active and not expired.
     */
    public boolean isActive() {
        return !isComplete() && !sequence.isExpired();
    }
}
