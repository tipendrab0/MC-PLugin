package com.mha.plugin.qte;

import java.util.Collections;
import java.util.List;

/**
 * Represents a QTE sequence that a player needs to complete.
 */
public final class QTESequence {

    private final List<QTEKey> keys;
    private final long startTime;
    private final long duration;
    private final long endTime;
    private final double successMultiplier;

    public QTESequence(final List<QTEKey> keys, final long startTime, final long duration) {
        this.keys = Collections.unmodifiableList(keys);
        this.startTime = startTime;
        this.duration = duration;
        this.endTime = startTime + duration;
        this.successMultiplier = 2.0;
    }

    public List<QTEKey> getKeys() {
        return keys;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getDuration() {
        return duration;
    }

    public long getEndTime() {
        return endTime;
    }

    /**
     * Get the damage/success multiplier for completing this QTE.
     */
    public double getSuccessMultiplier() {
        return successMultiplier;
    }

    /**
     * Get the key count for this sequence.
     */
    public int getKeyCount() {
        return keys.size();
    }

    /**
     * Check if the sequence has expired.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > endTime;
    }

    /**
     * Get remaining time in milliseconds.
     */
    public long getRemainingTime() {
        return Math.max(0, endTime - System.currentTimeMillis());
    }
}
