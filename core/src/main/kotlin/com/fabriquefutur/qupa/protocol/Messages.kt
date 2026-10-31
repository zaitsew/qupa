package com.fabriquefutur.qupa.protocol

import com.fabriquefutur.qupa.game.GameSnapshot
import com.fabriquefutur.qupa.game.Phase
import com.fabriquefutur.qupa.game.PlayerColors
import com.fabriquefutur.qupa.game.QuestionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON on the wire; the message type travels in the `t` field. */
val WireJson = Json {
    classDiscriminator = "t"
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Phone to TV. */
@Serializable
sealed interface ClientMessage

@Serializable
@SerialName("join")
data class Join(val name: String, val playerId: String? = null) : ClientMessage

/** [q] is the question number the phone is looking at, counted from 1. */
@Serializable
@SerialName("answer")
data class Answer(val q: Int, val choice: Int) : ClientMessage

@Serializable
@SerialName("ping")
data class Ping(val n: Int = 0) : ClientMessage

/** TV to phone. */
@Serializable
sealed interface ServerMessage

@Serializable
@SerialName("welcome")
data class Welcome(val playerId: String, val name: String, val color: String) : ServerMessage

/** [reason] is one of `full`, `name`, `closed`. */
@Serializable
@SerialName("reject")
data class Reject(val reason: String) : ServerMessage

@Serializable
@SerialName("pong")
data class Pong(val n: Int = 0) : ServerMessage

/** The answer to one [Answer]: `ok`, or a reason such as `late`, `duplicate`, `closed`. */
@Serializable
@SerialName("ack")
data class Ack(val q: Int, val ok: Boolean, val reason: String? = null) : ServerMessage

/**
 * Everything a phone needs to draw its screen, for that player. The correct
 * answer is -1 until the question is revealed.
 */
@Serializable
@SerialName("view")
data class View(
    /** `lobby`, `question`, `reveal`, `scoreboard` or `podium`. */
    val phase: String,
    /** Question number from 1; 0 in the lobby. */
    val q: Int,
    val total: Int,
    /** `mc` for four answers, `tf` for true/false, empty in the lobby. */
    val kind: String,
    val choices: Int,
    /** Time left to answer, measured when this message was built. */
    val remainingMs: Long,
    val totalMs: Long,
    val answered: Boolean,
    /** The phone's own choice, -1 for none. Shown after reveal. */
    val chosen: Int,
    val correct: Int,
    val gained: Int,
    val score: Int,
    val rank: Int,
    val players: Int,
    val name: String,
    val color: String,
) : ServerMessage

object PhoneViews {
    /** Builds [playerId]'s view of [snap]; null when that player has no seat. */
    fun build(snap: GameSnapshot, playerId: String, nowMs: Long): View? {
        val me = snap.participant(playerId) ?: return null
        val q = snap.question
        val revealed = snap.phase == Phase.REVEAL || snap.phase == Phase.SCOREBOARD || snap.phase == Phase.PODIUM
        return View(
            phase = snap.phase.name.lowercase(),
            q = q?.let { it.index + 1 } ?: 0,
            total = q?.total ?: 0,
            kind = when (q?.type) {
                QuestionType.BOOLEAN -> "tf"
                QuestionType.MULTIPLE -> "mc"
                null -> ""
            },
            choices = q?.answers?.size ?: 0,
            remainingMs = if (snap.phase == Phase.QUESTION) (snap.deadlineMs - nowMs).coerceIn(0, snap.answerMs) else 0,
            totalMs = if (snap.phase == Phase.QUESTION) snap.answerMs else 0,
            answered = me.answered,
            chosen = if (revealed) me.choice else -1,
            correct = if (revealed && q != null) q.correctIndex else -1,
            gained = if (revealed) me.gained else 0,
            score = me.score,
            rank = me.rank,
            players = snap.participants.size,
            name = me.name,
            color = PlayerColors.hex(me.colorIndex),
        )
    }
}
