package com.fabriquefutur.qupa.game

import kotlin.random.Random

object QuestionPicker {
    /**
     * Chooses up to [GameSettings.questions] distinct questions that match the
     * category and difficulty filters. Questions in [excludeIds] (already asked
     * in this session) never come back; questions in [seenIds] (asked in an
     * earlier session) come last, only when the unseen ones run out.
     */
    fun pick(
        bank: List<Question>,
        settings: GameSettings,
        seenIds: Set<String>,
        excludeIds: Set<String>,
        random: Random,
    ): List<Question> {
        val pool = bank.asSequence()
            .filter { settings.categories.isEmpty() || it.categoryId in settings.categories }
            .filter { settings.difficulty == null || it.difficulty == settings.difficulty }
            .filter { it.id !in excludeIds }
            .distinctBy { it.id }
            .toList()
        val (seen, unseen) = pool.partition { it.id in seenIds }
        return (unseen.shuffled(random) + seen.shuffled(random)).take(settings.questions)
    }
}
