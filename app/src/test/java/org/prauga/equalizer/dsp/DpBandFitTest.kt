package org.prauga.equalizer.dsp

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.prauga.equalizer.domain.BandType
import org.prauga.equalizer.domain.EqProfile
import org.prauga.equalizer.domain.ParametricBand

class DpBandFitTest {

    private val sampleRate = 48_000.0

    /** A curve with a realistic spread of shelves, bells, gain and Q. */
    private fun musical(): EqProfile = EqProfile(
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

    @Test
    fun `a musical profile fits within one decibel`() {
        val profile = musical()
        val fit = DpBandFit.fit(profile, sampleRate)
        val worst = worstError(fit, ProfileResponse(profile, sampleRate))
        assertTrue("worst error was $worst dB", worst <= 1.0)
    }

    @Test
    fun `cutoffs are sorted, in range and within the api limit`() {
        val cutoffs = DpBandFit.cutoffs(musical(), DpBandFit.DEFAULT_BAND_COUNT, sampleRate)
        assertTrue("count ${cutoffs.size}", cutoffs.size <= DpBandFit.MAX_BAND_COUNT)
        assertTrue("count ${cutoffs.size}", cutoffs.size >= 100)
        assertEquals(DpBandFit.LOWEST_HZ, cutoffs.first(), 1e-9)
        for (index in 1 until cutoffs.size) {
            assertTrue(
                "${cutoffs[index - 1]} then ${cutoffs[index]}",
                cutoffs[index] > cutoffs[index - 1]
            )
        }
        assertTrue(cutoffs.last() <= DpBandFit.HIGHEST_HZ + 1e-9)
    }

    @Test
    fun `every enabled band centre is a cutoff`() {
        val profile = musical()
        val cutoffs = DpBandFit.cutoffs(profile, DpBandFit.DEFAULT_BAND_COUNT, sampleRate)
        for (band in profile.bands) {
            assertTrue(
                "no cutoff at ${band.frequencyHz}",
                cutoffs.any { abs(it - band.frequencyHz) < 1e-6 }
            )
        }
    }

    @Test
    fun `a disabled band places no cutoff`() {
        val profile = EqProfile(
            bands = listOf(
                ParametricBand(0, BandType.PEAK, 1_234.0, 1.0, 6.0, enabled = false)
            )
        )
        val cutoffs = DpBandFit.cutoffs(profile, DpBandFit.DEFAULT_BAND_COUNT, sampleRate)
        assertTrue(cutoffs.none { abs(it - 1_234.0) < 1e-6 })
    }

    @Test
    fun `a flat profile fits to silence on every band`() {
        val profile = EqProfile.flat()
        val fit = DpBandFit.fit(profile, sampleRate)
        for (band in fit.bands) {
            assertEquals("at ${band.cutoffHz} Hz", 0.0, band.gainDb, 1e-6)
        }
    }

    @Test
    fun `the preamp shifts every band`() {
        val fit = DpBandFit.fit(EqProfile.flat().copy(preampDb = -6.0), sampleRate)
        for (band in fit.bands) {
            assertEquals(-6.0, band.gainDb, 1e-6)
        }
    }

    @Test
    fun `the fit honours a smaller band count`() {
        val fit = DpBandFit.fit(musical(), sampleRate, bandCount = 32)
        assertTrue("count ${fit.bands.size}", fit.bands.size <= 32)
        val worst = worstError(fit, ProfileResponse(musical(), sampleRate))
        assertTrue("worst error was $worst dB", worst <= 2.0)
    }

    @Test
    fun `a steep high frequency bell stays close`() {
        val profile = EqProfile(
            bands = listOf(ParametricBand(0, BandType.PEAK, 4_000.0, 8.0, 12.0))
        )
        val fit = DpBandFit.fit(profile, sampleRate)
        val worst = worstError(fit, ProfileResponse(profile, sampleRate))
        assertTrue("worst error was $worst dB", worst <= 1.0)
    }

    @Test
    fun `the staircase reports the band that covers a frequency`() {
        val fit = DpFit(
            listOf(
                DpBand(100.0, 1.0),
                DpBand(1_000.0, 2.0),
                DpBand(10_000.0, 3.0)
            )
        )
        assertEquals(1.0, fit.dbAt(50.0), 0.0)
        assertEquals(1.0, fit.dbAt(100.0), 0.0)
        assertEquals(2.0, fit.dbAt(999.0), 0.0)
        assertEquals(3.0, fit.dbAt(20_000.0), 0.0)
    }

    private fun worstError(fit: DpFit, response: ProfileResponse): Double {
        var worst = 0.0
        val count = 500
        for (index in 0 until count) {
            val frequency = 20.0 * Math.pow(1_000.0, index.toDouble() / (count - 1))
            worst = maxOf(worst, abs(fit.dbAt(frequency) - response.dbAt(frequency)))
        }
        return worst
    }
}
