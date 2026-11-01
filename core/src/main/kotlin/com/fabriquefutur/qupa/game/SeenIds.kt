package com.fabriquefutur.qupa.game

/** The ids of questions asked in earlier sessions, kept so new games prefer fresh ones. */
object SeenIds {
    /** More than the bank holds, so a full bank resets by itself instead of growing forever. */
    const val CAP = 8_000

    /**
     * Adds [fresh] to [old]. When the total would pass [cap] only the fresh ids
     * and the most recent old ones survive; a plain set has no order, so the
     * old ones are cut arbitrarily, which is fine: the point is only to prefer
     * unseen questions.
     */
    fun merge(old: Set<String>, fresh: Set<String>, cap: Int = CAP): Set<String> {
        val all = LinkedHashSet<String>(old)
        all.addAll(fresh)
        if (all.size <= cap) return all
        val keepOld = (cap - fresh.size).coerceAtLeast(0)
        return LinkedHashSet<String>().apply {
            addAll(old.filter { it !in fresh }.take(keepOld))
            addAll(fresh.take(cap))
        }
    }
}
