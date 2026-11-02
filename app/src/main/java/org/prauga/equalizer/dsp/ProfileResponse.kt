package org.prauga.equalizer.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.prauga.equalizer.domain.EqProfile

/**
 * The analytic response of a whole [EqProfile]: the preamp plus every enabled band.
 *
 * This is what the curve on screen is drawn from. It is deliberately independent of any engine, so
 * the picture does not depend on how well the engine can reproduce it.
 *
 * @param profile The profile to evaluate
 * @param sampleRateHz Sample rate the filters are designed at; the response is a function of it
 */
class ProfileResponse(
    private val profile: EqProfile,
    private val sampleRateHz: Double
) {

    private val preampDb: Double = profile.preampDb
    private val filters: List<BiquadCoefficients> = profile.bands
        .filter { it.enabled }
        .map { Biquad.of(it, sampleRateHz) }

    /** The combined response at [frequencyHz], in decibels. */
    fun dbAt(frequencyHz: Double): Double {
        var db = preampDb
        for (coefficients in filters) {
            db += Biquad.dbAt(coefficients, frequencyHz, sampleRateHz)
        }
        return db
    }

    /**
     * The response at every frequency in [frequencies], in decibels.
     *
     * Equivalent to calling [dbAt] in a loop, but the trigonometry is done once per frequency rather
     * than once per band and frequency. Fitting samples the curve on a thousand points, so this is
     * the difference between a refit that keeps up with a drag and one that does not.
     */
    fun curve(frequencies: DoubleArray): DoubleArray {
        val result = DoubleArray(frequencies.size)
        val nyquist = sampleRateHz * 0.5
        for (index in frequencies.indices) {
            val w = 2.0 * PI * frequencies[index].coerceIn(0.0, nyquist) / sampleRateHz
            val cosW = cos(w)
            val sinW = sin(w)
            var db = preampDb
            for (coefficients in filters) {
                db += Biquad.dbAtRotation(coefficients, cosW, sinW)
            }
            result[index] = db
        }
        return result
    }
}
