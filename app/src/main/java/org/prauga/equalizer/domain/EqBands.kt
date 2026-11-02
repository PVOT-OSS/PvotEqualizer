package org.prauga.equalizer.domain

import kotlin.math.sqrt

/**
 * The fixed frequencies the equalizer works at, and the shape of each band.
 *
 * The frequencies are fixed on purpose. A graphic equalizer is quicker to use when every band lives
 * in a place the eye can predict, and it removes the two controls (frequency and Q) that made the
 * editor feel like a puzzle. Q is derived from the spacing of each band's neighbours, so a six-band
 * curve is as smooth as a ten-band one without the user ever meeting the concept.
 */
object EqBands {

    /** Every frequency the equalizer can use, in Hz, ascending. */
    val ALL: List<Double> = listOf(
        31.5, 63.0, 125.0, 250.0, 500.0,
        1_000.0, 2_000.0, 4_000.0, 8_000.0, 16_000.0
    )

    /**
     * Six bands spaced evenly in octaves from 31.5 Hz to 16 kHz. Simple mode is a coarser grid than
     * advanced mode, not a handful of holes punched in it: uneven spacing is what made the graph look
     * like it had clusters.
     */
    val SIMPLE: List<Double> = listOf(31.5, 110.0, 380.0, 1_320.0, 4_600.0, 16_000.0)

    /** The frequencies [mode] offers. */
    fun frequencies(mode: EqMode): List<Double> = when (mode) {
        EqMode.SIMPLE -> SIMPLE
        EqMode.ADVANCED -> ALL
    }

    /**
     * The Q that makes a band meet its neighbours: its band edges are the geometric means of the
     * frequencies either side, which is the standard graphic-equalizer compromise between a smooth
     * curve and bands that stay independent.
     */
    fun qFor(frequencies: List<Double>, index: Int): Double {
        val f = frequencies[index]
        val upper = if (index == frequencies.lastIndex) {
            f * f / sqrt(f * frequencies[index - 1])
        } else {
            sqrt(f * frequencies[index + 1])
        }
        val lower = if (index == 0) {
            f * f / sqrt(f * frequencies[index + 1])
        } else {
            sqrt(frequencies[index - 1] * f)
        }
        return (f / (upper - lower)).coerceIn(EqProfile.MIN_Q, EqProfile.MAX_Q)
    }

    /** A flat profile for [mode]. */
    fun profile(mode: EqMode, preampDb: Double = 0.0): EqProfile {
        val frequencies = frequencies(mode)
        return EqProfile(
            preampDb = preampDb,
            bands = frequencies.mapIndexed { index, frequency ->
                ParametricBand(
                    id = index,
                    type = BandType.PEAK,
                    frequencyHz = frequency,
                    q = qFor(frequencies, index),
                    gainDb = 0.0
                )
            }
        )
    }
}
