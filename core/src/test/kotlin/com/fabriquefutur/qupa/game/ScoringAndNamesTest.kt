package com.fabriquefutur.qupa.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoringAndNamesTest {
    @Test fun instantCorrectAnswerIsWorth1000() =
        assertEquals(1000, Scoring.phonePoints(true, timeLeftMs = 15_000, totalMs = 15_000))

    @Test fun halfTimeLeftIsWorth500() =
        assertEquals(500, Scoring.phonePoints(true, 7_500, 15_000))

    @Test fun aCorrectAnswerNeverDropsBelow500() {
        assertEquals(500, Scoring.phonePoints(true, 1, 15_000))
        assertEquals(500, Scoring.phonePoints(true, 0, 15_000))
        assertEquals(500, Scoring.phonePoints(true, -50, 15_000))
    }

    @Test fun threeQuartersTimeLeftIs750() = assertEquals(750, Scoring.phonePoints(true, 7_500, 10_000))

    @Test fun wrongAnswerScoresNothing() = assertEquals(0, Scoring.phonePoints(false, 15_000, 15_000))

    @Test fun timeLeftAboveTotalIsCapped() = assertEquals(1000, Scoring.phonePoints(true, 99_000, 15_000))

    @Test fun remoteModeHasNoSpeedBonus() {
        assertEquals(1000, Scoring.remotePoints(true))
        assertEquals(0, Scoring.remotePoints(false))
    }

    @Test fun namesAreTrimmedAndCollapsed() = assertEquals("Ana Maria", Names.clean("   Ana \n  Maria  "))

    @Test fun namesAreCutAtSixteenCharacters() = assertEquals("abcdefghijklmnop", Names.clean("abcdefghijklmnopqrstuvwxyz"))

    @Test fun cuttingNeverSplitsAnEmoji() {
        val name = Names.clean("a".repeat(15) + "😀😀")!!
        assertEquals(16, name.codePointCount(0, name.length))
        assertEquals("a".repeat(15) + "😀", name)
    }

    @Test fun blankAndControlOnlyNamesAreRefused() {
        assertNull(Names.clean("   "))
        assertNull(Names.clean("\u0000\u0007"))
        assertNull(Names.clean(null))
    }

    @Test fun directionOverridesAreStripped() = assertEquals("Eve", Names.clean("‮Eve"))
}
