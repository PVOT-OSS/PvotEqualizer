package org.prauga.equalizer.dsp

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.prauga.equalizer.domain.BandType
import org.prauga.equalizer.domain.EqProfile
import org.prauga.equalizer.domain.ParametricBand

/**
 * The same guarantees the JVM tests make, run on the device: the maths has to hold under ART on
 * ARM as well, and a fit has to be fast enough to keep up with a drag on the phone rather than on a
 * developer machine.
 */
class DspOnDeviceTest {

    private val sampleRate = 48_000.0

    private fun musical() = EqProfile(
        preampDb = -2.0,
        bands = listOf(
            ParametricBand(0, BandType.LOW_SHELF, 100.0, 0.7, 4.0),
            ParametricBand(1, BandType.PEAK, 400.0, 1.2, -3.0),
            ParametricBand(2, BandType.PEAK, 1_000.0, 2.0, 2.0),
            ParametricBand(3, BandType.PEAK, 3_200.0, 1.5, -5.0),
            ParametricBand(4, BandType.PEAK, 10_000.0, 1.0, 6.0),
            ParametricBand(5, BandType.HIGH_SHELF, 12_000.0, 0.7, -3.0)
        )
    )

    private fun worstError(fit: DpFit, response: ProfileResponse): Double {
        var worst = 0.0
        for (index in 0 until 500) {
            val frequency = 20.0 * Math.pow(1_000.0, index.toDouble() / 499.0)
            worst = maxOf(worst, abs(fit.dbAt(frequency) - response.dbAt(frequency)))
        }
        return worst
    }

    @Test
    fun aMusicalProfileFitsWithinOneDecibelOnDevice() {
        val profile = musical()
        val fit = DpBandFit.fit(profile, sampleRate)
        val worst = worstError(fit, ProfileResponse(profile, sampleRate))
        assertTrue("worst error was $worst dB", worst <= 1.0)
    }

    @Test
    fun aSteepBellFitsWithinOneDecibelOnDevice() {
        val profile = EqProfile(bands = listOf(ParametricBand(0, BandType.PEAK, 4_000.0, 8.0, 12.0)))
        val fit = DpBandFit.fit(profile, sampleRate)
        val worst = worstError(fit, ProfileResponse(profile, sampleRate))
        assertTrue("worst error was $worst dB", worst <= 1.0)
    }

    @Test
    fun aPeakingBandBoostsItsCentreOnDevice() {
        val band = ParametricBand(0, BandType.PEAK, 1_000.0, 1.0, 6.0)
        val response = ProfileResponse(
            EqProfile(bands = listOf(band)),
            sampleRate
        )
        assertEquals(6.0, response.dbAt(1_000.0), 0.01)
    }

    @Test
    fun aFitKeepsUpWithADragOnDevice() {
        val profile = musical()
        // Warm the JIT the way a drag would.
        repeat(30) { DpBandFit.fit(profile, sampleRate) }

        val start = System.nanoTime()
        repeat(100) { DpBandFit.fit(profile, sampleRate) }
        val perFitMs = (System.nanoTime() - start) / 100.0 / 1_000_000.0

        println("device fit: %.3f ms per fit".format(perFitMs))
        // One 120 Hz frame is 8.3 ms; a refit has to leave room for drawing.
        assertTrue("fit took $perFitMs ms", perFitMs < 3.0)
    }
}
