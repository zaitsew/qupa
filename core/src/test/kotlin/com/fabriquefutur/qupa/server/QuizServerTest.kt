package com.fabriquefutur.qupa.server

import com.fabriquefutur.qupa.game.Clock
import com.fabriquefutur.qupa.game.GameSession
import com.fabriquefutur.qupa.game.Mode
import com.fabriquefutur.qupa.game.Phase
import com.fabriquefutur.qupa.game.Timings
import com.fabriquefutur.qupa.protocol.Ack
import com.fabriquefutur.qupa.protocol.Answer
import com.fabriquefutur.qupa.protocol.ClientMessage
import com.fabriquefutur.qupa.protocol.Join
import com.fabriquefutur.qupa.protocol.Ping
import com.fabriquefutur.qupa.protocol.Pong
import com.fabriquefutur.qupa.protocol.Reject
import com.fabriquefutur.qupa.protocol.ServerMessage
import com.fabriquefutur.qupa.protocol.View
import com.fabriquefutur.qupa.protocol.Welcome
import com.fabriquefutur.qupa.protocol.WireJson
import com.fabriquefutur.qupa.testBank
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.random.Random

class QuizServerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var session: GameSession
    private lateinit var server: QuizServer
    private lateinit var client: HttpClient
    private var port = 0

    @Before fun setUp() {
        session = GameSession(
            mode = Mode.PHONES,
            clock = Clock.System,
            bank = testBank(),
            scope = scope,
            roomCode = "4321",
            timings = Timings(revealMs = 300, scoreboardMs = 300, lobbyGraceMs = 15_000),
            random = Random(1),
        )
        server = QuizServer(session) { path -> if (path == "index.html") "<html>qupa</html>".toByteArray() else null }
        port = server.start(preferredPort = 0)
        client = HttpClient(CIO) { install(WebSockets) }
    }

    @After fun tearDown() {
        client.close()
        server.stop()
        scope.cancel()
    }

    private fun status(path: String): Int {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        c.connectTimeout = 3000
        c.readTimeout = 3000
        return try { c.responseCode } finally { c.disconnect() }
    }

    private suspend fun DefaultClientWebSocketSession.say(m: ClientMessage) =
        send(Frame.Text(WireJson.encodeToString(ClientMessage.serializer(), m)))

    private suspend fun DefaultClientWebSocketSession.next(): ServerMessage {
        while (true) {
            val f = incoming.receive()
            if (f is Frame.Text) return WireJson.decodeFromString(ServerMessage.serializer(), f.readText())
        }
    }

    /** Reads messages until [match] is satisfied, within five seconds. */
    private suspend fun DefaultClientWebSocketSession.until(match: (ServerMessage) -> Boolean): List<ServerMessage> =
        withTimeout(5_000) {
            val seen = mutableListOf<ServerMessage>()
            do { seen += next() } while (!match(seen.last()))
            seen
        }

    private fun open(block: suspend DefaultClientWebSocketSession.() -> Unit) = runBlocking {
        withTimeout(15_000) { client.webSocket(host = "127.0.0.1", port = port, path = "/ws?r=4321", block = block) }
    }

    @Test fun thePageNeedsTheRoomCode() {
        assertEquals(200, status("/?r=4321"))
        assertEquals(403, status("/"))
        assertEquals(403, status("/?r=0000"))
        assertEquals(403, status("/?r="))
        assertEquals(403, status("/ws"))
        assertEquals(403, status("/ws?r=1111"))
        assertEquals(403, status("/nope"))
        assertEquals(404, status("/nope?r=4321"))
    }

    @Test fun thePageIsServedWithoutCaching() {
        val c = URL("http://127.0.0.1:$port/?r=4321").openConnection() as HttpURLConnection
        assertEquals("no-store", c.getHeaderField("Cache-Control"))
        assertTrue(c.inputStream.readBytes().decodeToString().contains("qupa"))
        assertTrue(c.contentType.startsWith("text/html"))
        c.disconnect()
    }

    @Test fun aSocketWithoutTheCodeIsRefusedBeforeTheUpgrade() = runBlocking {
        val result = runCatching {
            withTimeout(10_000) { client.webSocket(host = "127.0.0.1", port = port, path = "/ws?r=9999") { } }
        }
        assertTrue("a wrong room code must not upgrade", result.isFailure)
        assertTrue(session.snapshots.value.participants.isEmpty())
    }

    @Test fun onePlayerJoinsAndPlaysOneQuestionOverARealWebSocket() = open {
        say(Join("  Ana  "))
        val welcome = next() as Welcome
        assertEquals("Ana", welcome.name)
        assertTrue(welcome.color.startsWith("#"))
        val lobby = until { it is View } .last() as View
        assertEquals("lobby", lobby.phase)
        assertEquals(1, lobby.players)

        session.start()
        val question = until { it is View && it.phase == "question" }.last() as View
        assertEquals(1, question.q)
        assertEquals(-1, question.correct)
        assertTrue(question.choices == 2 || question.choices == 4)
        val correct = session.snapshots.value.question!!.correctIndex

        say(Answer(1, correct))
        val after = until { it is View && it.phase == "reveal" }
        val ack = after.filterIsInstance<Ack>().firstOrNull() ?: until { it is Ack }.filterIsInstance<Ack>().first()
        assertTrue(ack.ok)
        val reveal = after.last() as View
        assertEquals(correct, reveal.correct)
        assertEquals(correct, reveal.chosen)
        assertTrue(reveal.gained in 500..1000)
        assertEquals(reveal.gained, reveal.score)
        assertEquals(1, reveal.rank)
    }

    @Test fun aSecondAnswerGetsADuplicateAckAndChangesNothing() = open {
        say(Join("Ana"))
        until { it is View }
        // A second seat keeps the question open after the first answer.
        session.join("Bo", null)
        session.start()
        until { it is View && it.phase == "question" }
        val snap = session.snapshots.value.question!!
        say(Answer(1, snap.correctIndex))
        assertTrue(until { it is Ack }.last().let { it is Ack && it.ok })
        say(Answer(1, (snap.correctIndex + 1) % snap.answers.size))
        val dup = until { it is Ack }.last() as Ack
        assertEquals("duplicate", dup.reason)
    }

    @Test fun theNinthPlayerGetsRejectedAsFull() = open {
        repeat(8) { session.join("P$it", null) }
        say(Join("Late"))
        assertEquals(Reject("full"), next())
    }

    @Test fun aPhoneThatSleptComesBackToItsSeatAndScore() = runBlocking {
        var id = ""
        var score = 0
        withTimeout(15_000) {
            client.webSocket(host = "127.0.0.1", port = port, path = "/ws?r=4321") {
                say(Join("Ana"))
                id = (next() as Welcome).playerId
                until { it is View }
                val bo = (session.join("Bo", null) as com.fabriquefutur.qupa.game.JoinOutcome.Ok).seated
                session.disconnect(bo.id, bo.token) // Bo never opens a socket, so he must not hold the question
                session.start()
                until { it is View && it.phase == "question" }
                say(Answer(1, session.snapshots.value.question!!.correctIndex))
                score = (until { it is View && it.phase == "reveal" }.last() as View).score
            }
        }
        assertTrue(score >= 500)
        // The socket is closed now; the seat stays with its score.
        withTimeout(5_000) {
            while (session.snapshots.value.participant(id)?.connected != false) kotlinx.coroutines.delay(20)
        }
        withTimeout(15_000) {
            client.webSocket(host = "127.0.0.1", port = port, path = "/ws?r=4321") {
                say(Join("ignored", id))
                val back = next() as Welcome
                assertEquals(id, back.playerId)
                assertEquals("Ana", back.name)
                val view = until { it is View }.last() as View
                assertEquals(score, view.score)
            }
        }
        assertEquals(2, session.snapshots.value.participants.size)
    }

    @Test fun pingGetsAPongAndJunkIsIgnored() = open {
        send(Frame.Text("not json"))
        send(Frame.Text("""{"t":"launch"}"""))
        say(Ping(5))
        assertEquals(Pong(5), next())
    }

    @Test fun answeringBeforeJoiningIsRefused() = open {
        say(Answer(1, 0))
        assertEquals(Ack(1, false, "closed"), next())
    }

    @Test fun theNextFreePortIsUsedWhenThePreferredOneIsBusy() {
        ServerSocket(0).use { busy ->
            val other = QuizServer(session) { null }
            try {
                val bound = other.start(preferredPort = busy.localPort, attempts = 10)
                assertNotEquals(busy.localPort, bound)
                assertTrue(bound in busy.localPort + 1 until busy.localPort + 10)
            } finally {
                other.stop()
            }
        }
    }

    @Test fun stoppingFreesThePort() {
        val extra = QuizServer(session) { null }
        val p = extra.start(0)
        extra.stop()
        assertEquals(0, extra.port)
        ServerSocket(p).close() // would throw if still bound
    }

    @Test fun theGameKeepsRunningWhenAPhoneDropsMidQuestion() = open {
        say(Join("Ana"))
        until { it is View }
        val bo = (session.join("Bo", null) as com.fabriquefutur.qupa.game.JoinOutcome.Ok).seated
        session.start()
        until { it is View && it.phase == "question" }
        session.disconnect(bo.id, bo.token)
        say(Answer(1, session.snapshots.value.question!!.correctIndex))
        until { it is View && it.phase == "reveal" }
        assertEquals(Phase.REVEAL, session.snapshots.value.phase)
    }
}
