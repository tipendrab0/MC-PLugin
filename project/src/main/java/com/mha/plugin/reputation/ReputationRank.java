package com.mha.plugin.reputation;

/**
 * Represents a player's moral alignment and rank in society.
 */
public enum ReputationRank {
    UNKNOWN("Unknown", "§7", "", false, false),
    NEUTRAL("Civilian", "§f", "", false, false),
    HERO("Hero", "§b", "§b[Hero]§r ", true, false),
    PRO_HERO("Pro Hero", "§6", "§6[Pro Hero]§r ", true, false),
    VILLAIN("Villain", "§c", "§c[Villain]§r ", false, true),
    SUPERVILLAIN("Supervillain", "§4", "§4[Supervillain]§r ", false, true);

    private final String displayName;
    private final String displayColor;
    private final String chatPrefix;
    private final boolean heroic;
    private final boolean villainous;

    ReputationRank(final String displayName, final String displayColor, final String chatPrefix,
                   final boolean heroic, final boolean villainous) {
        this.displayName = displayName;
        this.displayColor = displayColor;
        this.chatPrefix = chatPrefix;
        this.heroic = heroic;
        this.villainous = villainous;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDisplayColor() {
        return displayColor;
    }

    public String getChatPrefix() {
        return chatPrefix;
    }

    public boolean isHeroic() {
        return heroic;
    }

    public boolean isVillainous() {
        return villainous;
    }

    public boolean isNeutral() {
        return !heroic && !villainous;
    }
}
