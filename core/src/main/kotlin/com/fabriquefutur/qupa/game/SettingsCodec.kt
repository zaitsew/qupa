package com.fabriquefutur.qupa.game

/** Text forms of [GameSettings] fields, for DataStore. Anything unreadable falls back to a default. */
object SettingsCodec {
    fun encodeCategories(ids: Set<Int>): String = ids.sorted().joinToString(",")

    fun decodeCategories(text: String?): Set<Int> =
        text.orEmpty().split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { Categories.isKnown(it) }
            .toSet()

    fun encodeDifficulty(d: Difficulty?): String = d?.name?.lowercase() ?: "any"

    fun decodeDifficulty(text: String?): Difficulty? = Difficulty.parse(text)
}
