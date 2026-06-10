package com.mha.plugin.qte;

/**
 * Represents keys used in QTE sequences.
 */
public enum QTEKey {
    FORWARD("§bW §r", "W"),
    BACKWARD("§bS §r", "S"),
    LEFT("§bA §r", "A"),
    RIGHT("§bD §r", "D"),
    JUMP("§aSPACE §r", "SPACE"),
    SNEAK("§cSHIFT §r", "SHIFT");

    private final String display;
    private final String keyName;

    QTEKey(final String display, final String keyName) {
        this.display = display;
        this.keyName = keyName;
    }

    public String getDisplay() {
        return display;
    }

    public String getKeyName() {
        return keyName;
    }
}
