package com.fabriquefutur.qupa.sound

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

enum class Effect { TICK, CORRECT, WRONG }

/** One steady note: [hz] for [ms] at [volume] 0..1. A rest has [hz] = 0. */
data class Note(val hz: Double, val ms: Int, val volume: Double = 0.6)

/**
 * The three sound effects, made of sine waves at run time: no audio files ship
 * in the app. The android layer plays the samples with AudioTrack.
 */
object Synth {
    const val SAMPLE_RATE = 22_050
    private const val EDGE_MS = 6

    fun notes(effect: Effect): List<Note> = when (effect) {
        Effect.TICK -> listOf(Note(1_100.0, 35, 0.35))
        Effect.CORRECT -> listOf(Note(659.3, 90), Note(880.0, 170))
        Effect.WRONG -> listOf(Note(196.0, 150, 0.7), Note(147.0, 260, 0.7))
    }

    fun durationMs(effect: Effect): Int = notes(effect).sumOf { it.ms }

    /** 16-bit mono PCM. Each note fades in and out over 6 ms so it does not click. */
    fun render(effect: Effect): ShortArray = render(notes(effect))

    fun render(notes: List<Note>): ShortArray {
        val out = ArrayList<Short>()
        for (note in notes) {
            val n = SAMPLE_RATE * note.ms / 1000
            val edge = (SAMPLE_RATE * EDGE_MS / 1000).coerceAtLeast(1)
            for (i in 0 until n) {
                val envelope = min(1.0, min(i, n - 1 - i).toDouble() / edge)
                val wave = if (note.hz <= 0) 0.0 else sin(2 * PI * note.hz * i / SAMPLE_RATE)
                out += (wave * envelope * note.volume * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out.toShortArray()
    }
}
