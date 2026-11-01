package com.fabriquefutur.qupa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.tv.material3.Text
import com.fabriquefutur.qupa.PlayState
import com.fabriquefutur.qupa.QupaViewModel
import com.fabriquefutur.qupa.R
import com.fabriquefutur.qupa.StartNotice
import com.fabriquefutur.qupa.game.GameSnapshot
import com.fabriquefutur.qupa.game.Limits
import com.fabriquefutur.qupa.game.Names
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** A QR code drawn from the zxing bit matrix on a white card with a quiet zone. */
@Composable
fun QrCode(text: String, description: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
    }
    Canvas(modifier.background(Color.White).semantics { contentDescription = description }) {
        val modules = matrix.width
        val quiet = 2
        val cell = size.minDimension / (modules + 2 * quiet)
        for (y in 0 until modules) for (x in 0 until modules) {
            if (matrix.get(x, y)) {
                drawRect(Color.Black, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.6f, cell + 0.6f))
            }
        }
    }
}

@Composable
private fun NoticeLine(play: PlayState, snap: GameSnapshot, available: Int) {
    val text = when {
        play.notice == StartNotice.NEED_PLAYER -> stringResource(R.string.lobby_need_player)
        play.notice == StartNotice.NO_QUESTIONS || available == 0 -> stringResource(R.string.no_questions)
        else -> stringResource(R.string.lobby_available, available)
    }
    val bad = play.notice != null || available == 0
    Text(text, style = Type.Label.copy(color = if (bad) QupaColors.Bad else QupaColors.Muted), maxLines = 2)
}

@Composable
fun PhoneLobby(vm: QupaViewModel, play: PlayState, snap: GameSnapshot) {
    val start = rememberInitialFocus()
    val url = play.joinUrl.orEmpty()
    val available = remember(snap.settings, snap.version) { vm.available(snap.settings) }
    Row(
        Modifier.fillMaxSize().padding(horizontal = SafeHorizontal, vertical = SafeVertical),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(Modifier.width(300.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.lobby_phones_title), style = Type.Metric, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            QrCode(url, stringResource(R.string.cd_qr), Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.height(8.dp))
            Text(url, style = Type.BodyBold.copy(fontFamily = FontFamily.Monospace), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.lobby_room, play.roomCode), style = Type.Muted)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.lobby_players, snap.participants.size, Limits.MAX_PLAYERS),
                style = Type.Title.copy(color = QupaColors.Text),
                maxLines = 1,
            )
            val slots = (0 until Limits.MAX_PLAYERS).map { snap.participants.getOrNull(it) }
            slots.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { p ->
                        Box(Modifier.weight(1f).height(40.dp)) {
                            if (p != null) {
                                PlayerChip(p.name, p.colorIndex, Modifier.fillMaxWidth(), dimmed = !p.connected)
                            } else {
                                Text(
                                    stringResource(R.string.lobby_slot_empty),
                                    style = Type.Label,
                                    maxLines = 1,
                                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
            GameSettingChips(snap.settings, vm::updateGame, vm::openCategories)
            NoticeLine(play, snap, available)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(stringResource(R.string.lobby_start), vm::startGame, Modifier.focusRequester(start))
                SecondaryButton(stringResource(R.string.back), { vm.back() })
            }
        }
    }
}

/** One team: colour dot and a name that turns into a text field on OK. */
@Composable
private fun TeamRow(id: String, name: String, colorIndex: Int, onRename: (String, String) -> Unit, requester: FocusRequester?) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(name) }
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldFocus = remember { FocusRequester() }

    fun commit() {
        Names.clean(draft)?.let { onRename(id, it) }
        editing = false
    }
    BackHandler(enabled = editing) { editing = false }
    LaunchedEffect(editing) {
        if (editing) {
            draft = name
            runCatching { fieldFocus.requestFocus() }
            keyboard?.show()
        }
    }
    FocusTile(
        onClick = { editing = true },
        modifier = Modifier.fillMaxWidth().height(56.dp).let { if (requester != null) it.focusRequester(requester) else it },
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(QupaColors.player(colorIndex)))
            if (editing) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.take(Limits.MAX_NAME) },
                    singleLine = true,
                    textStyle = Type.BodyBold,
                    cursorBrush = SolidColor(QupaColors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier.padding(start = 12.dp).fillMaxWidth().focusRequester(fieldFocus),
                )
            } else {
                Text(
                    name,
                    style = Type.BodyBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
fun TeamLobby(vm: QupaViewModel, play: PlayState, snap: GameSnapshot) {
    val start = rememberInitialFocus()
    val available = remember(snap.settings, snap.version) { vm.available(snap.settings) }
    Row(
        Modifier.fillMaxSize().padding(horizontal = SafeHorizontal, vertical = SafeVertical),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.team_title), style = Type.Title, maxLines = 1)
            Text(stringResource(R.string.team_hint), style = Type.Label, maxLines = 3)
            snap.participants.forEach { team ->
                TeamRow(team.id, team.name, team.colorIndex, vm::renameTeam, null)
            }
        }
        Column(Modifier.width(400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingChip(
                stringResource(R.string.team_count), snap.participants.size.toString(),
                {
                    val next = if (snap.participants.size >= Limits.MAX_TEAMS) Limits.MIN_TEAMS else snap.participants.size + 1
                    vm.setTeamCount(next)
                },
                Modifier.fillMaxWidth(),
            )
            GameSettingChips(snap.settings, vm::updateGame, vm::openCategories)
            NoticeLine(play, snap, available)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(stringResource(R.string.lobby_start), vm::startGame, Modifier.focusRequester(start))
                SecondaryButton(stringResource(R.string.back), { vm.back() })
            }
        }
    }
}
