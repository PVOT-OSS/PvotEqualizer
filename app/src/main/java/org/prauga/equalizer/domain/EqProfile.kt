package org.prauga.equalizer.domain

/** The shape of a [ParametricBand]. */
enum class BandType {
    /** A bell: boosts or cuts around [ParametricBand.frequencyHz]. */
    PEAK,

    /** Lifts or drops everything below [ParametricBand.frequencyHz]. */
    LOW_SHELF,

    /** Lifts or drops everything above [ParametricBand.frequencyHz]. */
    HIGH_SHELF,

    /** A high-pass: removes everything below [ParametricBand.frequencyHz]. */
    LOW_CUT,

    /** A low-pass: removes everything above [ParametricBand.frequencyHz]. */
    HIGH_CUT
}

/**
 * One band of a parametric equalizer.
 *
 * @param id Stable identity for the UI and for animation between edits; not part of the sound
 * @param type The filter shape
 * @param frequencyHz Centre frequency, or the corner frequency for a shelf or a cut
 * @param q Sharpness. Larger is narrower for a [BandType.PEAK], steeper for a cut or a shelf
 * @param gainDb Boost or cut in decibels; ignored by [BandType.LOW_CUT] and [BandType.HIGH_CUT]
 * @param enabled Whether the band is in the profile sent to the engine and drawn on the curve
 */
data class ParametricBand(
    val id: Int,
    val type: BandType,
    val frequencyHz: Double,
    val q: Double,
    val gainDb: Double,
    val enabled: Boolean = true
)

/**
 * The complete equalizer setting: a preamp and the bands.
 *
 * This is the single source of truth for the sound. The curve on screen is drawn from it through
 * [org.prauga.equalizer.dsp.ProfileResponse] and the engine receives the same profile fitted onto
 * `DynamicsProcessing` bands, so what is drawn and what is heard cannot drift apart.
 *
 * @param preampDb Headroom applied to every band, in decibels
 * @param bands The bands, in the order the user added them
 * @param version Schema version, so stored profiles can be migrated
 */
data class EqProfile(
    val preampDb: Double = 0.0,
    val bands: List<ParametricBand> = emptyList(),
    val version: Int = CURRENT_VERSION
) {

    companion object {
        /** Schema version written with every stored profile. */
        const val CURRENT_VERSION: Int = 1

        /** Bands the editor starts a new preset with. */
        const val DEFAULT_BANDS: Int = 6

        /** Bands a user may add to one profile. */
        const val MAX_BANDS: Int = 12

        const val MIN_FREQUENCY_HZ: Double = 20.0
        const val MAX_FREQUENCY_HZ: Double = 20_000.0
        const val MIN_Q: Double = 0.3
        const val MAX_Q: Double = 10.0
        const val MIN_GAIN_DB: Double = -12.0
        const val MAX_GAIN_DB: Double = 12.0
        const val MIN_PREAMP_DB: Double = -12.0
        const val MAX_PREAMP_DB: Double = 12.0

        /**
         * A flat profile with the default six bands: shelves at the ends, bells between, and
         * nothing boosted until the user moves something.
         */
        fun flat(): EqProfile = EqProfile(
            bands = listOf(
                ParametricBand(0, BandType.LOW_SHELF, 62.0, 0.7, 0.0),
                ParametricBand(1, BandType.PEAK, 250.0, 1.0, 0.0),
                ParametricBand(2, BandType.PEAK, 1_000.0, 1.0, 0.0),
                ParametricBand(3, BandType.PEAK, 4_000.0, 1.0, 0.0),
                ParametricBand(4, BandType.PEAK, 8_000.0, 1.0, 0.0),
                ParametricBand(5, BandType.HIGH_SHELF, 12_000.0, 0.7, 0.0)
            )
        )
    }
}
