package io.github.plvzfqrit.plvinyl.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
            // Before the sliders these were fixed choices, saved by name: read those once.
            spin = p[SPIN_SPEED] ?: p[OLD_SPIN]?.let { OLD_SPINS[it] } ?: d.spin,
            grooveGlints = p[GLINTS] ?: d.grooveGlints,
            motion = enumOr(p[MOTION], d.motion),
            keepScreenOn = p[SCREEN_ON] ?: d.keepScreenOn,
            haptics = p[HAPTICS] ?: d.haptics,
            repeatOne = p[REPEAT_ONE] ?: d.repeatOne,
            lighting = enumOr(p[LIGHTING], d.lighting),
            sunTime = p[SUN_TIME],
            endMarginMs = p[END_MARGIN_MS] ?: p[OLD_END_MARGIN]?.let { OLD_END_MARGINS[it] } ?: d.endMarginMs,
            recordSize = enumOr(p[RECORD_SIZE], d.recordSize),
            dustCover = p[DUST_COVER] ?: d.dustCover,
            surfaceNoise = p[NOISE_LEVEL] ?: p[OLD_NOISE]?.let { OLD_NOISES[it] } ?: d.surfaceNoise,
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
            panelPaint = p[PANEL_PAINT],
        )
    }

    suspend fun save(s: Settings) {
        store.edit { p ->
            p[SPIN_SPEED] = s.spin
            p[GLINTS] = s.grooveGlints
            p[MOTION] = s.motion.name
            p[SCREEN_ON] = s.keepScreenOn
            p[HAPTICS] = s.haptics
            p[REPEAT_ONE] = s.repeatOne
            p[LIGHTING] = s.lighting.name
            s.sunTime?.let { p[SUN_TIME] = it } ?: p.remove(SUN_TIME)
            p[END_MARGIN_MS] = s.endMarginMs
            p[RECORD_SIZE] = s.recordSize.name
            p[DUST_COVER] = s.dustCover
            p[NOISE_LEVEL] = s.surfaceNoise
            p[RECORD_WEAR] = s.recordWear
            p[COUNT_BACKGROUND] = s.countInBackground
            p[CROSSFADES] = s.learnedCrossfades.map { "${it.key}=${it.value}" }.toSet()
            p.remove(OLD_SPIN); p.remove(OLD_END_MARGIN); p.remove(OLD_NOISE)
        }
    }

    suspend fun save(look: Look) {
        store.edit { p ->
            p[BACKDROP] = look.backdrop.name
            p[FINISH] = look.finish.name
            look.deckPaint?.let { p[DECK_PAINT] = it } ?: p.remove(DECK_PAINT)
            look.panelPaint?.let { p[PANEL_PAINT] = it } ?: p.remove(PANEL_PAINT)
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: fallback

    private companion object {
        val BACKDROP = stringPreferencesKey("backdrop")
        val FINISH = stringPreferencesKey("finish")
        val DECK_PAINT = intPreferencesKey("deck_paint")
        val PANEL_PAINT = intPreferencesKey("panel_paint")
        val SPIN_SPEED = floatPreferencesKey("spin_deg_per_sec")
        val GLINTS = booleanPreferencesKey("groove_glints")
        val MOTION = stringPreferencesKey("motion")
        val SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val HAPTICS = booleanPreferencesKey("haptics")
        val REPEAT_ONE = booleanPreferencesKey("repeat_one")
        val LIGHTING = stringPreferencesKey("lighting")
        val SUN_TIME = intPreferencesKey("sun_time")
        val END_MARGIN_MS = longPreferencesKey("end_margin_ms")
        val RECORD_SIZE = stringPreferencesKey("record_size")
        val DUST_COVER = booleanPreferencesKey("dust_cover")
        val NOISE_LEVEL = floatPreferencesKey("surface_noise_level")
        val RECORD_WEAR = booleanPreferencesKey("record_wear")
        val COUNT_BACKGROUND = booleanPreferencesKey("count_in_background")
        val CROSSFADES = stringSetPreferencesKey("crossfade_by_player")

        // The fixed choices from before the sliders, and what they were.
        val OLD_SPIN = stringPreferencesKey("spin")
        val OLD_END_MARGIN = stringPreferencesKey("end_margin")
        val OLD_NOISE = stringPreferencesKey("surface_noise")
        val OLD_SPINS = mapOf("Slow" to 30f, "Medium" to 60f, "Real" to 200f)
        val OLD_END_MARGINS = mapOf("Short" to 500L, "Normal" to 1000L, "Long" to 2000L)
        val OLD_NOISES = mapOf("Off" to 0f, "Light" to 0.55f, "Heavy" to 1f)
    }
}
