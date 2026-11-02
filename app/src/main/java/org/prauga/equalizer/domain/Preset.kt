package org.prauga.equalizer.domain

import java.util.UUID

/**
 * A saved curve: a name, a preamp, and the gain of each frequency the user set.
 *
 * Gains are keyed by frequency rather than by band index, so a preset survives a change of mode and
 * can be applied to whichever band grid is current.
 */
data class Preset(
    val id: String,
    val name: String,
    val mode: EqMode,
    val preampDb: Double,
    val gains: Map<Double, Double>
) {

    /** This preset as an editable profile on its own band grid. */
    fun toProfile(): EqProfile = EqBands.profile(
        mode,
        preampDb.coerceIn(EqProfile.MIN_PREAMP_DB, EqProfile.MAX_PREAMP_DB)
    ).let { flat ->
        flat.copy(
            bands = flat.bands.map { band ->
                band.copy(gainDb = gainFor(band.frequencyHz))
            }
        )
    }

    /** The gain this preset wants for [frequencyHz], taking the nearest saved frequency. */
    fun gainFor(frequencyHz: Double): Double {
        val exact = gains[frequencyHz]
        if (exact != null) return exact
        val nearest = gains.minByOrNull { kotlin.math.abs(it.key - frequencyHz) } ?: return 0.0
        val closeEnough = nearest.key / frequencyHz in 0.8..1.25
        return if (closeEnough) nearest.value else 0.0
    }

    companion object {
        fun from(name: String, profile: EqProfile, mode: EqMode): Preset = Preset(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            mode = mode,
            preampDb = profile.preampDb,
            gains = profile.bands.filter { it.gainDb != 0.0 }
                .associate { it.frequencyHz to it.gainDb }
        )
    }
}
