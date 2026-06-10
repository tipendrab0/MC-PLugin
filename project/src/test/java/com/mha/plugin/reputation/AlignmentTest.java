package com.mha.plugin.reputation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlignmentTest {

    @Test
    void fromIdResolvesKnownValuesCaseInsensitively() {
        assertEquals(Alignment.HERO, Alignment.fromId("hero"));
        assertEquals(Alignment.HERO, Alignment.fromId("HERO"));
        assertEquals(Alignment.VILLAIN, Alignment.fromId("villain"));
        assertEquals(Alignment.UNDECIDED, Alignment.fromId("undecided"));
    }

    @Test
    void fromIdFallsBackToUndecided() {
        assertEquals(Alignment.UNDECIDED, Alignment.fromId(null));
        assertEquals(Alignment.UNDECIDED, Alignment.fromId(""));
        assertEquals(Alignment.UNDECIDED, Alignment.fromId("nonsense"));
    }

    @Test
    void idRoundTrips() {
        for (final Alignment alignment : Alignment.values()) {
            assertEquals(alignment, Alignment.fromId(alignment.getId()));
        }
    }

    @Test
    void isChosenOnlyForHeroAndVillain() {
        assertTrue(Alignment.HERO.isChosen());
        assertTrue(Alignment.VILLAIN.isChosen());
        assertFalse(Alignment.UNDECIDED.isChosen());
    }
}
