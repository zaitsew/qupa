package com.fabriquefutur.qupa.game

import kotlin.math.roundToInt

object Scoring {
    const val MAX_POINTS = 1000
    const val MIN_CORRECT_POINTS = 500

    /**
     * Phones: 1000 x the share of the answer time that was left, but a correct
     * answer is never worth less than 500. A wrong or missing answer scores 0.
     */
    fun phonePoints(correct: Boolean, timeLeftMs: Long, totalMs: Long): Int {
        if (!correct) return 0
        if (totalMs <= 0) return MIN_CORRECT_POINTS
        val share = (timeLeftMs.toDouble() / totalMs).coerceIn(0.0, 1.0)
        return (MAX_POINTS * share).roundToInt().coerceAtLeast(MIN_CORRECT_POINTS)
    }

    /** Remote mode has no speed bonus: a correct answer is worth the maximum. */
    fun remotePoints(correct: Boolean): Int = if (correct) MAX_POINTS else 0
}
