package com.fabriquefutur.qupa.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.fabriquefutur.qupa.QupaViewModel
import com.fabriquefutur.qupa.R
import com.fabriquefutur.qupa.game.Clock
import com.fabriquefutur.qupa.game.GameSnapshot
import com.fabriquefutur.qupa.game.Mode
import com.fabriquefutur.qupa.game.ParticipantInfo
import com.fabriquefutur.qupa.game.QuestionInfo
import com.fabriquefutur.qupa.game.QuestionType
import kotlinx.coroutines.delay
import kotlin.math.ceil

private val AnswerInk = QupaColors.Ink
private val AnswerStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AnswerInk)

@Composable
private fun answerText(q: QuestionInfo, index: Int): String = when (q.type) {
    QuestionType.BOOLEAN -> stringResource(if (index == 0) R.string.true_label else R.string.false_label)
    QuestionType.MULTIPLE -> q.answers[index]
}

/** The countdown number and bar. Ticks by itself so the rest of the screen does not recompose. */
@Composable
private fun Countdown(deadlineMs: Long, answerMs: Long, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(Clock.System.nowMs()) }
    LaunchedEffect(deadlineMs) {
        while (true) {
            now = Clock.System.nowMs()
            delay(50)
        }
    }
    val left = (deadlineMs - now).coerceIn(0, answerMs)
    val fraction = if (answerMs <= 0) 0f else left.toFloat() / answerMs
    val urgent = left <= 5_000
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BarFill(fraction, if (urgent) QupaColors.Bad else QupaColors.Accent, Modifier.weight(1f), height = 12.dp)
        Text(
            ceil(left / 1000.0).toInt().toString(),
            style = Type.Metric.copy(fontSize = 40.sp, color = if (urgent) QupaColors.Bad else QupaColors.Text),
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.End,
        )
    }
}

/** One answer: colour, shape, letter and text. Dimmed when it was wrong at reveal. */
@Composable
private fun AnswerPanel(
    index: Int,
    text: String,
    modifier: Modifier,
    dim: Boolean = false,
    mark: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val color = QupaColors.Answer[index]
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            AnswerGlyph(index, AnswerInk, Modifier.size(30.dp))
            Text(
                "ABCD"[index].toString(),
                style = AnswerStyle.copy(fontSize = 30.sp),
                modifier = Modifier.padding(start = 12.dp).width(28.dp),
            )
            Text(text, style = AnswerStyle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (mark) Text("✓", style = AnswerStyle.copy(fontSize = 36.sp))
        }
    }
    if (onClick != null) {
        FocusTile(onClick, modifier.alpha(if (dim) 0.35f else 1f), container = color, focusedContainer = color) { content() }
    } else {
        Box(
            modifier.alpha(if (dim) 0.35f else 1f).clip(RoundedCornerShape(16.dp)).background(color)
                .border(if (mark) 4.dp else 0.dp, if (mark) QupaColors.Focus else Color.Transparent, RoundedCornerShape(16.dp)),
        ) { content() }
    }
}

@Composable
private fun QuestionHeader(snap: GameSnapshot, q: QuestionInfo, right: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.game_question_of, q.index + 1, q.total), style = Type.BodyBold, maxLines = 1)
        Text(
            categoryName(q.categoryId) + " · " + difficultyName(q.difficulty),
            style = Type.Label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        right()
    }
}

