package org.prauga.equalizer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.prauga.pvot.designsystem.effects.PvotGlassDefaults
import org.prauga.equalizer.domain.EqMode

/** The app's settings, kept in SharedPreferences and observable from Compose. */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Liquid glass at full strength when on, the lighter frosted look when off. */
    var liquidGlass by mutableStateOf(
        prefs.getBoolean(KEY_LIQUID_GLASS, PvotGlassDefaults.defaultIntensity() > 0f)
    )
        private set

    fun updateLiquidGlass(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LIQUID_GLASS, enabled) }
        liquidGlass = enabled
    }

    /** The value for [com.prauga.pvot.designsystem.effects.LocalPvotGlassIntensity]. */
    val glassIntensity: Float
        get() = if (liquidGlass) 1f else 0f

    /** Whether the editor offers up to ten bands instead of six. */
    var advancedMode by mutableStateOf(prefs.getBoolean(KEY_ADVANCED_MODE, false))
        private set

    fun updateAdvancedMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ADVANCED_MODE, enabled) }
        advancedMode = enabled
    }

    /** The editor's band allowance, derived from [advancedMode]. */
    val mode: EqMode
        get() = if (advancedMode) EqMode.ADVANCED else EqMode.SIMPLE

    private companion object {
        const val PREFS_NAME = "equalizer_preferences"
        const val KEY_LIQUID_GLASS = "liquid_glass"
        const val KEY_ADVANCED_MODE = "advanced_mode"
    }
}
