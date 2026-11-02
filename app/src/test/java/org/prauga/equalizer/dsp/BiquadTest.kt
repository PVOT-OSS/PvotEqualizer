package org.prauga.equalizer.dsp

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.prauga.equalizer.domain.BandType
import org.prauga.equalizer.domain.EqProfile
import org.prauga.equalizer.domain.ParametricBand

class BiquadTest {

    private val sampleRate = 48_000.0

    private fun band(type: BandType, frequency: Double, q: Double, gainDb: Double) =
        ParametricBand(id = 0, type = type, frequencyHz = frequency, q = q, gainDb = gainDb)

    private fun db(band: ParametricBand, at: Double): Double =
        Biquad.dbAt(Biquad.of(band, sampleRate), at, sampleRate)

    @Test
    fun `a peaking band boosts its centre by its gain`() {
        val bell = band(BandType.PEAK, 1_000.0, 1.0, 6.0)
        assertEquals(6.0, db(bell, 1_000.0), 0.01)
    }

    @Test
    fun `a peaking band cuts its centre by its gain`() {
        val bell = band(BandType.PEAK, 1_000.0, 1.0, -9.0)
        assertEquals(-9.0, db(bell, 1_000.0), 0.01)
    }

    @Test
    fun `a peaking band leaves distant frequencies alone`() {
        val bell = band(BandType.PEAK, 1_000.0, 1.0, 6.0)
        assertEquals(0.0, db(bell, 20.0), 0.2)
        assertEquals(0.0, db(bell, 20_000.0), 0.2)
    }

    @Test
    fun `a peaking band with no gain is flat`() {
        val flat = band(BandType.PEAK, 1_000.0, 1.0, 0.0)
        for (frequency in logSweep(20.0, 20_000.0, 60)) {
            assertEquals("at $frequency Hz", 0.0, db(flat, frequency), 1e-9)
        }
    }

    @Test
    fun `a low shelf lifts the low end and leaves the top`() {
        val shelf = band(BandType.LOW_SHELF, 100.0, 0.7, 6.0)
        assertEquals(6.0, db(shelf, 20.0), 0.4)
        assertEquals(0.0, db(shelf, 10_000.0), 0.1)
    }

    @Test
    fun `a high shelf lifts the top and leaves the low end`() {
        val shelf = band(BandType.HIGH_SHELF, 8_000.0, 0.7, 6.0)
        assertEquals(0.0, db(shelf, 100.0), 0.1)
        assertEquals(6.0, db(shelf, 20_000.0), 0.4)
    }

    @Test
    fun `a low cut removes the low end and leaves the top`() {
        val cut = band(BandType.LOW_CUT, 100.0, 0.7, 0.0)
        assertTrue("was ${db(cut, 20.0)}", db(cut, 20.0) < -20.0)
        assertEquals(0.0, db(cut, 10_000.0), 0.1)
    }

    @Test
    fun `a high cut removes the top and leaves the low end`() {
        val cut = band(BandType.HIGH_CUT, 8_000.0, 0.7, 0.0)
        assertTrue("was ${db(cut, 20_000.0)}", db(cut, 20_000.0) < -6.0)
        assertEquals(0.0, db(cut, 100.0), 0.1)
    }

    @Test
    fun `out of range values degrade instead of producing NaN`() {
        val odd = ParametricBand(
            id = 0,
            type = BandType.PEAK,
            frequencyHz = 100_000.0,
            q = 0.0,
            gainDb = 6.0
        )
        val response = Biquad.dbAt(Biquad.of(odd, sampleRate), 1_000.0, sampleRate)
        assertTrue("was $response", response.isFinite())
    }

    @Test
    fun `the profile response adds the preamp and every enabled band`() {
        val profile = EqProfile(
            preampDb = -3.0,
            bands = listOf(
                ParametricBand(0, BandType.PEAK, 1_000.0, 1.0, 4.0),
                ParametricBand(1, BandType.PEAK, 1_000.0, 1.0, 6.0),
                ParametricBand(2, BandType.PEAK, 1_000.0, 1.0, 99.0, enabled = false)
            )
        )
        val response = ProfileResponse(profile, sampleRate)
        assertEquals(-3.0 + 4.0 + 6.0, response.dbAt(1_000.0), 0.01)
    }

    private fun logSweep(from: Double, to: Double, count: Int): List<Double> =
        (0 until count).map { index ->
            from * Math.pow(to / from, index.toDouble() / (count - 1))
        }

    @Test
    fun `response is symmetric around a bell`() {
        val bell = band(BandType.PEAK, 1_000.0, 2.0, 6.0)
        val below = db(bell, 500.0)
        val above = db(bell, 2_000.0)
        assertTrue("below $below above $above", abs(below - above) < 0.05)
    }
}
