package com.mha.plugin.quirk;

/**
 * Enumeration of all available Quirks in the plugin.
 * Each Quirk has a rarity that affects its power and chance to obtain.
 */
public enum QuirkType {
    // COMMON Quirks - 40% chance
    NONE("none", "No Quirk", "You have no Quirk ability", QuirkRarity.COMMON),
    POP_OFF("pop-off", "Pop Off", "Eject sticky balls from your head", QuirkRarity.COMMON),
    NAVEL_LASER("navel-laser", "Navel Laser", "Fire a laser beam from your navel", QuirkRarity.COMMON),
    ENGINE("engine", "Engine", "Reciprocating rotary engines for speed", QuirkRarity.COMMON),
    FROG("frog", "Frog", "Frog-like abilities with tongue and mobility", QuirkRarity.COMMON),

    // UNCOMMON Quirks - 30% chance
    EXPLOSION("explosion", "Explosion", "Create powerful explosions", QuirkRarity.UNCOMMON),
    ZERO_GRAVITY("zero-gravity", "Zero Gravity", "Remove gravity from targets", QuirkRarity.UNCOMMON),
    TRANSFORMATION("transformation", "Transform", "Appear as another person temporarily", QuirkRarity.UNCOMMON),

    // RARE Quirks - 18% chance
    ICE_FIRE("ice-fire", "Half-Cold Half-Hot", "Control ice and fire", QuirkRarity.RARE),
    HARDENING("hardening", "Hardening", "Turn body into unbreakable steel", QuirkRarity.RARE),
    ELECTRIFICATION("electrification", "Electrification", "Generate and control electricity", QuirkRarity.RARE),
    WAVE_MOTION("wave-motion", "Wave Motion", "Convert stamina into powerful energy waves", QuirkRarity.RARE),

    // EPIC Quirks - 9% chance
    CREMATION("cremation", "Blueflame", "Generate intensely hot blue flames", QuirkRarity.EPIC),
    CREATION("creation", "Creation", "Create objects from body lipids", QuirkRarity.EPIC),
    PERMEATION("permeation", "Permeation", "Phase through solid matter", QuirkRarity.EPIC),
    BLOODCURDLE("bloodcurdle", "Bloodcurdle", "Paralyze targets by tasting their blood", QuirkRarity.EPIC),

    // LEGENDARY Quirks - 3% chance
    ONE_FOR_ALL("one-for-all", "One For All", "Stockpiled power of destruction", QuirkRarity.LEGENDARY),
    DECAY("decay", "Decay", "Disintegrate whatever you touch", QuirkRarity.LEGENDARY),
    BLACKWHIP("blackwhip", "Blackwhip", "Manifest shadowy tendrils for combat", QuirkRarity.LEGENDARY);

    private final String id;
    private final String displayName;
    private final String description;
    private final QuirkRarity rarity;

    QuirkType(final String id, final String displayName, final String description, final QuirkRarity rarity) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public QuirkRarity getRarity() {
        return rarity;
    }

    /**
     * Get a QuirkType from its string ID.
     */
    public static QuirkType fromId(final String id) {
        if (id == null) {
            return NONE;
        }
        for (final QuirkType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return NONE;
    }

    /**
     * Check if this is a valid playable Quirk.
     */
    public boolean isPlayable() {
        return this != NONE;
    }
}