package com.mha.plugin.quirk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CooldownMathTest {

    @Test
    void commonRarityUsesFullConfiguredCooldown() {
        final int configCooldown = 2000;
        final int scaled = (int) (configCooldown * QuirkRarity.COMMON.getCooldownMultiplier() / 100L);
        assertEquals(2000, scaled);
    }

    @Test
    void legendaryRarityReducesCooldown() {
        final int configCooldown = 2000;
        final int scaled = (int) (configCooldown * QuirkRarity.LEGENDARY.getCooldownMultiplier() / 100L);
        assertEquals(1000, scaled);
    }

    @Test
    void epicRarityScalesStaminaCost() {
        final int baseCost = 20;
        final int scaled = (int) (baseCost * QuirkRarity.EPIC.getStaminaMultiplier());
        assertEquals(15, scaled);
    }
}
