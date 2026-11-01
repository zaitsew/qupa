package com.fabriquefutur.qupa.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fabriquefutur.qupa.bank.QuestionBank
import com.fabriquefutur.qupa.game.GameSettings
import com.fabriquefutur.qupa.game.Question
import com.fabriquefutur.qupa.game.SeenIds
import com.fabriquefutur.qupa.game.SettingsCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale

/** What Settings stores: the sound switch and the default game setup. */
data class AppSettings(val sound: Boolean = true, val game: GameSettings = GameSettings())

private val Context.settingsStore by preferencesDataStore(name = "settings")
private val Context.seenStore by preferencesDataStore(name = "seen")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore
    private val soundKey = booleanPreferencesKey("sound")
    private val questionsKey = intPreferencesKey("questions")
    private val secondsKey = intPreferencesKey("seconds")
    private val difficultyKey = stringPreferencesKey("difficulty")
    private val categoriesKey = stringPreferencesKey("categories")

    val settings: Flow<AppSettings> = store.data.map { p ->
        val defaults = GameSettings()
        AppSettings(
            sound = p[soundKey] ?: true,
            game = GameSettings(
                questions = p[questionsKey] ?: defaults.questions,
                categories = SettingsCodec.decodeCategories(p[categoriesKey]),
                difficulty = SettingsCodec.decodeDifficulty(p[difficultyKey]),
                answerSeconds = p[secondsKey] ?: defaults.answerSeconds,
            ).normalized(),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun saveSound(on: Boolean) {
        store.edit { it[soundKey] = on }
    }

    suspend fun saveGame(game: GameSettings) {
        val g = game.normalized()
        store.edit {
            it[questionsKey] = g.questions
            it[secondsKey] = g.answerSeconds
            it[difficultyKey] = SettingsCodec.encodeDifficulty(g.difficulty)
            it[categoriesKey] = SettingsCodec.encodeCategories(g.categories)
        }
    }
}

/** Ids of questions asked in earlier sessions. Stays on the device. */
class SeenRepository(context: Context) {
    private val store = context.applicationContext.seenStore
    private val key = stringSetPreferencesKey("ids")

    suspend fun load(): Set<String> = store.data.map { it[key] ?: emptySet() }.first()

    suspend fun add(fresh: Set<String>) {
        if (fresh.isEmpty()) return
        store.edit { it[key] = SeenIds.merge(it[key] ?: emptySet(), fresh) }
    }
}

/** Reads `questions/<language>.json` from the APK assets; English when the language has none. */
class QuestionRepository(private val context: Context) {
    suspend fun load(): List<Question> = withContext(Dispatchers.IO) {
        val assets = context.applicationContext.assets
        val present = assets.list("questions")?.toSet().orEmpty()
        QuestionBank.load(
            language = Locale.getDefault().language,
            exists = { path -> path.removePrefix("questions/") in present },
            read = { path -> assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() } },
        )
    }
}