@Composable
private fun AnswerGrid(
    q: QuestionInfo,
    correctIndex: Int?,
    onPick: ((Int) -> Unit)?,
    firstFocus: androidx.compose.ui.focus.FocusRequester? = null,
    picked: Int = -1,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        q.answers.indices.chunked(2).forEach { rowIdx ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowIdx.forEach { i ->
                    val reveal = correctIndex != null
                    var m = Modifier.weight(1f).height(88.dp)
                    if (i == 0 && firstFocus != null) m = m.focusRequester(firstFocus)
                    AnswerPanel(
                        index = i,
                        text = answerText(q, i),
                        modifier = m,
                        dim = reveal && i != correctIndex,
                        mark = (reveal && i == correctIndex) || (!reveal && picked == i),
                        onClick = if (onPick != null) ({ onPick(i) }) else null,
                    )
                }
                if (rowIdx.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun QuestionScreen(vm: QupaViewModel, snap: GameSnapshot) {
    val q = snap.question ?: return
    val teams = snap.mode == Mode.TEAMS
    val focus = rememberInitialFocus()
    var picked by remember(q.index) { mutableStateOf(-1) }
    val active = snap.participants.firstOrNull { it.id == snap.activeId }
    val connected = snap.participants.count { it.connected }
    val answered = snap.participants.count { it.answered }

    Column(
        Modifier.fillMaxSize().padding(horizontal = SafeHorizontal, vertical = SafeVertical),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuestionHeader(snap, q)
        Countdown(snap.deadlineMs, snap.answerMs)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
            Text(q.text, style = Type.Question, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        AnswerGrid(
            q, correctIndex = null,
            onPick = if (teams && active != null) ({ i ->
                if (picked < 0) {
                    picked = i
                    vm.answerFromTv(active.id, i)
                }
            }) else null,
            firstFocus = if (teams) focus else null,
            picked = picked,
        )
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (teams && active != null) {
                PlayerChip(stringResource(R.string.game_your_turn, active.name), active.colorIndex, Modifier.weight(1f))
            } else {
                Text(
                    stringResource(R.string.game_answered, answered, connected),
                    style = Type.BodyBold.copy(color = QupaColors.Muted),
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(stringResource(R.string.game_reveal_now), vm::advance, Modifier.focusRequester(focus))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RevealScreen(vm: QupaViewModel, snap: GameSnapshot) {
    val q = snap.question ?: return
    val focus = rememberInitialFocus()
    val right = snap.participants.filter { it.choice == q.correctIndex }.sortedByDescending { it.gained }
    val anyAnswered = snap.participants.any { it.choice >= 0 }
    Column(
        Modifier.fillMaxSize().padding(horizontal = SafeHorizontal, vertical = SafeVertical),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuestionHeader(snap, q)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
            Text(q.text, style = Type.Question, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        AnswerGrid(q, correctIndex = q.correctIndex, onPick = null)
        Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val headline = when {
                    right.isNotEmpty() -> R.string.game_got_it
                    anyAnswered -> R.string.game_nobody
                    else -> R.string.game_time_up
                }
                Text(stringResource(headline), style = Type.Label)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxLines = 2) {
                    right.forEach { p -> PlayerChip(p.name, p.colorIndex, trailing = "+${p.gained}") }
                }
            }
            PrimaryButton(stringResource(R.string.game_next), vm::advance, Modifier.focusRequester(focus))
        }
    }
}

@Composable
fun ScoreboardScreen(vm: QupaViewModel, snap: GameSnapshot) {
    val q = snap.question
    val focus = rememberInitialFocus()
    Column(
        Modifier.fillMaxSize().padding(horizontal = SafeHorizontal, vertical = SafeVertical),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.scoreboard_title), style = Type.Title, maxLines = 1)
                if (q != null) Text(stringResource(R.string.scoreboard_after, q.index + 1, q.total), style = Type.Muted, maxLines = 1)
            }
            PrimaryButton(stringResource(R.string.game_next), vm::advance, Modifier.focusRequester(focus))
        }
        Column(Modifier.width(680.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            snap.ranked.forEach { p -> ScoreRow(p) }
        }
    }
}

@Composable
private fun ScoreRow(p: ParticipantInfo) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(QupaColors.Card).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(p.rank.toString(), style = Type.BodyBold.copy(color = QupaColors.Muted), modifier = Modifier.width(36.dp))
        Box(Modifier.size(20.dp).clip(CircleShape).background(QupaColors.player(p.colorIndex).copy(alpha = if (p.connected) 1f else 0.35f)))
        Text(p.name, style = Type.BodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
        if (p.gained > 0) Text("+${p.gained}", style = Type.BodyBold.copy(color = QupaColors.Ok), modifier = Modifier.padding(end = 16.dp))
        Text(p.score.toString(), style = Type.BodyBold, modifier = Modifier.width(88.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun PodiumBlock(p: ParticipantInfo, height: Int, modifier: Modifier = Modifier) {
    var target by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { target = 1f }
    val progress by animateFloatAsState(target, tween(600), label = "podium")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(p.name, style = Type.BodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(p.score.toString(), style = Type.Label)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height((height * progress).dp)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(QupaColors.player(p.colorIndex)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                p.rank.toString(),
                style = Type.Hero.copy(fontSize = 48.sp, color = QupaColors.Ink),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun PodiumScreen(vm: QupaViewModel, snap: GameSnapshot) {
    val focus = rememberInitialFocus()
    val ranked = snap.ranked
    val winners = ranked.filter { it.rank == 1 }.joinToString(", ") { it.name }
    val top = ranked.take(3)
    val rest = ranked.drop(3)
    // Podium order: second, first, third.
    val order = listOfNotNull(top.getOrNull(1), top.getOrNull(0), top.getOrNull(2))
    Column(
        Modifier.fillMaxSize().padding(horizontal = SafeHorizontal, vertical = SafeVertical),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.podium_wins, winners), style = Type.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
            order.forEach { p ->
                val h = when (p.rank) { 1 -> 190; 2 -> 140; else -> 100 }
                PodiumBlock(p, h, Modifier.width(200.dp))
            }
        }
        if (rest.isNotEmpty()) {
            Text(
                rest.joinToString("   ") { "${it.rank}. ${it.name} ${it.score}" },
                style = Type.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PrimaryButton(stringResource(R.string.play_again), vm::playAgain, Modifier.focusRequester(focus))
            SecondaryButton(stringResource(R.string.home), vm::stopPlaying)
        }
    }
}
