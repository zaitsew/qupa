package com.fabriquefutur.qupa.game

import com.fabriquefutur.qupa.testBank
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class GameSessionTest {
    private fun TestScope.session(mode: Mode = Mode.PHONES) = GameSession(
        mode = mode,
        clock = { currentTime },
        bank = testBank(),
        scope = backgroundScope,
        roomCode = "0042",
        random = Random(3),
    )

    @Test fun theTickerEndsAQuestionInVirtualTime() = runTest {
        val s = session()
        val seated = (s.join("Ana", null) as JoinOutcome.Ok).seated
        assertEquals(StartResult.STARTED, s.start())
        assertEquals(Phase.QUESTION, s.snapshots.value.phase)
        advanceTimeBy(14_900)
        assertEquals(Phase.QUESTION, s.snapshots.value.phase)
        advanceTimeBy(300)
        assertEquals(Phase.REVEAL, s.snapshots.value.phase)
        assertEquals(0, s.snapshots.value.participant(seated.id)!!.score)
        advanceTimeBy(5_200)
        assertEquals(Phase.SCOREBOARD, s.snapshots.value.phase)
        advanceTimeBy(6_200)
        assertEquals(1, s.snapshots.value.question!!.index)
    }

    @Test fun phoneNumbersCountFromOne() = runTest {
        val s = session()
        val seated = (s.join("Ana", null) as JoinOutcome.Ok).seated
        s.start()
        val correct = s.snapshots.value.question!!.correctIndex
        assertEquals(AnswerResult.STALE, s.answer(seated.id, seated.token, 0, correct))
        assertEquals(AnswerResult.ACCEPTED, s.answer(seated.id, seated.token, 1, correct))
    }

    @Test fun aSocketThatLostTheSeatCannotAnswer() = runTest {
        val s = session()
        val first = (s.join("Ana", null) as JoinOutcome.Ok).seated
        val second = (s.join("Ana", first.id) as JoinOutcome.Ok).seated
        s.start()
        val correct = s.snapshots.value.question!!.correctIndex
        assertEquals(AnswerResult.UNKNOWN_PLAYER, s.answer(first.id, first.token, 1, correct))
        assertEquals(AnswerResult.ACCEPTED, s.answer(second.id, second.token, 1, correct))
    }

    @Test fun aNewerSocketKeepsTheSeatWhenTheOldOneCloses() = runTest {
        val s = session()
        val first = (s.join("Ana", null) as JoinOutcome.Ok).seated
        val second = (s.join(null, first.id) as JoinOutcome.Ok).seated
        assertEquals(first.id, second.id)
        assertTrue(second.reconnected)
        s.disconnect(first.id, first.token) // the stale socket closing
        assertTrue(s.snapshots.value.participant(first.id)!!.connected)
        s.disconnect(second.id, second.token)
        assertEquals(false, s.snapshots.value.participant(first.id)!!.connected)
    }

    @Test fun theNinthJoinIsRejectedWithFull() = runTest {
        val s = session()
        repeat(8) { s.join("P$it", null) }
        assertEquals(JoinOutcome.No(JoinRejection.FULL), s.join("Late", null))
    }

    @Test fun theTvCanAnswerForTheActiveTeam() = runTest {
        val s = session(Mode.TEAMS)
        val red = (s.addTeam("Red") as JoinResult.Joined).id
        s.addTeam("Blue")
        s.start()
        val correct = s.snapshots.value.question!!.correctIndex
        assertEquals(AnswerResult.ACCEPTED, s.answerFromTv(red, correct))
        assertEquals(Phase.REVEAL, s.snapshots.value.phase)
        assertEquals(1000, s.snapshots.value.participant(red)!!.score)
    }
}
