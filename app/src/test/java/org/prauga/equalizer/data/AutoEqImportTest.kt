package org.prauga.equalizer.data

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.prauga.equalizer.domain.EqMode

class AutoEqImportTest {

    private val parametric = """
        Preamp: -6.0 dB
        Filter 1: ON PK Fc 105 Hz Gain 6.0 dB Q 0.70
        Filter 2: ON PK Fc 3000 Hz Gain -4.0 dB Q 1.20
    """.trimIndent()

    @Test
    fun `parses an AutoEQ parametric export`() {
        val preset = AutoEqImport.toPreset(parametric, EqMode.ADVANCED, "Test")
        assertNotNull(preset)
        assertEquals(-6.0, preset!!.preampDb, 0.001)
        // The low filter lifts the bass bands and the preamp pulls everything down.
        val low = preset.gains.entries.minByOrNull { it.key }!!.value
        assertTrue("low band should be lifted, was $low", low > 0.0)
        // The 3 kHz filter cuts, so the bands around it stay below the bass lift.
        val high = preset.gains.entries.filter { it.key in 2_000.0..4_000.0 }
        assertTrue("high bands should be cut", high.isNotEmpty() && high.all { it.value < low })
    }

    @Test
    fun `parses an AutoEQ graphic export`() {
        val text = "GraphicEQ: 20 -4.0; 100 2.0; 1000 0.0; 10000 -3.0; 20000 -6.0"
        val preset = AutoEqImport.toPreset(text, EqMode.SIMPLE, "Graphic")
        assertNotNull(preset)
        val mid = preset!!.gains.entries.first { it.key in 900.0..1_500.0 }.value
        assertTrue("mid band near the 2 dB point, was $mid", abs(mid) < 2.0)
    }

    @Test
    fun `rejects text that is not an EQ export`() {
        assertNull(AutoEqImport.toPreset("hello world", EqMode.SIMPLE, "Nope"))
        assertNull(AutoEqImport.toPreset("""{"name":"json"}""", EqMode.SIMPLE, "Nope"))
    }
}
