package com.mha.plugin.quirk;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuirkRarityTest {

    @Test
    void commonCooldownMultiplierIsFullDuration() {
        assertEquals(100L, QuirkRarity.COMMON.getCooldownMultiplier());
    }

    @Test
    void legendaryHasHighestPowerAndLowestStaminaCost() {
        assertTrue(QuirkRarity.LEGENDARY.getPowerMultiplier() > QuirkRarity.COMMON.getPowerMultiplier());
        assertTrue(QuirkRarity.LEGENDARY.getStaminaMultiplier() < QuirkRarity.COMMON.getStaminaMultiplier());
    }

    @Test
    void rarityWeightsSumToOneHundred() {
        int total = 0;
        for (final QuirkRarity rarity : QuirkRarity.values()) {
            total += rarity.getWeight();
        }
        assertEquals(100, total);
    }

    @Test
    void weightedRandomNeverReturnsNull() {
        final Map<QuirkRarity, Integer> counts = new EnumMap<>(QuirkRarity.class);
        for (int i = 0; i < 1000; i++) {
            final QuirkRarity roll = QuirkRarity.getRandomWeighted();
            assertNotNull(roll);
            counts.merge(roll, 1, Integer::sum);
        }
        assertTrue(counts.containsKey(QuirkRarity.COMMON));
        assertTrue(counts.containsKey(QuirkRarity.LEGENDARY));
    }
}
