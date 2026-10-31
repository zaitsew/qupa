package com.fabriquefutur.qupa.protocol

import com.fabriquefutur.qupa.FakeClock
import com.fabriquefutur.qupa.game.GameEngine
import com.fabriquefutur.qupa.game.GameSettings
import com.fabriquefutur.qupa.game.JoinResult
import com.fabriquefutur.qupa.game.Mode
import com.fabriquefutur.qupa.testBank
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ProtocolTest {
    private fun roundTripClient(m: ClientMessage) {
        val text = WireJson.encodeToString(ClientMessage.serializer(), m)
        assertEquals(m, WireJson.decodeFromString(ClientMessage.serializer(), text))
    }

    private fun roundTripServer(m: ServerMessage) {
        val text = WireJson.encodeToString(ServerMessage.serializer(), m)
        assertEquals(m, WireJson.decodeFromString(ServerMessage.serializer(), text))
    }

    @Test fun clientMessagesRoundTrip() {
        roundTripClient(Join("Ana"))
        roundTripClient(Join("Ana", "abc123"))
        roundTripClient(Answer(3, 2))
        roundTripClient(Ping(7))
    }

    @Test fun serverMessagesRoundTrip() {
        roundTripServer(Welcome("id1", "Ana", "#FF6B6B"))
        roundTripServer(Reject("full"))
        roundTripServer(Pong(7))
        roundTripServer(Ack(2, false, "late"))
        roundTripServer(View("question", 2, 10, "mc", 4, 8_000, 15_000, false, -1, -1, 0, 1200, 2, 5, "Ana", "#FF6B6B"))
    }

    @Test fun theTypeTravelsInTheTField() {
        val text = WireJson.encodeToString(ClientMessage.serializer(), Answer(1, 0))
        assertTrue(text, text.contains("\"t\":\"answer\""))
    }

    @Test fun unknownFieldsAreIgnoredForForwardCompatibility() {
        val m = WireJson.decodeFromString(ClientMessage.serializer(), """{"t":"join","name":"Ana","future":42}""")
        assertEquals(Join("Ana"), m)
    }

    @Test fun anUnknownMessageTypeFailsToDecode() {
        val result = runCatching { WireJson.decodeFromString(ClientMessage.serializer(), """{"t":"launch"}""") }
        assertTrue(result.isFailure)
    }

    @Test fun theViewHidesTheCorrectAnswerUntilReveal() {
        val clock = FakeClock()
        val engine = GameEngine(Mode.PHONES, clock, testBank(), Random(1))
        val me = (engine.join("Ana") as JoinResult.Joined).id
        engine.updateSettings(GameSettings(answerSeconds = 15))
        engine.start()
        val open = PhoneViews.build(engine.snapshot(), me, clock.nowMs())!!
        assertEquals("question", open.phase)
        assertEquals(1, open.q)
        assertEquals(-1, open.correct)
        assertEquals(15_000, open.remainingMs)
        clock.advance(4_000)
        assertEquals(11_000, PhoneViews.build(engine.snapshot(), me, clock.nowMs())!!.remainingMs)
        engine.advance()
        val shown = PhoneViews.build(engine.snapshot(), me, clock.nowMs())!!
        assertEquals("reveal", shown.phase)
        assertTrue(shown.correct in 0..3)
    }

    @Test fun anUnknownPlayerHasNoView() {
        val engine = GameEngine(Mode.PHONES, FakeClock(), testBank())
        assertNull(PhoneViews.build(engine.snapshot(), "ghost", 0))
        val me = (engine.join("Ana") as JoinResult.Joined).id
        assertNotNull(PhoneViews.build(engine.snapshot(), me, 0))
    }
}
