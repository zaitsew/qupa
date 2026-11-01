package com.fabriquefutur.qupa

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabriquefutur.qupa.audio.SoundFx
import com.fabriquefutur.qupa.bank.QuestionBank
import com.fabriquefutur.qupa.data.AppSettings
import com.fabriquefutur.qupa.data.QuestionRepository
import com.fabriquefutur.qupa.data.SeenRepository
import com.fabriquefutur.qupa.data.SettingsRepository
import com.fabriquefutur.qupa.game.Clock
import com.fabriquefutur.qupa.game.GameSession
import com.fabriquefutur.qupa.game.GameSettings
import com.fabriquefutur.qupa.game.GameSnapshot
import com.fabriquefutur.qupa.game.LanAddress
import com.fabriquefutur.qupa.game.Limits
import com.fabriquefutur.qupa.game.Mode
import com.fabriquefutur.qupa.game.Phase
import com.fabriquefutur.qupa.game.Question
import com.fabriquefutur.qupa.game.RoomCode
import com.fabriquefutur.qupa.game.StartResult
import com.fabriquefutur.qupa.net.AssetsWeb
import com.fabriquefutur.qupa.net.NetworkAddress
import com.fabriquefutur.qupa.server.QuizServer
import com.fabriquefutur.qupa.sound.Effect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

enum class Screen { HOME, SETTINGS, ABOUT, CATEGORIES, PLAY }

/** Why the lobby cannot run. */
enum class PlayProblem { NO_NETWORK, SERVER, NO_QUESTIONS }

/** A message under the Start button. */
enum class StartNotice { NEED_PLAYER, NO_QUESTIONS }

data class PlayState(
    val mode: Mode,
    val loading: Boolean = true,
    val problem: PlayProblem? = null,
    val joinUrl: String? = null,
    val roomCode: String = "",
    val notice: StartNotice? = null,
)

data class UiState(
    val screen: Screen = Screen.HOME,
    /** Where the category picker returns to: Settings, or the lobby it was opened from. */
    val categoriesReturn: Screen = Screen.SETTINGS,
    val settings: AppSettings = AppSettings(),
    val play: PlayState? = null,
    val confirmExit: Boolean = false,
)

/**
 * Holds navigation, the saved settings and the running game. The game exists
 * only between [startPlaying] and [stopPlaying]; the web server lives exactly as
 * long, so nothing listens on the network while the TV shows the home screen.
 */
class QupaViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsRepo = SettingsRepository(app)
    private val seenRepo = SeenRepository(app)
    private val questionRepo = QuestionRepository(app)
    private val network = NetworkAddress(app)

    val sound = SoundFx()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val _snapshot = MutableStateFlow<GameSnapshot?>(null)
    val snapshot: StateFlow<GameSnapshot?> = _snapshot

    private var session: GameSession? = null
    private var server: QuizServer? = null
    private var playScope: CoroutineScope? = null
    private var bank: List<Question> = emptyList()

    init {
        viewModelScope.launch {
            settingsRepo.settings.collect { s ->
                sound.enabled = s.sound
                _state.update { it.copy(settings = s) }
            }
        }
    }

    // ---- navigation ----

    fun open(screen: Screen) = _state.update { it.copy(screen = screen) }

    fun openCategories() = _state.update {
        it.copy(screen = Screen.CATEGORIES, categoriesReturn = if (it.play != null) Screen.PLAY else Screen.SETTINGS)
    }

    /** Handles Back. Returns false on the home screen, where the system leaves the app. */
    fun back(): Boolean {
        val s = _state.value
        return when {
            s.confirmExit -> { _state.update { it.copy(confirmExit = false) }; true }
            s.screen == Screen.HOME -> false
            s.screen == Screen.CATEGORIES -> { _state.update { it.copy(screen = it.categoriesReturn) }; true }
            s.screen == Screen.PLAY -> { leaveOrConfirm(); true }
            else -> { _state.update { it.copy(screen = Screen.HOME) }; true }
        }
    }

    /** Mid-game, Back asks first; in the lobby, on the podium or on an error it leaves at once. */
    private fun leaveOrConfirm() {
        val phase = _snapshot.value?.phase
        val midGame = phase == Phase.QUESTION || phase == Phase.REVEAL || phase == Phase.SCOREBOARD
        if (midGame) _state.update { it.copy(confirmExit = true) } else stopPlaying()
    }

    fun keepPlaying() = _state.update { it.copy(confirmExit = false) }

    // ---- settings ----

    fun setSound(on: Boolean) {
        sound.enabled = on
        _state.update { it.copy(settings = it.settings.copy(sound = on)) }
        viewModelScope.launch { settingsRepo.saveSound(on) }
    }

    /** Saves [game] as the default and, in a lobby, applies it to the game that is open. */
    fun updateGame(game: GameSettings) {
        val g = game.normalized()
        session?.updateSettings(g)
        _state.update { it.copy(settings = it.settings.copy(game = g)) }
        viewModelScope.launch { settingsRepo.saveGame(g) }
    }

    /** How many questions the filters leave, for the lobby. */
    fun available(settings: GameSettings): Int = QuestionBank.available(bank, settings)

    // ---- the game ----

    fun startPlaying(mode: Mode) {
        if (_state.value.play != null) return
        _state.update { it.copy(screen = Screen.PLAY, play = PlayState(mode), confirmExit = false) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        playScope = scope
        scope.launch {
            try {
                prepare(mode, scope)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                fail(PlayProblem.SERVER)
            }
        }
    }

    private suspend fun prepare(mode: Mode, scope: CoroutineScope) {
        val loaded = questionRepo.load()
        val saved = settingsRepo.current()
        val seen = seenRepo.load()
        if (!scope.isActive) return
        bank = loaded
        if (loaded.isEmpty()) {
            fail(PlayProblem.NO_QUESTIONS)
            return
        }
        val room = RoomCode.generate()
        val game = GameSession(mode, Clock.System, loaded, scope, room, seen)
        game.updateSettings(saved.game)
        var url: String? = null
        if (mode == Mode.TEAMS) {
            repeat(Limits.MIN_TEAMS) { n -> game.addTeam(teamName(n + 1)) }
        } else {
            val host = network.find()
            if (host == null) {
                fail(PlayProblem.NO_NETWORK)
                return
            }
            val srv = QuizServer(game, AssetsWeb(getApplication()))
            try {
                // Not cancellable: leaving the lobby now must not abandon a server that is coming up.
                val port = withContext(NonCancellable + Dispatchers.IO) { srv.start() }
                url = LanAddress.joinUrl(host, port, room)
            } catch (_: Exception) {
                fail(PlayProblem.SERVER)
                return
            }
            server = srv
            // The lobby may have been left while the server was starting.
            if (!scope.isActive) {
                withContext(NonCancellable + Dispatchers.IO) { srv.stop() }
                if (server === srv) server = null
                return
            }
        }
        session = game
        _snapshot.value = null
        _state.update { it.copy(play = it.play?.copy(loading = false, joinUrl = url, roomCode = room)) }
        scope.launch { game.snapshots.collect { onSnapshot(it) } }
        scope.launch { tickSounds() }
    }

    private fun fail(problem: PlayProblem) {
        _state.update { it.copy(play = it.play?.copy(loading = false, problem = problem)) }
    }

    private var lastPhase: Phase? = null

    private fun onSnapshot(snap: GameSnapshot) {
        _snapshot.value = snap
        if (snap.phase != lastPhase) {
            lastPhase = snap.phase
            val q = snap.question
            if (snap.phase == Phase.REVEAL && q != null) {
                val anyRight = snap.participants.any { it.choice == q.correctIndex }
                sound.play(if (anyRight) Effect.CORRECT else Effect.WRONG)
            }
        }
        // A new lobby result clears a stale "waiting for a player" notice.
        if (snap.phase != Phase.LOBBY) {
            _state.update { it.copy(play = it.play?.copy(notice = null)) }
        }
    }

    /** A tick for each of the last five seconds of a question. */
    private suspend fun tickSounds() {
        var lastSecond = -1
        while (true) {
            delay(100)
            val snap = _snapshot.value
            if (snap?.phase != Phase.QUESTION) {
                lastSecond = -1
                continue
            }
            val left = ceil((snap.deadlineMs - Clock.System.nowMs()) / 1000.0).toInt()
            if (left in 1..5 && left != lastSecond) {
                lastSecond = left
                sound.play(Effect.TICK)
            }
        }
    }

    fun startGame() {
        val notice = when (session?.start()) {
            StartResult.NOT_ENOUGH_PLAYERS -> StartNotice.NEED_PLAYER
            StartResult.NO_QUESTIONS -> StartNotice.NO_QUESTIONS
            else -> null
        }
        _state.update { it.copy(play = it.play?.copy(notice = notice)) }
    }

    fun advance() { session?.advance() }

    fun playAgain() { session?.playAgain() }

    fun answerFromTv(teamId: String, choice: Int) { session?.answerFromTv(teamId, choice) }

    fun setTeamCount(count: Int) {
        val game = session ?: return
        val teams = _snapshot.value?.participants.orEmpty()
        val target = count.coerceIn(Limits.MIN_TEAMS, Limits.MAX_TEAMS)
        if (teams.size < target) {
            for (n in teams.size + 1..target) game.addTeam(teamName(n))
        } else {
            teams.drop(target).forEach { game.removeSeat(it.id) }
        }
    }

    fun renameTeam(id: String, name: String) { session?.rename(id, name) }

    private fun teamName(n: Int): String = getApplication<Application>().getString(R.string.team_default, n)

    /** Stops the server, remembers which questions were asked and returns home. */
    fun stopPlaying() {
        val game = session
        val srv = server
        val scope = playScope
        session = null
        server = null
        playScope = null
        lastPhase = null
        val asked = game?.askedIds().orEmpty()
        scope?.cancel()
        _snapshot.value = null
        _state.update { it.copy(play = null, screen = Screen.HOME, confirmExit = false) }
        if (srv != null) viewModelScope.launch(Dispatchers.IO) { srv.stop() }
        if (asked.isNotEmpty()) viewModelScope.launch { seenRepo.add(asked) }
    }

    /** The app left the screen: nothing may keep listening on the network. */
    fun onAppStopped() {
        if (_state.value.play != null) stopPlaying()
    }

    override fun onCleared() {
        playScope?.cancel()
        server?.let { srv -> Thread { runCatching { srv.stop() } }.start() }
        sound.release()
    }
}
