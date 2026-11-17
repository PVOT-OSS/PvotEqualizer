package org.prauga.equalizer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject
import org.prauga.equalizer.core.model.EqBands
import org.prauga.equalizer.core.model.EqMode
import org.prauga.equalizer.core.model.EqProfile
import org.prauga.equalizer.core.model.Preset

/**
 * Where the curve, the power state and the presets live between launches.
 *
 * Stored in SharedPreferences rather than a database: the whole payload is a few hundred bytes, and
 * everything here is read once at startup and written a few times per minute at most. A malformed
 * payload is treated as "nothing saved" rather than crashing the app on launch.
 */
@Singleton
class EqStateStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Kept apart from the curve so backup can leave it out: a restored phone must not switch the
    // equalizer on by itself.
    private val powerPrefs: SharedPreferences =
        context.getSharedPreferences(POWER_PREFS_NAME, Context.MODE_PRIVATE)

    init {
        if (prefs.contains(KEY_POWER)) {
            val on = prefs.getBoolean(KEY_POWER, false)
            powerPrefs.edit { putBoolean(KEY_POWER, on) }
            prefs.edit { remove(KEY_POWER) }
        }
    }

    fun loadPowerOn(): Boolean = powerPrefs.getBoolean(KEY_POWER, false)

    fun savePowerOn(on: Boolean) {
        powerPrefs.edit { putBoolean(KEY_POWER, on) }
    }

    /** The curve saved for [mode], or a flat one. Each mode keeps its own. */
    fun loadProfile(mode: EqMode): EqProfile {
        val raw = prefs.getString(profileKey(mode), null) ?: return EqBands.profile(mode)
        return runCatching {
            val json = JSONObject(raw)
            val preamp = json.optDouble("preamp", 0.0)
            val gains = json.optJSONObject("gains")
            EqBands.profile(mode, preamp).let { flat ->
                flat.copy(
                    bands = flat.bands.map { band ->
                        band.copy(
                            gainDb = gains?.optDouble(band.frequencyHz.toString(), 0.0) ?: 0.0
                        )
                    }
                )
            }
        }.getOrElse { EqBands.profile(mode) }
    }

    fun saveProfile(profile: EqProfile, mode: EqMode) {
        val gains = JSONObject().apply {
            profile.bands.forEach { put(it.frequencyHz.toString(), it.gainDb) }
        }
        val json = JSONObject().apply {
            put("preamp", profile.preampDb)
            put("gains", gains)
        }
        prefs.edit { putString(profileKey(mode), json.toString()) }
    }

    fun loadPresets(): List<Preset> {
        val raw = prefs.getString(KEY_PRESETS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let(PresetJson::decode)
            }
        }.getOrElse { emptyList() }
    }

    fun savePresets(presets: List<Preset>) {
        val array = JSONArray().apply {
            presets.forEach { preset -> put(PresetJson.encode(preset)) }
        }
        prefs.edit { putString(KEY_PRESETS, array.toString()) }
    }

    private fun profileKey(mode: EqMode): String = "profile_${mode.name}"

    private companion object {
        const val PREFS_NAME = "eq_state"
        const val POWER_PREFS_NAME = "eq_power"
        const val KEY_POWER = "power_on"
        const val KEY_PRESETS = "presets"
    }
}
