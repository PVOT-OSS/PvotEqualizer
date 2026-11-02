package org.prauga.equalizer.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.prauga.equalizer.domain.BandType
import org.prauga.equalizer.domain.ParametricBand

/**
 * Normalized biquad coefficients, with `a0` already divided out.
 *
 * Apply as `y = b0*x + b1*x1 + b2*x2 - a1*y1 - a2*y2`. The coefficients come from the RBJ audio EQ
 * cookbook, which is the same family of filters `DynamicsProcessing` implements internally, so a
 * parametric curve drawn from these is the curve the engine is asked to reproduce.
 */
data class BiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double
)

/**
 * Turns a [ParametricBand] into biquad coefficients and evaluates its magnitude response.
 *
 * Pure maths, no Android: this is what the curve is drawn from and what the engine band fit is
 * measured against.
 */
object Biquad {

    /**
     * The coefficients of [band] at [sampleRateHz].
     *
     * The frequency is held inside `(0, sampleRate/2)` and the Q at or above 0.05 so that an
     * out-of-range value from the UI degrades instead of producing NaN coefficients.
     */
    fun of(band: ParametricBand, sampleRateHz: Double): BiquadCoefficients {
        val fs = sampleRateHz.coerceAtLeast(8_000.0)
        val f0 = band.frequencyHz.coerceIn(1.0, fs * 0.49)
        val q = band.q.coerceAtLeast(0.05)
        val w0 = 2.0 * PI * f0 / fs
        val cosW = cos(w0)
        val sinW = sin(w0)
        // alpha for a bell, a cut, and (with the shelf slope S = 1) a shelf.
        val alpha = sinW / (2.0 * q)
        val amp = 10.0.pow(band.gainDb / 40.0)

        return when (band.type) {
            BandType.PEAK -> {
                val a0 = 1.0 + alpha / amp
                BiquadCoefficients(
                    b0 = (1.0 + alpha * amp) / a0,
                    b1 = (-2.0 * cosW) / a0,
                    b2 = (1.0 - alpha * amp) / a0,
                    a1 = (-2.0 * cosW) / a0,
                    a2 = (1.0 - alpha / amp) / a0
                )
            }

            BandType.LOW_SHELF -> {
                val shelfAlpha = sinW / 2.0 * sqrt(2.0)
                val rootA = sqrt(amp)
                val a0 = (amp + 1.0) + (amp - 1.0) * cosW + 2.0 * rootA * shelfAlpha
                BiquadCoefficients(
                    b0 = amp * ((amp + 1.0) - (amp - 1.0) * cosW + 2.0 * rootA * shelfAlpha) / a0,
                    b1 = 2.0 * amp * ((amp - 1.0) - (amp + 1.0) * cosW) / a0,
                    b2 = amp * ((amp + 1.0) - (amp - 1.0) * cosW - 2.0 * rootA * shelfAlpha) / a0,
                    a1 = -2.0 * ((amp - 1.0) + (amp + 1.0) * cosW) / a0,
                    a2 = ((amp + 1.0) + (amp - 1.0) * cosW - 2.0 * rootA * shelfAlpha) / a0
                )
            }

            BandType.HIGH_SHELF -> {
                val shelfAlpha = sinW / 2.0 * sqrt(2.0)
                val rootA = sqrt(amp)
                val a0 = (amp + 1.0) - (amp - 1.0) * cosW + 2.0 * rootA * shelfAlpha
                BiquadCoefficients(
                    b0 = amp * ((amp + 1.0) + (amp - 1.0) * cosW + 2.0 * rootA * shelfAlpha) / a0,
                    b1 = -2.0 * amp * ((amp - 1.0) + (amp + 1.0) * cosW) / a0,
                    b2 = amp * ((amp + 1.0) + (amp - 1.0) * cosW - 2.0 * rootA * shelfAlpha) / a0,
                    a1 = 2.0 * ((amp - 1.0) - (amp + 1.0) * cosW) / a0,
                    a2 = ((amp + 1.0) - (amp - 1.0) * cosW - 2.0 * rootA * shelfAlpha) / a0
                )
            }

            // A high-pass, per the cookbook's naming rather than the band's.
            BandType.LOW_CUT -> {
                val a0 = 1.0 + alpha
                BiquadCoefficients(
                    b0 = (1.0 + cosW) / 2.0 / a0,
                    b1 = -(1.0 + cosW) / a0,
                    b2 = (1.0 + cosW) / 2.0 / a0,
                    a1 = (-2.0 * cosW) / a0,
                    a2 = (1.0 - alpha) / a0
                )
            }

            // A low-pass.
            BandType.HIGH_CUT -> {
                val a0 = 1.0 + alpha
                BiquadCoefficients(
                    b0 = (1.0 - cosW) / 2.0 / a0,
                    b1 = (1.0 - cosW) / a0,
                    b2 = (1.0 - cosW) / 2.0 / a0,
                    a1 = (-2.0 * cosW) / a0,
                    a2 = (1.0 - alpha) / a0
                )
            }
        }
    }

    /** The magnitude response of [coefficients] at [frequencyHz], in decibels. */
    fun dbAt(coefficients: BiquadCoefficients, frequencyHz: Double, sampleRateHz: Double): Double {
        val fs = sampleRateHz.coerceAtLeast(8_000.0)
        val w = 2.0 * PI * frequencyHz.coerceIn(0.0, fs * 0.5) / fs
        return dbAtRotation(coefficients, cos(w), sin(w))
    }

    /**
     * The magnitude response from a precomputed rotation, in decibels.
     *
     * A profile has to be evaluated at hundreds of frequencies, and every band turns into the same
     * four trigonometry calls; doing them once per frequency instead of once per band and frequency
     * is what makes a refit fit inside a frame on a phone. The double-angle terms are derived rather
     * than asked for.
     */
    fun dbAtRotation(coefficients: BiquadCoefficients, cosW: Double, sinW: Double): Double {
        val cos2W = cosW * cosW - sinW * sinW
        val sin2W = 2.0 * sinW * cosW

        // H(e^jw) = (b0 + b1 e^-jw + b2 e^-2jw) / (1 + a1 e^-jw + a2 e^-2jw)
        val numeratorReal = coefficients.b0 + coefficients.b1 * cosW + coefficients.b2 * cos2W
        val numeratorImaginary = -(coefficients.b1 * sinW + coefficients.b2 * sin2W)
        val denominatorReal = 1.0 + coefficients.a1 * cosW + coefficients.a2 * cos2W
        val denominatorImaginary = -(coefficients.a1 * sinW + coefficients.a2 * sin2W)

        val numerator = sqrt(numeratorReal * numeratorReal + numeratorImaginary * numeratorImaginary)
        val denominator =
            sqrt(denominatorReal * denominatorReal + denominatorImaginary * denominatorImaginary)
        if (denominator == 0.0) return 0.0
        return 20.0 * log10(numerator / denominator)
    }
}
