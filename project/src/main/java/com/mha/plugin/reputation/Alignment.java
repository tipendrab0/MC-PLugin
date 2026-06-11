package com.mha.plugin.reputation;

/**
 * A player's chosen path in the Hero Society.
 *
 * <p>This is distinct from {@link ReputationRank}: the rank is derived from a
 * player's score, while the alignment is an explicit choice the player makes on
 * their first join (Hero vs Villain). The alignment drives the coloured name tag
 * and the bounty system.
 */
public enum Alignment {
    UNDECIDED("undecided", "Undecided", "§7"),
    HERO("hero", "Hero", "§9"),
    VILLAIN("villain", "Villain", "§c");

    private final String id;
    private final String displayName;
    private final String color;

    Alignment(final String id, final String displayName, final String color) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Legacy colour code (e.g. {@code §9}) used for chat/title text.
     */
    public String getColor() {
        return color;
    }

    public boolean isChosen() {
        return this != UNDECIDED;
    }

    /**
     * Resolve an {@link Alignment} from its stored id, falling back to
     * {@link #UNDECIDED} for unknown or {@code null} values.
     */
    public static Alignment fromId(final String id) {
        if (id != null) {
            for (final Alignment alignment : values()) {
                if (alignment.id.equalsIgnoreCase(id)) {
                    return alignment;
                }
            }
        }
        return UNDECIDED;
    }
}
