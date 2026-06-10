package com.mha.plugin.reputation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReputationRankCalculatorTest {

    private ReputationRankCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ReputationRankCalculator(50, 200, -30, -100);
    }

    @Test
    void neutralAroundZero() {
        assertEquals(ReputationRank.NEUTRAL, calculator.getRank(0));
        assertEquals(ReputationRank.NEUTRAL, calculator.getRank(49));
        assertEquals(ReputationRank.NEUTRAL, calculator.getRank(-29));
    }

    @Test
    void heroThresholds() {
        assertEquals(ReputationRank.HERO, calculator.getRank(50));
        assertEquals(ReputationRank.HERO, calculator.getRank(199));
        assertEquals(ReputationRank.PRO_HERO, calculator.getRank(200));
    }

    @Test
    void villainThresholds() {
        assertEquals(ReputationRank.VILLAIN, calculator.getRank(-30));
        assertEquals(ReputationRank.VILLAIN, calculator.getRank(-99));
        assertEquals(ReputationRank.SUPERVILLAIN, calculator.getRank(-100));
    }

    @Test
    void ultimateAccessRequiresHeroRank() {
        assertFalse(calculator.canUseUltimate(49));
        assertTrue(calculator.canUseUltimate(50));
        assertTrue(calculator.canUseUltimate(200));
        assertFalse(calculator.canUseUltimate(-30));
    }
}
