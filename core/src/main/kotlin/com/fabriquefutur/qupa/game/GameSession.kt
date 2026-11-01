package com.fabriquefutur.qupa.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A seat granted to a phone, with the token of the socket that owns it. */
data class Seated(val id: String, val name: String, val colorIndex: Int, val token: Int, val reconnected: Boolean)

sealed interface JoinOutcome {
    data class Ok(val seated: Seated) : JoinOutcome
    data class No(val reason: JoinRejection) : JoinOutcome
}

/**
 * The thread-safe owner of one [GameEngine]. The TV UI, the web server's
 * sockets and a ticker coroutine all call it from different threads; every call
 * takes one lock, runs the engine, and publishes a new [snapshots] value when
 * the engine's version changed.
 */
class GameSession(
    mode: Mode,
    val clock: Clock,
    bank: List<Question>,
    scope: CoroutineScope,
    val roomCode: String,
    seenIds: Set<String> = emptySet(),
    timings: Timings = Timings(),
    random: kotlin.random.Random = kotlin.random.Random.Default,
    tickMs: Long = 100,
) {
    private val lock = Any()
    private val engine = GameEngine(mode, clock, bank, random, timings, seenIds)
    private val tokens = HashMap<String, Int>()
    private var nextToken = 1
    private var published = -1

    private val _snapshots = MutableStateFlow(engine.snapshot())
    val snapshots: StateFlow<GameSnapshot> = _snapshots

    init {
        scope.launch {
            while (isActive) {
                delay(tickMs)
                locked { engine.tick() }
            }
        }
    }

    private inline fun <T> locked(block: () -> T): T = synchronized(lock) {
        val result = block()
        if (engine.stateVersion != published) {
            published = engine.stateVersion
            _snapshots.value = engine.snapshot()
        }
        result
    }

    // ---- phones ----

    fun join(name: String?, playerId: String?): JoinOutcome = locked {
        when (val r = engine.join(name, playerId)) {
            is JoinResult.Rejected -> JoinOutcome.No(r.reason)
            is JoinResult.Joined -> {
                val token = nextToken++
                tokens[r.id] = token
                JoinOutcome.Ok(Seated(r.id, r.name, r.colorIndex, token, r.reconnected))
            }
        }
    }

    /** Ignored when a newer socket already took the seat over. */
    fun disconnect(playerId: String, token: Int) = locked {
        if (tokens[playerId] == token) {
            tokens.remove(playerId)
            engine.setConnected(playerId, false)
        }
    }

    /** Only the socket that holds the seat can answer: a tab that was taken over is refused. */
    fun answer(playerId: String, token: Int, questionNumber: Int, choice: Int): AnswerResult = locked {
        if (tokens[playerId] != token) return@locked AnswerResult.UNKNOWN_PLAYER
        // Phones count questions from 1; the engine from 0.
        engine.submitAnswer(playerId, questionNumber - 1, choice)
    }

    // ---- the TV ----

    fun addTeam(name: String): JoinResult = locked { engine.join(name) }
    fun removeSeat(id: String) = locked { engine.remove(id) }
    fun rename(id: String, name: String) = locked { engine.rename(id, name) }
    fun updateSettings(settings: GameSettings) = locked { engine.updateSettings(settings) }
    fun start(): StartResult = locked { engine.start() }
    fun advance() = locked { engine.advance() }
    fun playAgain() = locked { engine.playAgain() }
    fun answerFromTv(teamId: String, choice: Int): AnswerResult = locked {
        engine.submitAnswer(teamId, engine.snapshot().question?.index ?: -1, choice)
    }

    fun askedIds(): Set<String> = locked { engine.askedIds() }
}
