package org.prauga.equalizer.dsp

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
}
