package com.fabriquefutur.qupa.game

import com.fabriquefutur.qupa.sound.Effect
import com.fabriquefutur.qupa.sound.Note
import com.fabriquefutur.qupa.sound.Synth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SynthAndSeenTest {
    @Test fun everyEffectRendersAudibleSamplesOfTheRightLength() {
        for (effect in Effect.entries) {
            val pcm = Synth.render(effect)
            assertEquals(Synth.notes(effect).sumOf { Synth.SAMPLE_RATE * it.ms / 1000 }, pcm.size)
            assertTrue("$effect is silent", pcm.any { abs(it.toInt()) > 2_000 })
            assertTrue("$effect clips", pcm.all { abs(it.toInt()) < Short.MAX_VALUE })
        }
    }

    @Test fun anEffectStartsAndEndsAtSilenceSoItDoesNotClick() {
        val pcm = Synth.render(Effect.CORRECT)
        assertEquals(0, pcm.first().toInt())
        assertTrue(abs(pcm.last().toInt()) < 800)
    }

    @Test fun aRestIsSilent() {
        assertTrue(Synth.render(listOf(Note(0.0, 50))).all { it.toInt() == 0 })
    }

    @Test fun mergingKeepsOldAndFreshIds() {
        assertEquals(setOf("a", "b", "c"), SeenIds.merge(setOf("a", "b"), setOf("b", "c")))
    }

    @Test fun mergingNeverExceedsTheCapAndKeepsTheFreshIds() {
        val old = (0 until 10).map { "o$it" }.toSet()
        val fresh = setOf("f1", "f2", "f3")
        val merged = SeenIds.merge(old, fresh, cap = 6)
        assertEquals(6, merged.size)
        assertTrue(merged.containsAll(fresh))
    }
}
