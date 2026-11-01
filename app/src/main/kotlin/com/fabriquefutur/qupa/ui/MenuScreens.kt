package com.fabriquefutur.qupa.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabriquefutur.qupa.QupaViewModel
import com.fabriquefutur.qupa.R
import com.fabriquefutur.qupa.Screen
import com.fabriquefutur.qupa.UiState
import com.fabriquefutur.qupa.game.Categories
import com.fabriquefutur.qupa.game.Difficulty
import com.fabriquefutur.qupa.game.GameSettings
import com.fabriquefutur.qupa.game.Limits
import com.fabriquefutur.qupa.game.Mode

private fun <T> cycle(values: List<T>, current: T): T = values[(values.indexOf(current) + 1).mod(values.size)]

private val Difficulties: List<Difficulty?> = listOf(null, Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)

/** Requests focus on the returned requester once, when the screen appears. */
@Composable
fun rememberInitialFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    return requester
}

@Composable
fun HomeScreen(vm: QupaViewModel) {
    val first = rememberInitialFocus()
    Row(
        Modifier.fillMaxSize().padding(horizontal = SafeHorizontal, vertical = SafeVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.app_name), style = Type.Hero)
            Text(stringResource(R.string.tagline), style = Type.Muted, maxLines = 2)
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    stringResource(R.string.menu_phones),
                    { vm.startPlaying(Mode.PHONES) },
                    Modifier.width(380.dp).focusRequester(first),
                )
                SecondaryButton(stringResource(R.string.menu_remote), { vm.startPlaying(Mode.TEAMS) }, Modifier.width(380.dp))
                SecondaryButton(stringResource(R.string.menu_settings), { vm.open(Screen.SETTINGS) }, Modifier.width(380.dp))
                SecondaryButton(stringResource(R.string.menu_about), { vm.open(Screen.ABOUT) }, Modifier.width(380.dp))
            }
        }
        MarkArt(Modifier.weight(0.8f).height(320.dp))
    }
}

/** The four game settings as cycling chips. Used by Settings and both lobbies. */
@Composable
fun GameSettingChips(
    game: GameSettings,
    onChange: (GameSettings) -> Unit,
    onCategories: () -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
) {
    val seconds = stringResource(R.string.seconds_fmt)
    val categories = if (game.categories.isEmpty()) {
        stringResource(R.string.categories_all)
    } else {
        stringResource(R.string.categories_count, game.categories.size)
    }
    val chips: List<@Composable (Modifier) -> Unit> = listOf(
        { m ->
            SettingChip(
                stringResource(R.string.opt_questions), game.questions.toString(),
                { onChange(game.copy(questions = cycle(Limits.QUESTION_COUNTS, game.questions))) }, m,
            )
        },
        { m ->
            SettingChip(
                stringResource(R.string.opt_time), seconds.format(game.answerSeconds),
                { onChange(game.copy(answerSeconds = cycle(Limits.ANSWER_SECONDS, game.answerSeconds))) }, m,
            )
        },
        { m ->
            SettingChip(
                stringResource(R.string.opt_difficulty), difficultyName(game.difficulty),
                { onChange(game.copy(difficulty = cycle(Difficulties, game.difficulty))) }, m,
            )
        },
        { m -> SettingChip(stringResource(R.string.opt_categories), categories, onCategories, m) },
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        chips.chunked(columns).forEach { rowChips ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowChips.forEach { chip -> chip(Modifier.weight(1f)) }
                repeat(columns - rowChips.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: QupaViewModel, state: UiState) {
    val first = rememberInitialFocus()
    Column(
        Modifier.fillMaxSize().padding(horizontal = SafeHorizontal, vertical = SafeVertical),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = Type.Title)
        SettingChip(
            stringResource(R.string.settings_sound),
            stringResource(if (state.settings.sound) R.string.on else R.string.off),
            { vm.setSound(!state.settings.sound) },
            Modifier.width(420.dp).focusRequester(first),
        )
        Text(stringResource(R.string.settings_defaults), style = Type.BodyBold.copy(color = QupaColors.Muted))
        GameSettingChips(
            state.settings.game,
            onChange = vm::updateGame,
            onCategories = vm::openCategories,
            modifier = Modifier.width(620.dp),
        )
        Text(stringResource(R.string.settings_hint), style = Type.Label)
        Spacer(Modifier.weight(1f))
        SecondaryButton(stringResource(R.string.back), { vm.back() })
    }
}

@Composable
fun AboutScreen(vm: QupaViewModel) {
    val first = rememberInitialFocus()
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = SafeHorizontal, vertical = SafeVertical),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.about_title), style = Type.Title)
        Column(
            Modifier.weight(1f).width(760.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.about_how), style = Type.Body)
            Text(stringResource(R.string.about_attribution), style = Type.BodyBold)
            Text(stringResource(R.string.about_privacy), style = Type.Body)
            Text(stringResource(R.string.about_license), style = Type.Body)
            Text(stringResource(R.string.about_version, version), style = Type.Muted)
        }
        SecondaryButton(stringResource(R.string.back), { vm.back() }, Modifier.focusRequester(first))
    }
}

/** A category toggle: filled dot when picked. */
@Composable
private fun CategoryTile(name: String, picked: Boolean, onClick: () -> Unit) {
    FocusTile(
        onClick,
        Modifier.fillMaxWidth().height(56.dp),
        container = if (picked) QupaColors.CardHigh else QupaColors.Card,
        focusedContainer = QupaColors.Track,
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(18.dp).clip(CircleShape)
                    .background(if (picked) QupaColors.Ok else QupaColors.Background),
            )
            Text(
                name,
                style = Type.Body.copy(fontSize = 18.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
fun CategoriesScreen(vm: QupaViewModel, state: UiState) {
    val first = rememberInitialFocus()
    // Opened from a lobby, the game that is open owns the current setup.
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val live = snap?.settings
    val game = if (state.categoriesReturn == Screen.PLAY && live != null) live else state.settings.game
    val picked = game.categories
    val all = Categories.ALL.map { it.id }.toSet()

    Column(
        Modifier.fillMaxSize().padding(horizontal = SafeHorizontal, vertical = SafeVertical),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.categories_title), style = Type.Title, maxLines = 1)
                Text(stringResource(R.string.categories_hint), style = Type.Label, maxLines = 2)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(stringResource(R.string.done), { vm.back() }, Modifier.focusRequester(first))
            SecondaryButton(stringResource(R.string.categories_select_all), { vm.updateGame(game.copy(categories = all)) })
            SecondaryButton(stringResource(R.string.categories_clear), { vm.updateGame(game.copy(categories = emptySet())) })
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(Categories.ALL, key = { it.id }) { category ->
                val on = category.id in picked
                CategoryTile(categoryName(category.id), on) {
                    vm.updateGame(game.copy(categories = if (on) picked - category.id else picked + category.id))
                }
            }
        }
    }
}
