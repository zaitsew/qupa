package com.fabriquefutur.qupa

import com.fabriquefutur.qupa.bank.QuestionBank
import com.fabriquefutur.qupa.game.AnswerResult
import com.fabriquefutur.qupa.game.GameSession
import com.fabriquefutur.qupa.game.JoinOutcome
import com.fabriquefutur.qupa.game.Mode
import com.fabriquefutur.qupa.game.Phase
import com.fabriquefutur.qupa.game.Clock
import com.fabriquefutur.qupa.server.QuizServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

/**
 * Runs the real server and the real phone page on a laptop, with no TV:
 *
 *     ./gradlew :core:devServer           # then open the printed URL in a browser
 *
 * It adds two bots, starts a game as soon as a real phone has joined, and starts
 * another when a game ends. The room code is fixed at 1234.
 */
fun main() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val bankFile = File("../app/src/main/assets/questions/en.json")
    val bank = if (bankFile.exists()) QuestionBank.parse(bankFile.readText()).questions else testBank(200)
    val session = GameSession(Mode.PHONES, Clock.System, bank, scope, roomCode = "1234")
    val web = File("../app/src/main/assets/web")
    val server = QuizServer(session) { name -> File(web, name).takeIf { it.isFile }?.readBytes() }
    val port = server.start(8080)
    println("Qupa dev server: http://localhost:$port/?r=1234  (${bank.size} questions)")

    val bots = listOf("Bot Ana", "Bot Bo").map { (session.join(it, null) as JoinOutcome.Ok).seated }
    val random = Random(7)
    scope.launch {
        var lastQuestion = -1
        while (true) {
            delay(250)
            val snap = session.snapshots.value
            val humans = snap.participants.count { p -> bots.none { it.id == p.id } }
            when (snap.phase) {
                Phase.LOBBY -> if (humans > 0) { delay(2_000); println("start: ${session.start()}") }
                Phase.QUESTION -> {
                    val q = snap.question!!
                    if (q.index != lastQuestion) {
                        lastQuestion = q.index
                        bots.forEach { bot ->
                            launch {
                                delay(random.nextLong(1_500, 9_000))
                                val choice = if (random.nextInt(100) < 60) q.correctIndex else random.nextInt(q.answers.size)
                                val r = session.answer(bot.id, q.index + 1, choice)
                                if (r != AnswerResult.ACCEPTED) println("bot answer: $r")
                            }
                        }
                    }
                }
                Phase.PODIUM -> { delay(8_000); session.playAgain() }
                else -> Unit
            }
        }
    }
    Thread.currentThread().join()
}
