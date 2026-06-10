package com.mha.plugin.quirk;

/**
 * Rarity levels for Quirks.
 * Higher rarity = stronger abilities but lower chance to obtain.
 */
public enum QuirkRarity {
    COMMON("§fCommon", "A standard Quirk with basic abilities", 40),      // 40% chance
    UNCOMMON("§aUncommon", "A slightly above-average Quirk", 30),         // 30% chance
    RARE("§bRare", "A powerful Quirk with advanced abilities", 18),         // 18% chance
    EPIC("§dEpic", "An exceptional Quirk with devastating power", 9),       // 9% chance
    LEGENDARY("§6Legendary", "A rare and immensely powerful Quirk", 3);     // 3% chance

    private final String displayName;
    private final String description;
    private final int weight; // Percentage weight for random selection

    QuirkRarity(final String displayName, final String description, final int weight) {
        this.displayName = displayName;
        this.description = description;
        this.weight = weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getWeight() {
        return weight;
    }

    /**
     * Get power multiplier for this rarity.
     */
    public double getPowerMultiplier() {
        return switch (this) {
            case COMMON -> 1.0;
            case UNCOMMON -> 1.2;
            case RARE -> 1.5;
            case EPIC -> 2.0;
            case LEGENDARY -> 3.0;
        };
    }

    /**
     * Get stamina cost reduction for this rarity.
     */
    public double getStaminaMultiplier() {
        return switch (this) {
            case COMMON -> 1.0;
            case UNCOMMON -> 0.95;
            case RARE -> 0.85;
            case EPIC -> 0.75;
            case LEGENDARY -> 0.6;
        };
    }

    /**
     * Get cooldown reduction for this rarity.
     */
    public long getCooldownMultiplier() {
        return switch (this) {
            case COMMON -> 100L;
            case UNCOMMON -> 95;
            case RARE -> 85;
            case EPIC -> 75;
            case LEGENDARY -> 50;
        };
    }

    /**
     * Get a random rarity weighted by their chances.
     */
    public static QuirkRarity getRandomWeighted() {
        final int roll = java.util.concurrent.ThreadLocalRandom.current().nextInt(100);
        int cumulative = 0;

        for (final QuirkRarity rarity : values()) {
            cumulative += rarity.getWeight();
            if (roll < cumulative) {
                return rarity;
            }
        }

        return COMMON;
    }
}
