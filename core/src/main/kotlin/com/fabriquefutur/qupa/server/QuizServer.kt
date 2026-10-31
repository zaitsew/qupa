package com.fabriquefutur.qupa.server

import com.fabriquefutur.qupa.game.AnswerResult
import com.fabriquefutur.qupa.game.GameSession
import com.fabriquefutur.qupa.game.JoinOutcome
import com.fabriquefutur.qupa.game.JoinRejection
import com.fabriquefutur.qupa.game.PlayerColors
import com.fabriquefutur.qupa.game.RoomCode
import com.fabriquefutur.qupa.protocol.Ack
import com.fabriquefutur.qupa.protocol.Answer
import com.fabriquefutur.qupa.protocol.ClientMessage
import com.fabriquefutur.qupa.protocol.Join
import com.fabriquefutur.qupa.protocol.PhoneViews
import com.fabriquefutur.qupa.protocol.Ping
import com.fabriquefutur.qupa.protocol.Pong
import com.fabriquefutur.qupa.protocol.Reject
import com.fabriquefutur.qupa.protocol.ServerMessage
import com.fabriquefutur.qupa.protocol.Welcome
import com.fabriquefutur.qupa.protocol.WireJson
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.BindException

/** Reads a file of the phone page from the app's assets (or, in tests, from disk). */
fun interface WebAssets {
    fun read(path: String): ByteArray?
}

/**
 * The web server that lives on the TV. It serves the phone page at `/` and one
 * WebSocket at `/ws`; every request answers 403 unless it carries the room
 * code (`?r=1234`). It never makes an outbound connection.
 */
class QuizServer(
    private val session: GameSession,
    private val assets: WebAssets,
) {
    private var engine: ApplicationEngine? = null

    /** The port the server listens on, or 0 when it is stopped. */
    var port: Int = 0
        private set

    /**
     * Binds the first free port from [preferredPort] up to [attempts] ports
     * further; 0 means "any free port" (tests). Returns the bound port.
     */
    fun start(preferredPort: Int = 8080, attempts: Int = 20): Int {
        check(engine == null) { "already started" }
        var lastError: Exception? = null
        val candidates = if (preferredPort == 0) listOf(0) else (preferredPort until preferredPort + attempts).toList()
        for (candidate in candidates) {
            val server = embeddedServer(CIO, port = candidate, host = "0.0.0.0") { configure() }
            try {
                server.start(wait = false)
                val bound = runBlocking { server.resolvedConnectors().first().port }
                engine = server
                port = bound
                return bound
            } catch (e: Exception) {
                lastError = e
                runCatching { server.stop(0, 0) }
                if (!isPortInUse(e)) throw e
            }
        }
        throw lastError ?: BindException("no free port")
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 0, timeoutMillis = 500)
        engine = null
        port = 0
    }

    private fun isPortInUse(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t is BindException) return true
            t = t.cause
        }
        return false
    }

    private fun io.ktor.server.application.Application.configure() {
        install(WebSockets) {
            pingPeriodMillis = 15_000
            timeoutMillis = 30_000
            maxFrameSize = 8 * 1024
        }
        // Every request, whatever its path, needs the room code.
        intercept(ApplicationCallPipeline.Plugins) {
            if (!roomOk(call)) {
                forbidden(call)
                finish()
            }
        }
        routing {
            get("/") {
                val page = assets.read("index.html")
                if (page == null) {
                    call.respondText("Not found", status = HttpStatusCode.NotFound)
                } else {
                    call.response.header(HttpHeaders.CacheControl, "no-store")
                    call.respondBytes(page, ContentType.Text.Html.withCharset(Charsets.UTF_8))
                }
            }
            webSocket("/ws") { handleSocket(this) }
        }
    }

    private fun roomOk(call: ApplicationCall): Boolean =
        RoomCode.matches(session.roomCode, call.request.queryParameters["r"])

    private suspend fun forbidden(call: ApplicationCall) {
        call.respondText("Forbidden", status = HttpStatusCode.Forbidden)
    }

    private suspend fun handleSocket(ws: io.ktor.server.websocket.DefaultWebSocketServerSession) {
        var playerId: String? = null
        var token = 0
        var lastSentVersion = -1
        val pusher = ws.launch {
            session.snapshots.collect { snap ->
                val id = playerId ?: return@collect
                if (snap.version == lastSentVersion) return@collect
                PhoneViews.build(snap, id, session.clock.nowMs())?.let {
                    lastSentVersion = snap.version
                    ws.send(it)
                }
            }
        }
        try {
            for (frame in ws.incoming) {
                if (frame !is Frame.Text) continue
                val message = try {
                    WireJson.decodeFromString<ClientMessage>(frame.readText())
                } catch (_: Exception) {
                    continue
                }
                when (message) {
                    is Ping -> ws.send(Pong(message.n))
                    is Join -> {
                        if (playerId != null) continue
                        when (val outcome = session.join(message.name, message.playerId)) {
                            is JoinOutcome.No -> ws.send(Reject(outcome.reason.wire()))
                            is JoinOutcome.Ok -> {
                                playerId = outcome.seated.id
                                token = outcome.seated.token
                                ws.send(Welcome(outcome.seated.id, outcome.seated.name, PlayerColors.hex(outcome.seated.colorIndex)))
                                // Push the current view right away, whatever the version.
                                lastSentVersion = -1
                                PhoneViews.build(session.snapshots.value, outcome.seated.id, session.clock.nowMs())?.let {
                                    lastSentVersion = session.snapshots.value.version
                                    ws.send(it)
                                }
                            }
                        }
                    }
                    is Answer -> {
                        val id = playerId
                        if (id == null) {
                            ws.send(Ack(message.q, false, "closed"))
                        } else {
                            val result = session.answer(id, message.q, message.choice)
                            ws.send(Ack(message.q, result == AnswerResult.ACCEPTED, result.wire()))
                        }
                    }
                }
            }
        } catch (_: CancellationException) {
            // The socket closed; fall through to the cleanup below.
        } finally {
            pusher.cancel()
            playerId?.let { session.disconnect(it, token) }
        }
    }

    private suspend fun io.ktor.server.websocket.DefaultWebSocketServerSession.send(message: ServerMessage) {
        outgoing.send(Frame.Text(WireJson.encodeToString(ServerMessage.serializer(), message)))
    }

    private fun JoinRejection.wire() = when (this) {
        JoinRejection.FULL -> "full"
        JoinRejection.BAD_NAME -> "name"
    }

    private fun AnswerResult.wire() = when (this) {
        AnswerResult.ACCEPTED -> "ok"
        AnswerResult.TOO_LATE -> "late"
        AnswerResult.DUPLICATE -> "duplicate"
        AnswerResult.STALE -> "stale"
        AnswerResult.BAD_CHOICE -> "choice"
        AnswerResult.UNKNOWN_PLAYER, AnswerResult.NOT_ACCEPTING, AnswerResult.NOT_YOUR_TURN -> "closed"
    }
}
