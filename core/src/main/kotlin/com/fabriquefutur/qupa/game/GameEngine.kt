package com.fabriquefutur.qupa.game

import java.util.UUID
import kotlin.random.Random

/** One seat in the lobby: a phone player, or a team in remote mode. */
data class ParticipantInfo(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val connected: Boolean,
    val score: Int,
    /** 1 for the leader; equal scores share a rank. */
    val rank: Int,
    /** True once this participant locked an answer for the open question. */
    val answered: Boolean,
    /** What they chose: -1 for nothing. Only meaningful once the question is revealed. */
    val choice: Int,
    /** Points the last question earned. */
    val gained: Int,
)

/** The open question as the TV shows it. Answers are already shuffled. */
data class QuestionInfo(
    /** Zero-based. */
    val index: Int,
    val total: Int,
    val text: String,
    val answers: List<String>,
    val correctIndex: Int,
    val type: QuestionType,
    val categoryId: Int,
    val difficulty: Difficulty,
)

/** An immutable picture of the game. [version] rises with every change. */
data class GameSnapshot(
    val version: Int,
    val mode: Mode,
    val phase: Phase,
    val settings: GameSettings,
    /** In seat order. */
    val participants: List<ParticipantInfo>,
    val question: QuestionInfo?,
    /** Engine-clock time the open question stops taking answers; 0 outside [Phase.QUESTION]. */
    val deadlineMs: Long,
    val answerMs: Long,
    /** Engine-clock time the current pause ends by itself; 0 when it does not. */
    val phaseEndsAtMs: Long,
    /** Remote mode: the team whose turn it is. */
    val activeId: String?,
) {
    fun participant(id: String): ParticipantInfo? = participants.firstOrNull { it.id == id }

    /** Best first. */
    val ranked: List<ParticipantInfo> get() = participants.sortedWith(compareBy({ it.rank }, { participants.indexOf(it) }))
}

sealed interface JoinResult {
    data class Joined(val id: String, val name: String, val colorIndex: Int, val reconnected: Boolean) : JoinResult
    data class Rejected(val reason: JoinRejection) : JoinResult
}

enum class JoinRejection { FULL, BAD_NAME }

enum class StartResult { STARTED, NOT_ENOUGH_PLAYERS, NO_QUESTIONS, WRONG_PHASE }

enum class AnswerResult {
    ACCEPTED, UNKNOWN_PLAYER, NOT_ACCEPTING, STALE, NOT_YOUR_TURN, DUPLICATE, TOO_LATE, BAD_CHOICE,
}

/**
 * The whole game as a state machine, with no Android, no sockets and no
 * threads. Time comes from [clock]; the host calls [tick] often (the TV does so
 * every 100 ms) and every deadline is checked there, so a test advances a fake
 * clock and calls [tick]. Not thread-safe: [GameSession] serializes access.
 */
