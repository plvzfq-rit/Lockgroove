package io.github.plvzfqrit.plvinyl.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.plvzfqrit.plvinyl.core.settings.Settings
import io.github.plvzfqrit.plvinyl.ui.drawing.Look
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Remembers your choices between launches (on the phone only). */
class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { p ->
        val d = Settings()
        Settings(
            spin = enumOr(p[SPIN], d.spin),
            grooveGlints = p[GLINTS] ?: d.grooveGlints,
            motion = enumOr(p[MOTION], d.motion),
            keepScreenOn = p[SCREEN_ON] ?: d.keepScreenOn,
            haptics = p[HAPTICS] ?: d.haptics,
            repeatOne = p[REPEAT_ONE] ?: d.repeatOne,
            lighting = enumOr(p[LIGHTING], d.lighting),
            endMargin = enumOr(p[END_MARGIN], d.endMargin),
            recordSize = enumOr(p[RECORD_SIZE], d.recordSize),
            dustCover = p[DUST_COVER] ?: d.dustCover,
            surfaceNoise = enumOr(p[SURFACE_NOISE], d.surfaceNoise),
            recordWear = p[RECORD_WEAR] ?: d.recordWear,
            countInBackground = p[COUNT_BACKGROUND] ?: d.countInBackground,
            learnedCrossfades = (p[CROSSFADES] ?: emptySet()).mapNotNull { entry ->
                val pkg = entry.substringBeforeLast('=', "")
                val ms = entry.substringAfterLast('=').toLongOrNull()
                if (pkg.isNotEmpty() && ms != null) pkg to ms else null
            }.toMap(),
        )
    }

    val look: Flow<Look> = store.data.map { p ->
        Look(
            backdrop = enumOr(p[BACKDROP], Look().backdrop),
            finish = enumOr(p[FINISH], Look().finish),
            deckPaint = p[DECK_PAINT],
        )
    }

    suspend fun save(s: Settings) {
        store.edit { p ->
            p[SPIN] = s.spin.name
            p[GLINTS] = s.grooveGlints
            p[MOTION] = s.motion.name
            p[SCREEN_ON] = s.keepScreenOn
            p[HAPTICS] = s.haptics
            p[REPEAT_ONE] = s.repeatOne
            p[LIGHTING] = s.lighting.name
            p[END_MARGIN] = s.endMargin.name
            p[RECORD_SIZE] = s.recordSize.name
            p[DUST_COVER] = s.dustCover
            p[SURFACE_NOISE] = s.surfaceNoise.name
            p[RECORD_WEAR] = s.recordWear
            p[COUNT_BACKGROUND] = s.countInBackground
            p[CROSSFADES] = s.learnedCrossfades.map { "${it.key}=${it.value}" }.toSet()
        }
    }

    suspend fun save(look: Look) {
        store.edit { p ->
            p[BACKDROP] = look.backdrop.name
            p[FINISH] = look.finish.name
            look.deckPaint?.let { p[DECK_PAINT] = it } ?: p.remove(DECK_PAINT)
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: fallback

    private companion object {
        val BACKDROP = stringPreferencesKey("backdrop")
        val FINISH = stringPreferencesKey("finish")
        val DECK_PAINT = intPreferencesKey("deck_paint")
        val SPIN = stringPreferencesKey("spin")
        val GLINTS = booleanPreferencesKey("groove_glints")
        val MOTION = stringPreferencesKey("motion")
        val SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val HAPTICS = booleanPreferencesKey("haptics")
        val REPEAT_ONE = booleanPreferencesKey("repeat_one")
        val LIGHTING = stringPreferencesKey("lighting")
        val END_MARGIN = stringPreferencesKey("end_margin")
        val RECORD_SIZE = stringPreferencesKey("record_size")
        val DUST_COVER = booleanPreferencesKey("dust_cover")
        val SURFACE_NOISE = stringPreferencesKey("surface_noise")
        val RECORD_WEAR = booleanPreferencesKey("record_wear")
        val COUNT_BACKGROUND = booleanPreferencesKey("count_in_background")
        val CROSSFADES = stringSetPreferencesKey("crossfade_by_player")
    }
}
