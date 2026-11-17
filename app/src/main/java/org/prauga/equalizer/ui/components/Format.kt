package org.prauga.equalizer.ui.components

import java.util.Locale
import kotlin.math.abs

/**
 * One decimal with an explicit sign. Values a rounding error away from zero print as 0.0 rather
 * than -0.0, which reads as a bug on a control the user just reset.
 */
internal fun formatDb(db: Double): String {
    val normalized = if (abs(db) < 0.05) 0.0 else db
    return String.format(Locale.US, "%+.1f", normalized)
}

internal fun formatFrequency(hz: Double): String {
    val kilo = hz >= 1_000.0
    val value = if (kilo) hz / 1_000.0 else hz
    val number = if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
    return if (kilo) "$number kHz" else "$number Hz"
}