class GameEngine(
    val mode: Mode,
    private val clock: Clock,
    private val bank: List<Question>,
    private val random: Random = Random.Default,
    private val timings: Timings = Timings(),
    seenIds: Set<String> = emptySet(),
    private val idGenerator: () -> String = { UUID.randomUUID().toString().replace("-", "").take(16) },
) {
    private class Seat(val id: String, var name: String, val colorIndex: Int) {
        var connected = true
        var disconnectedAt = 0L
        var score = 0
        var lastGain = 0
        var lastChoice = -1
    }

    private class Prepared(val question: Question, val answers: List<String>, val correctIndex: Int)
    private class Given(val choice: Int, val pointsIfCorrect: Int)

    private val seats = LinkedHashMap<String, Seat>()
    private val seen: Set<String> = seenIds.toSet()
    private val askedThisSession = LinkedHashSet<String>()

    private var settings = GameSettings()
    private var phase = Phase.LOBBY
    private var prepared: List<Prepared> = emptyList()
    private var index = -1
    private var deadlineMs = 0L
    private var answerMs = 0L
    private var phaseEndsAtMs = 0L
    private val given = LinkedHashMap<String, Given>()
    private var version = 0

    val currentPhase: Phase get() = phase

    /** Ids of every question asked since this engine was created. */
    fun askedIds(): Set<String> = askedThisSession.toSet()

    private val capacity get() = if (mode == Mode.PHONES) Limits.MAX_PLAYERS else Limits.MAX_TEAMS

    // ---- seats ----

    /**
     * Seats a new participant, or re-seats a returning one when [existingId] is
     * known. A new name that is already taken gets a number ("Ana 2").
     */
    fun join(rawName: String?, existingId: String? = null): JoinResult {
        val returning = existingId?.let { seats[it] }
        if (returning != null) {
            returning.connected = true
            version++
            return JoinResult.Joined(returning.id, returning.name, returning.colorIndex, reconnected = true)
        }
        val name = Names.clean(rawName) ?: return JoinResult.Rejected(JoinRejection.BAD_NAME)
        if (seats.size >= capacity) return JoinResult.Rejected(JoinRejection.FULL)
        val seat = Seat(newId(), uniqueName(name), freeColor())
        seats[seat.id] = seat
        version++
        return JoinResult.Joined(seat.id, seat.name, seat.colorIndex, reconnected = false)
    }

    /** Remote mode and the lobby: a seat is removed outright. Ignored once a game runs. */
    fun remove(id: String): Boolean {
        if (phase != Phase.LOBBY) return false
        val removed = seats.remove(id) != null
        if (removed) version++
        return removed
    }

    fun rename(id: String, rawName: String?): Boolean {
        val seat = seats[id] ?: return false
        val name = Names.clean(rawName) ?: return false
        seat.name = uniqueName(name, except = id)
        version++
        return true
    }

    /** A socket closed or came back. A game keeps the seat; a lobby frees it after a grace period. */
    fun setConnected(id: String, connected: Boolean) {
        val seat = seats[id] ?: return
        if (seat.connected == connected) return
        seat.connected = connected
        if (!connected) seat.disconnectedAt = clock.nowMs()
        version++
        if (!connected) endEarlyIfEveryoneAnswered()
    }

    private fun newId(): String {
        var id = idGenerator()
        while (id in seats) id = idGenerator()
        return id
    }

    private fun uniqueName(name: String, except: String? = null): String {
        val taken = seats.values.filter { it.id != except }.map { it.name.lowercase() }.toSet()
        if (name.lowercase() !in taken) return name
        var n = 2
        while (true) {
            val suffix = " $n"
            val candidate = Names.truncate(name, Limits.MAX_NAME - suffix.length).trimEnd() + suffix
            if (candidate.lowercase() !in taken) return candidate
            n++
        }
    }

    private fun freeColor(): Int {
        val used = seats.values.map { it.colorIndex }.toSet()
        return (0 until PlayerColors.HEX.size).firstOrNull { it !in used } ?: seats.size
    }

    // ---- settings and flow ----

    fun updateSettings(new: GameSettings) {
        if (phase != Phase.LOBBY) return
        val normalized = new.normalized()
        if (normalized == settings) return
        settings = normalized
        version++
    }

    fun start(): StartResult {
        if (phase != Phase.LOBBY) return StartResult.WRONG_PHASE
        val enough = when (mode) {
            Mode.PHONES -> seats.values.any { it.connected }
            Mode.TEAMS -> seats.size >= Limits.MIN_TEAMS
        }
        if (!enough) return StartResult.NOT_ENOUGH_PLAYERS
        val picked = QuestionPicker.pick(bank, settings, seen, askedThisSession, random)
        if (picked.isEmpty()) return StartResult.NO_QUESTIONS
        prepared = picked.map { prepare(it) }
        seats.values.forEach { it.score = 0; it.lastGain = 0; it.lastChoice = -1 }
        beginQuestion(0)
        return StartResult.STARTED
    }

    private fun prepare(q: Question): Prepared = when (q.type) {
        QuestionType.BOOLEAN -> {
            val answers = listOf("True", "False")
            Prepared(q, answers, if (q.correct.equals("True", ignoreCase = true)) 0 else 1)
        }
        QuestionType.MULTIPLE -> {
            val answers = (q.incorrect + q.correct).shuffled(random)
            Prepared(q, answers, answers.indexOf(q.correct))
        }
    }

    private fun beginQuestion(i: Int) {
        index = i
        given.clear()
        answerMs = settings.answerSeconds * 1000L
        deadlineMs = clock.nowMs() + answerMs
        phaseEndsAtMs = 0
        phase = Phase.QUESTION
        askedThisSession += prepared[i].question.id
        version++
    }

    /** Remote mode: question i belongs to team i mod n, in seat order. */
    private fun activeSeat(): Seat? {
        if (mode != Mode.TEAMS || phase == Phase.LOBBY || index < 0) return null
        val order = seats.values.toList()
        if (order.isEmpty()) return null
        return order[index % order.size]
    }

    fun submitAnswer(playerId: String, questionIndex: Int, choice: Int): AnswerResult {
        if (playerId !in seats) return AnswerResult.UNKNOWN_PLAYER
        if (phase != Phase.QUESTION) return AnswerResult.NOT_ACCEPTING
        if (questionIndex != index) return AnswerResult.STALE
        if (mode == Mode.TEAMS && activeSeat()?.id != playerId) return AnswerResult.NOT_YOUR_TURN
        if (playerId in given) return AnswerResult.DUPLICATE
        val now = clock.nowMs()
        if (now >= deadlineMs) return AnswerResult.TOO_LATE
        val round = prepared[index]
        if (choice !in round.answers.indices) return AnswerResult.BAD_CHOICE
        val correct = choice == round.correctIndex
        val points = when (mode) {
            Mode.PHONES -> Scoring.phonePoints(correct, deadlineMs - now, answerMs)
            Mode.TEAMS -> Scoring.remotePoints(correct)
        }
        given[playerId] = Given(choice, points)
        version++
        endEarlyIfEveryoneAnswered()
        return AnswerResult.ACCEPTED
    }

    private fun endEarlyIfEveryoneAnswered() {
        if (phase != Phase.QUESTION) return
        val done = when (mode) {
            Mode.TEAMS -> activeSeat()?.id.let { it != null && it in given }
            Mode.PHONES -> {
                val live = seats.values.filter { it.connected }
                live.isNotEmpty() && live.all { it.id in given }
            }
        }
        if (done) endQuestion()
    }

    private fun endQuestion() {
        val round = prepared[index]
        for (seat in seats.values) {
            val g = given[seat.id]
            val gain = if (g != null && g.choice == round.correctIndex) g.pointsIfCorrect else 0
            seat.lastGain = gain
            seat.lastChoice = g?.choice ?: -1
            seat.score += gain
        }
        deadlineMs = 0
        phase = Phase.REVEAL
        phaseEndsAtMs = clock.nowMs() + timings.revealMs
        version++
    }

    private fun afterReveal() {
        if (index >= prepared.lastIndex) {
            phase = Phase.PODIUM
            phaseEndsAtMs = 0
        } else {
            phase = Phase.SCOREBOARD
            phaseEndsAtMs = clock.nowMs() + timings.scoreboardMs
        }
        version++
    }

    /** Checks every deadline against the clock. Call it about ten times a second. */
    fun tick() {
        val now = clock.nowMs()
        when (phase) {
            Phase.QUESTION -> if (now >= deadlineMs) endQuestion()
            Phase.REVEAL -> if (now >= phaseEndsAtMs) afterReveal()
            Phase.SCOREBOARD -> if (now >= phaseEndsAtMs) beginQuestion(index + 1)
            Phase.LOBBY -> pruneAbsentSeats(now)
            Phase.PODIUM -> Unit
        }
    }

    private fun pruneAbsentSeats(now: Long) {
        if (mode != Mode.PHONES) return
        val gone = seats.values.filter { !it.connected && now - it.disconnectedAt >= timings.lobbyGraceMs }
        if (gone.isEmpty()) return
        gone.forEach { seats.remove(it.id) }
        version++
    }

    /** The host pressed OK: skip the wait, or close the question now. */
    fun advance() {
        when (phase) {
            Phase.QUESTION -> endQuestion()
            Phase.REVEAL -> afterReveal()
            Phase.SCOREBOARD -> beginQuestion(index + 1)
            Phase.LOBBY, Phase.PODIUM -> Unit
        }
    }

    /** Back to the lobby with the same seats, scores cleared and questions not repeated. */
    fun playAgain() {
        if (phase != Phase.PODIUM) return
        seats.values.filter { !it.connected }.forEach { seats.remove(it.id) }
        seats.values.forEach { it.score = 0; it.lastGain = 0; it.lastChoice = -1 }
        prepared = emptyList()
        index = -1
        given.clear()
        deadlineMs = 0
        phaseEndsAtMs = 0
        phase = Phase.LOBBY
        version++
    }

    // ---- reading ----

    fun snapshot(): GameSnapshot {
        val ranks = rankOf()
        val reveal = phase == Phase.REVEAL || phase == Phase.SCOREBOARD || phase == Phase.PODIUM
        val infos = seats.values.map { s ->
            ParticipantInfo(
                id = s.id,
                name = s.name,
                colorIndex = s.colorIndex,
                connected = s.connected,
                score = s.score,
                rank = ranks.getValue(s.id),
                answered = s.id in given,
                choice = if (reveal) s.lastChoice else -1,
                gained = if (reveal) s.lastGain else 0,
            )
        }
        val question = prepared.getOrNull(index)?.takeIf { phase != Phase.LOBBY }?.let {
            QuestionInfo(
                index = index,
                total = prepared.size,
                text = it.question.text,
                answers = it.answers,
                correctIndex = it.correctIndex,
                type = it.question.type,
                categoryId = it.question.categoryId,
                difficulty = it.question.difficulty,
            )
        }
        return GameSnapshot(
            version = version,
            mode = mode,
            phase = phase,
            settings = settings,
            participants = infos,
            question = question,
            deadlineMs = deadlineMs,
            answerMs = answerMs,
            phaseEndsAtMs = phaseEndsAtMs,
            activeId = activeSeat()?.id,
        )
    }

    val stateVersion: Int get() = version

    /** Competition ranking: 1, 2, 2, 4. */
    private fun rankOf(): Map<String, Int> {
        val sorted = seats.values.sortedByDescending { it.score }
        val ranks = HashMap<String, Int>()
        var rank = 0
        var previous = Int.MIN_VALUE
        sorted.forEachIndexed { i, s ->
            if (s.score != previous) { rank = i + 1; previous = s.score }
            ranks[s.id] = rank
        }
        return ranks
    }
}
