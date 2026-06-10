package com.mha.plugin.stamina;

import java.util.UUID;

/**
 * Holds stamina state data for a single player.
 * Thread-safe data container.
 */
public final class StaminaState {

    /**
     * Stamina state enumeration for quick status checks.
     */
    public enum State {
        FULL, NORMAL, LOW, EXHAUSTED
    }

    private final UUID playerId;
    private final int maxStamina;
    private volatile int currentStamina;

    public StaminaState(final UUID playerId, final int maxStamina) {
        this.playerId = playerId;
        this.maxStamina = maxStamina;
        this.currentStamina = maxStamina;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getMaxStamina() {
        return maxStamina;
    }

    public int getCurrentStamina() {
        return currentStamina;
    }

    /**
     * Set stamina to a specific value (clamped to 0-max).
     */
    public void setStamina(final int value) {
        this.currentStamina = Math.max(0, Math.min(maxStamina, value));
    }

    /**
     * Decrease stamina by amount.
     */
    public void decrease(final int amount) {
        this.currentStamina = Math.max(0, currentStamina - amount);
    }

    /**
     * Increase stamina by amount (capped at max).
     */
    public void increase(final int amount) {
        this.currentStamina = Math.min(maxStamina, currentStamina + amount);
    }

    /**
     * Check if stamina is full.
     */
    public boolean isFull() {
        return currentStamina >= maxStamina;
    }

    /**
     * Get stamina as percentage.
     */
    public double getPercent() {
        return (currentStamina * 100.0) / maxStamina;
    }
}
