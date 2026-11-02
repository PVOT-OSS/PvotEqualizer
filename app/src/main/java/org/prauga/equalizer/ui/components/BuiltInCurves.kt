package org.prauga.equalizer.ui.components

import androidx.annotation.StringRes
import org.prauga.equalizer.R

/**
 * A curve that ships with the app.
 *
 * Gain is a function of frequency rather than a list of band values, so the same built-in fits the
 * six-band grid and the ten-band grid without being stored twice.
 */
data class BuiltInCurve(
    @StringRes val nameRes: Int,
    val preampDb: Double,
    val gainAt: (Double) -> Double
)

val BUILT_IN_CURVES: List<BuiltInCurve> = listOf(
    BuiltInCurve(R.string.preset_flat, 0.0) { 0.0 },
    BuiltInCurve(R.string.preset_bass, -3.0) { frequency ->
        when {
            frequency <= 60.0 -> 6.0
            frequency < 300.0 -> 6.0 * (300.0 - frequency) / 240.0
            else -> 0.0
        }
    },
    BuiltInCurve(R.string.preset_vocal, -2.0) { frequency ->
        when {
            frequency < 150.0 -> -3.0
            frequency < 800.0 -> -1.0
            frequency < 3_000.0 -> 4.0
            frequency < 6_000.0 -> 2.0
            frequency < 10_000.0 -> 0.0
            else -> -2.0
        }
    },
    BuiltInCurve(R.string.preset_treble, -2.0) { frequency ->
        when {
            frequency < 2_000.0 -> 0.0
            frequency < 8_000.0 -> 6.0 * (frequency - 2_000.0) / 6_000.0
            else -> 6.0
        }
    }
)
