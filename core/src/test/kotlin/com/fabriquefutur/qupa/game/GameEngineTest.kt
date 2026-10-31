package com.fabriquefutur.qupa.game

import com.fabriquefutur.qupa.FakeClock
import com.fabriquefutur.qupa.testBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    private val clock = FakeClock()
    private var ids = 0

    private fun engine(mode: Mode = Mode.PHONES, bank: List<Question> = testBank(), seen: Set<String> = emptySet()) =
        GameEngine(mode, clock, bank, Random(42), Timings(), seen) { "id${ids++}" }

    private fun GameEngine.add(name: String): String = (join(name) as JoinResult.Joined).id

    private fun GameEngine.correct(): Int = snapshot().question!!.correctIndex
    private fun GameEngine.wrong(): Int = (correct() + 1) % snapshot().question!!.answers.size

    /** Runs the clock past every pause until the engine reaches [target]. */
    private fun GameEngine.runUntil(target: Phase) {
        var guard = 0
        while (snapshot().phase != target && guard++ < 1000) {
            clock.advance(1_000)
            tick()
        }
        assertEquals(target, snapshot().phase)
    }

    // ---- scoring and answering ----

    @Test fun aFastCorrectAnswerEarnsNearlyFullPoints() {
        val e = engine()
        val ana = e.add("Ana")
        e.add("Bo")
        e.updateSettings(GameSettings(answerSeconds = 10))
        assertEquals(StartResult.STARTED, e.start())
        clock.advance(1_000)
        assertEquals(AnswerResult.ACCEPTED, e.submitAnswer(ana, 0, e.correct()))
        e.advance() // host closes the question
        assertEquals(900, e.snapshot().participant(ana)!!.score)
    }

    @Test fun aSlowCorrectAnswerEarnsAtLeastHalf() {
        val e = engine()
        val ana = e.add("Ana")
        e.add("Bo")
        e.updateSettings(GameSettings(answerSeconds = 10))
        e.start()
        clock.advance(9_800)
        e.submitAnswer(ana, 0, e.correct())
        e.advance()
        assertEquals(500, e.snapshot().participant(ana)!!.score)
    }

    @Test fun aWrongAnswerScoresNothing() {
        val e = engine()
        val ana = e.add("Ana")
        e.add("Bo")
        e.start()
        e.submitAnswer(ana, 0, e.wrong())
        e.advance()
        val p = e.snapshot().participant(ana)!!
        assertEquals(0, p.score)
        assertEquals(0, p.gained)
    }

    @Test fun theQuestionTimesOutAndNoAnswerScoresNothing() {
        val e = engine()
        val ana = e.add("Ana")
        e.start()
        clock.advance(14_999)
        e.tick()
        assertEquals(Phase.QUESTION, e.snapshot().phase)
        clock.advance(1)
        e.tick()
        assertEquals(Phase.REVEAL, e.snapshot().phase)
        assertEquals(0, e.snapshot().participant(ana)!!.score)
        assertEquals(-1, e.snapshot().participant(ana)!!.choice)
    }

    @Test fun anAnswerAtOrAfterTheDeadlineIsTooLate() {
        val e = engine()
        val ana = e.add("Ana")
        val bo = e.add("Bo")
        e.start()
        clock.advance(15_000) // deadline reached, tick has not run yet
        assertEquals(AnswerResult.TOO_LATE, e.submitAnswer(ana, 0, e.correct()))
        e.tick()
        assertEquals(AnswerResult.NOT_ACCEPTING, e.submitAnswer(bo, 0, e.correct()))
        assertEquals(0, e.snapshot().participant(ana)!!.score)
    }

    @Test fun aSecondAnswerIsIgnored() {
        val e = engine()
        val ana = e.add("Ana")
        e.add("Bo")
        e.start()
        assertEquals(AnswerResult.ACCEPTED, e.submitAnswer(ana, 0, e.wrong()))
        assertEquals(AnswerResult.DUPLICATE, e.submitAnswer(ana, 0, e.correct()))
        e.advance()
        assertEquals(0, e.snapshot().participant(ana)!!.score)
    }

    @Test fun anAnswerForAnotherQuestionIsStale() {
        val e = engine()
        val ana = e.add("Ana")
        e.start()
        assertEquals(AnswerResult.STALE, e.submitAnswer(ana, 3, 0))
        assertEquals(AnswerResult.STALE, e.submitAnswer(ana, -1, 0))
    }

    @Test fun anOutOfRangeChoiceIsRefused() {
        val e = engine()
        val ana = e.add("Ana")
        e.start()
        assertEquals(AnswerResult.BAD_CHOICE, e.submitAnswer(ana, 0, 9))
        assertEquals(AnswerResult.BAD_CHOICE, e.submitAnswer(ana, 0, -1))
        assertEquals(AnswerResult.UNKNOWN_PLAYER, e.submitAnswer("ghost", 0, 0))
    }

    @Test fun theQuestionEndsEarlyWhenEveryConnectedPlayerHasAnswered() {
        val e = engine()
        val ana = e.add("Ana")
        val bo = e.add("Bo")
        e.start()
        e.submitAnswer(ana, 0, e.correct())
        assertEquals(Phase.QUESTION, e.snapshot().phase)
        e.submitAnswer(bo, 0, e.wrong())
        assertEquals(Phase.REVEAL, e.snapshot().phase)
    }

    @Test fun aDisconnectedPlayerDoesNotHoldTheQuestionOpen() {
        val e = engine()
        val ana = e.add("Ana")
        val bo = e.add("Bo")
        e.start()
        e.submitAnswer(ana, 0, e.correct())
        e.setConnected(bo, false)
        assertEquals(Phase.REVEAL, e.snapshot().phase)
    }

    @Test fun trueFalseQuestionsHaveTwoAnswersInFixedOrder() {
        val e = engine(bank = testBank().filter { it.type == QuestionType.BOOLEAN })
        e.add("Ana")
        e.start()
        val q = e.snapshot().question!!
        assertEquals(listOf("True", "False"), q.answers)
        assertEquals(0, q.correctIndex) // test bank's booleans are all "True"
    }

    @Test fun multipleChoiceAnswersAreShuffledAndContainTheCorrectOne() {
        val e = engine(bank = testBank().filter { it.type == QuestionType.MULTIPLE })
        e.add("Ana")
        e.start()
        val q = e.snapshot().question!!
        assertEquals(4, q.answers.size)
        assertTrue(q.answers[q.correctIndex].startsWith("right"))
        val positions = (0 until 12).map {
            val e2 = GameEngine(Mode.PHONES, clock, testBank().filter { it.type == QuestionType.MULTIPLE }, Random(it))
            e2.join("Ana"); e2.start(); e2.snapshot().question!!.correctIndex
        }.toSet()
        assertTrue("the correct answer should not always sit in one slot: $positions", positions.size > 1)
    }

    // ---- flow ----

    @Test fun aWholeGameWalksThroughEveryPhase() {
        val e = engine()
        e.add("Ana")
        e.updateSettings(GameSettings(questions = 10, answerSeconds = 10))
        e.start()
        val seen = mutableListOf<Phase>()
        var guard = 0
        while (e.snapshot().phase != Phase.PODIUM && guard++ < 2000) {
            val p = e.snapshot().phase
            if (seen.lastOrNull() != p) seen += p
            clock.advance(1_000)
            e.tick()
        }
        assertEquals(Phase.PODIUM, e.snapshot().phase)
        assertEquals(listOf(Phase.QUESTION, Phase.REVEAL, Phase.SCOREBOARD), seen.take(3))
        assertEquals(10, e.askedIds().size)
        assertEquals(9, e.snapshot().question!!.index)
    }

    @Test fun theLastQuestionGoesStraightToThePodium() {
        val e = engine()
        e.add("Ana")
        e.start()
        repeat(9) {
            e.advance(); e.advance(); e.advance() // close, skip reveal, skip scoreboard
        }
        assertEquals(9, e.snapshot().question!!.index)
        e.advance() // close question
        e.advance() // skip reveal
        assertEquals(Phase.PODIUM, e.snapshot().phase)
    }

    @Test fun startNeedsAPlayerAndQuestions() {
        val e = engine()
        assertEquals(StartResult.NOT_ENOUGH_PLAYERS, e.start())
        e.add("Ana")
        e.updateSettings(GameSettings(categories = setOf(32)))
        assertEquals(StartResult.NO_QUESTIONS, e.start())
        e.updateSettings(GameSettings())
        assertEquals(StartResult.STARTED, e.start())
        assertEquals(StartResult.WRONG_PHASE, e.start())
    }

    @Test fun fewerQuestionsThanRequestedShortensTheGame() {
        val e = engine(bank = testBank(4))
        e.add("Ana")
        e.start()
        assertEquals(4, e.snapshot().question!!.total)
    }

    @Test fun settingsCannotChangeMidGame() {
        val e = engine()
        e.add("Ana")
        e.start()
        e.updateSettings(GameSettings(questions = 20))
        assertEquals(10, e.snapshot().settings.questions)
    }

    @Test fun skippingTheWaitsWithAdvance() {
        val e = engine()
        e.add("Ana")
        e.start()
        e.advance()
        assertEquals(Phase.REVEAL, e.snapshot().phase)
        e.advance()
        assertEquals(Phase.SCOREBOARD, e.snapshot().phase)
        e.advance()
        assertEquals(Phase.QUESTION, e.snapshot().phase)
        assertEquals(1, e.snapshot().question!!.index)
    }

    @Test fun aLongStallCatchesUpInOneTick() {
        val e = engine()
        e.add("Ana")
        e.start()
        clock.advance(15_000); e.tick()
        clock.advance(5_000); e.tick()
        clock.advance(6_000); e.tick()
        assertEquals(Phase.QUESTION, e.snapshot().phase)
        assertEquals(1, e.snapshot().question!!.index)
    }

    // ---- seats ----

    @Test fun theNinthPlayerIsRefused() {
        val e = engine()
        repeat(8) { assertTrue(e.join("P$it") is JoinResult.Joined) }
        val ninth = e.join("Late")
        assertEquals(JoinResult.Rejected(JoinRejection.FULL), ninth)
        assertEquals(8, e.snapshot().participants.size)
    }

    @Test fun aReturningPlayerKeepsTheSeatEvenWhenFull() {
        val e = engine()
        val first = e.add("P0")
        repeat(7) { e.add("P${it + 1}") }
        e.setConnected(first, false)
        val back = e.join("whatever", first) as JoinResult.Joined
        assertTrue(back.reconnected)
        assertEquals("P0", back.name)
        assertEquals(first, back.id)
        assertTrue(e.snapshot().participant(first)!!.connected)
    }

    @Test fun reconnectKeepsTheScore() {
        val e = engine()
        val ana = e.add("Ana")
        e.add("Bo")
        e.start()
        e.submitAnswer(ana, 0, e.correct())
        e.advance()
        val score = e.snapshot().participant(ana)!!.score
        assertTrue(score > 0)
        e.setConnected(ana, false)
        e.join(null, ana)
        assertEquals(score, e.snapshot().participant(ana)!!.score)
    }

    @Test fun aNewPlayerCanJoinMidGameAndStartsAtZero() {
        val e = engine()
        e.add("Ana")
        e.start()
        e.advance(); e.advance(); e.advance()
        val late = e.add("Late")
        assertEquals(0, e.snapshot().participant(late)!!.score)
        assertEquals(AnswerResult.ACCEPTED, e.submitAnswer(late, 1, e.correct()))
    }

    @Test fun blankNamesAreRefusedAndDuplicatesNumbered() {
        val e = engine()
        assertEquals(JoinResult.Rejected(JoinRejection.BAD_NAME), e.join("   "))
        assertEquals("Ana", (e.join("Ana") as JoinResult.Joined).name)
        assertEquals("ana 2", (e.join("ana") as JoinResult.Joined).name)
        assertEquals("Ana 3", (e.join("Ana") as JoinResult.Joined).name)
    }

    @Test fun aNumberedNameStillFitsSixteenCharacters() {
        val e = engine()
        e.join("abcdefghijklmnop")
        val second = (e.join("abcdefghijklmnop") as JoinResult.Joined).name
        assertEquals("abcdefghijklmn 2", second)
        assertEquals(16, second.length)
    }

    @Test fun seatsGetDistinctColours() {
        val e = engine()
        repeat(8) { e.add("P$it") }
        assertEquals(8, e.snapshot().participants.map { it.colorIndex }.toSet().size)
    }

    @Test fun aColourIsFreedWhenASeatLeavesTheLobby() {
        val e = engine()
        val a = e.add("A")
        e.add("B")
        val colour = e.snapshot().participant(a)!!.colorIndex
        e.remove(a)
        val c = e.add("C")
        assertEquals(colour, e.snapshot().participant(c)!!.colorIndex)
    }

    @Test fun aPhoneThatLeftTheLobbyLosesItsSeatAfterTheGrace() {
        val e = engine()
        val ana = e.add("Ana")
        e.add("Bo")
        e.setConnected(ana, false)
        clock.advance(14_000); e.tick()
        assertEquals(2, e.snapshot().participants.size)
        clock.advance(1_000); e.tick()
        assertEquals(listOf("Bo"), e.snapshot().participants.map { it.name })
    }

    @Test fun aPhoneThatLeftMidGameKeepsItsSeat() {
        val e = engine()
        val ana = e.add("Ana")
        e.add("Bo")
        e.start()
        e.setConnected(ana, false)
        e.runUntil(Phase.PODIUM)
        assertEquals(2, e.snapshot().participants.size)
    }

    // ---- ranking, replay ----

    @Test fun equalScoresShareARank() {
        val e = engine()
        val ana = e.add("Ana")
        val bo = e.add("Bo")
        val cy = e.add("Cy")
        e.start()
        e.submitAnswer(ana, 0, e.correct())
        e.submitAnswer(bo, 0, e.correct())
        e.submitAnswer(cy, 0, e.wrong())
        val s = e.snapshot()
        assertEquals(1, s.participant(ana)!!.rank)
        assertEquals(1, s.participant(bo)!!.rank)
        assertEquals(3, s.participant(cy)!!.rank)
        assertEquals(listOf("Ana", "Bo", "Cy"), s.ranked.map { it.name })
    }

    @Test fun theFasterAnswerRanksFirst() {
        val e = engine()
        val ana = e.add("Ana")
        val bo = e.add("Bo")
        e.start()
        e.submitAnswer(bo, 0, e.correct())
        clock.advance(5_000)
        e.submitAnswer(ana, 0, e.correct())
        val s = e.snapshot()
        assertTrue(s.participant(bo)!!.score > s.participant(ana)!!.score)
        assertEquals("Bo", s.ranked.first().name)
    }

    @Test fun playAgainKeepsPlayersClearsScoresAndNeverRepeatsQuestions() {
        val e = engine(bank = testBank(60))
        val ana = e.add("Ana")
        e.start()
        e.submitAnswer(ana, 0, e.correct())
        e.runUntil(Phase.PODIUM)
        val firstRound = e.askedIds()
        assertEquals(10, firstRound.size)
        e.playAgain()
        assertEquals(Phase.LOBBY, e.snapshot().phase)
        assertEquals(listOf("Ana"), e.snapshot().participants.map { it.name })
        assertEquals(0, e.snapshot().participant(ana)!!.score)
        e.start()
        e.runUntil(Phase.PODIUM)
        val all = e.askedIds()
        assertEquals(20, all.size) // no repeat: 10 + 10 distinct
        assertTrue(all.containsAll(firstRound))
    }

    @Test fun playAgainDropsPlayersWhoLeft() {
        val e = engine()
        val ana = e.add("Ana")
        e.add("Bo")
        e.start()
        e.setConnected(ana, false)
        e.runUntil(Phase.PODIUM)
        e.playAgain()
        assertEquals(listOf("Bo"), e.snapshot().participants.map { it.name })
    }

    @Test fun questionsSeenInEarlierSessionsComeLast() {
        val bank = testBank(30)
        val seen = bank.take(20).map { it.id }.toSet()
        val e = engine(bank = bank, seen = seen)
        e.add("Ana")
        e.start()
        e.runUntil(Phase.PODIUM)
        // 10 unseen exist, 10 are asked: all of them unseen.
        assertTrue(e.askedIds().none { it in seen })
    }

    @Test fun theVersionRisesOnEveryChange() {
        val e = engine()
        val v0 = e.snapshot().version
        e.add("Ana")
        val v1 = e.snapshot().version
        assertNotEquals(v0, v1)
        e.tick() // nothing changes in a lobby
        assertEquals(v1, e.snapshot().version)
    }

    // ---- teams (remote mode) ----

    @Test fun teamsTakeTurnsInSeatOrder() {
        val e = engine(Mode.TEAMS)
        val red = e.add("Red")
        val blue = e.add("Blue")
        val green = e.add("Green")
        e.start()
        val order = mutableListOf<String?>()
        repeat(6) {
            order += e.snapshot().activeId
            e.advance(); e.advance(); e.advance()
        }
        assertEquals(listOf(red, blue, green, red, blue, green), order)
    }

    @Test fun onlyTheActiveTeamMayAnswer() {
        val e = engine(Mode.TEAMS)
        val red = e.add("Red")
        val blue = e.add("Blue")
        e.start()
        assertEquals(AnswerResult.NOT_YOUR_TURN, e.submitAnswer(blue, 0, e.correct()))
        assertEquals(AnswerResult.ACCEPTED, e.submitAnswer(red, 0, e.correct()))
    }

    @Test fun anAnswerFromTheActiveTeamClosesTheQuestionAtOnce() {
        val e = engine(Mode.TEAMS)
        val red = e.add("Red")
        e.add("Blue")
        e.start()
        e.submitAnswer(red, 0, e.wrong())
        assertEquals(Phase.REVEAL, e.snapshot().phase)
    }

    @Test fun remoteScoringHasNoSpeedBonus() {
        val e = engine(Mode.TEAMS)
        val red = e.add("Red")
        e.add("Blue")
        e.start()
        clock.advance(14_000)
        e.submitAnswer(red, 0, e.correct())
        assertEquals(1000, e.snapshot().participant(red)!!.score)
    }

    @Test fun aTeamThatRunsOutOfTimeScoresNothing() {
        val e = engine(Mode.TEAMS)
        val red = e.add("Red")
        e.add("Blue")
        e.start()
        clock.advance(15_000); e.tick()
        assertEquals(Phase.REVEAL, e.snapshot().phase)
        assertEquals(0, e.snapshot().participant(red)!!.score)
    }

    @Test fun teamModeNeedsTwoTeamsAndHoldsAtMostFour() {
        val e = engine(Mode.TEAMS)
        e.add("Red")
        assertEquals(StartResult.NOT_ENOUGH_PLAYERS, e.start())
        e.add("Blue"); e.add("Green"); e.add("Yellow")
        assertEquals(JoinResult.Rejected(JoinRejection.FULL), e.join("Pink"))
    }

    @Test fun teamsCanBeRenamedAndRemovedInTheLobbyOnly() {
        val e = engine(Mode.TEAMS)
        val red = e.add("Red")
        val blue = e.add("Blue")
        assertTrue(e.rename(red, "Foxes"))
        assertFalse(e.rename(red, "  "))
        assertEquals("Foxes", e.snapshot().participant(red)!!.name)
        assertTrue(e.remove(blue))
        e.add("Blue2")
        e.start()
        assertFalse(e.remove(red))
    }

    @Test fun teamsNeverLoseSeatsToTheLobbyGrace() {
        val e = engine(Mode.TEAMS)
        val red = e.add("Red")
        e.add("Blue")
        e.setConnected(red, false)
        clock.advance(60_000); e.tick()
        assertEquals(2, e.snapshot().participants.size)
    }
}
