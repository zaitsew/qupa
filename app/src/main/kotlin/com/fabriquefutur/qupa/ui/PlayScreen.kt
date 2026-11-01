package com.fabriquefutur.qupa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.fabriquefutur.qupa.PlayProblem
import com.fabriquefutur.qupa.QupaViewModel
import com.fabriquefutur.qupa.R
import com.fabriquefutur.qupa.UiState
import com.fabriquefutur.qupa.game.Mode
import com.fabriquefutur.qupa.game.Phase

@Composable
fun PlayScreen(vm: QupaViewModel, state: UiState) {
    val play = state.play ?: return
    val snap by vm.snapshot.collectAsStateWithLifecycle()

    // While the exit question is open only it takes focus; the game goes on behind it.
    if (state.confirmExit) {
        ExitConfirm(vm)
        return
    }
    val problem = play.problem
    val current = snap
    when {
        problem != null -> ProblemScreen(vm, problem)
        play.loading || current == null -> MessageScreen(stringResource(R.string.lobby_loading))
        else -> when (current.phase) {
            Phase.LOBBY -> if (play.mode == Mode.PHONES) PhoneLobby(vm, play, current) else TeamLobby(vm, play, current)
            Phase.QUESTION -> QuestionScreen(vm, current)
            Phase.REVEAL -> RevealScreen(vm, current)
            Phase.SCOREBOARD -> ScoreboardScreen(vm, current)
            Phase.PODIUM -> PodiumScreen(vm, current)
        }
    }
}

@Composable
private fun MessageScreen(text: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = SafeHorizontal, vertical = SafeVertical),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = Type.Title, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProblemScreen(vm: QupaViewModel, problem: PlayProblem) {
    val first = rememberInitialFocus()
    val text = when (problem) {
        PlayProblem.NO_NETWORK -> R.string.lobby_no_network
        PlayProblem.SERVER -> R.string.lobby_server_failed
        PlayProblem.NO_QUESTIONS -> R.string.no_questions
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = SafeHorizontal, vertical = SafeVertical),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(text), style = Type.Metric.copy(fontSize = 24.sp), textAlign = TextAlign.Center, modifier = Modifier.width(640.dp))
        PrimaryButton(stringResource(R.string.back), { vm.stopPlaying() }, Modifier.focusRequester(first))
    }
}

@Composable
private fun ExitConfirm(vm: QupaViewModel) {
    val keep = rememberInitialFocus()
    Column(
        Modifier.fillMaxSize().padding(horizontal = SafeHorizontal, vertical = SafeVertical),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.exit_title), style = Type.Title)
        Text(stringResource(R.string.exit_message), style = Type.Muted, textAlign = TextAlign.Center)
        PrimaryButton(stringResource(R.string.exit_keep), vm::keepPlaying, Modifier.focusRequester(keep))
        SecondaryButton(stringResource(R.string.exit_end), vm::stopPlaying)
    }
}
