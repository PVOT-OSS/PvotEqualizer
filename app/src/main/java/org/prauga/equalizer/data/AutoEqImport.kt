package org.prauga.equalizer.data

import java.util.UUID
import kotlin.math.abs
import kotlin.math.ln
import org.prauga.equalizer.domain.BandType
import org.prauga.equalizer.domain.EqBands
import org.prauga.equalizer.domain.EqMode
import org.prauga.equalizer.domain.EqProfile
import org.prauga.equalizer.domain.ParametricBand
import org.prauga.equalizer.domain.Preset
import org.prauga.equalizer.dsp.Biquad
import org.prauga.equalizer.dsp.ProfileResponse

/**
 * Reads the two text formats AutoEQ exports, so curves people already have can come across.
 *
 * `ParametricEQ.txt` is a preamp and a list of biquads; `GraphicEQ.txt` is a list of frequency/gain
 * points. Either way the target is a continuous response, and the bands are fitted to it with a few
 * correction passes, because our bands are bells that overlap each other.
 */
object AutoEqImport {

    private const val SAMPLE_RATE_HZ = 48_000.0
    private const val PASSES = 4

    private val PREAMP = Regex("""Preamp:\s*(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val FILTER = Regex(
        """Filter\s+\d+:\s+(ON|OFF)\s+(\w+)\s+Fc\s+([\d.]+)\s*Hz\s+Gain\s+(-?[\d.]+)\s*dB\s+Q\s+([\d.]+)""",
        RegexOption.IGNORE_CASE
    )
    private val POINT = Regex("""(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)""")

    /** A target curve, with the preamp it assumes. */
    private class Target(val preampDb: Double, val responseAt: (Double) -> Double)

    /** Returns a preset fitted to [text], or null when the text is not a format we know. */
    fun toPreset(text: String, mode: EqMode, name: String): Preset? {
        val target = parseParametric(text) ?: parseGraphic(text) ?: return null
        val preamp = target.preampDb.coerceIn(
            EqProfile.MIN_PREAMP_DB,
            EqProfile.MAX_PREAMP_DB
        )

        var profile = EqBands.profile(mode, preamp)
        repeat(PASSES) {
            val response = ProfileResponse(profile, SAMPLE_RATE_HZ)
            profile = profile.copy(
                bands = profile.bands.map { band ->
                    val heard = response.dbAt(band.frequencyHz) - profile.preampDb
                    val wanted = target.responseAt(band.frequencyHz) - profile.preampDb
                    val corrected = band.gainDb + (wanted - heard)
                    band.copy(
                        gainDb = corrected.coerceIn(
                            EqProfile.MIN_GAIN_DB,
                            EqProfile.MAX_GAIN_DB
                        )
                    )
                }
            )
        }

        return Preset(
            id = UUID.randomUUID().toString(),
            name = name,
            mode = mode,
            preampDb = profile.preampDb,
            gains = profile.bands.filter { it.gainDb != 0.0 }
                .associate { it.frequencyHz to it.gainDb }
        )
    }

    private fun parseParametric(text: String): Target? {
        val filters = FILTER.findAll(text)
            .filter { it.groupValues[1].equals("ON", ignoreCase = true) }
            .mapNotNull { match ->
                val type = bandType(match.groupValues[2]) ?: return@mapNotNull null
                ParametricBand(
                    id = 0,
                    type = type,
                    frequencyHz = match.groupValues[3].toDoubleOrNull() ?: return@mapNotNull null,
                    q = match.groupValues[5].toDoubleOrNull() ?: 0.7,
                    gainDb = match.groupValues[4].toDoubleOrNull() ?: 0.0
                )
            }
            .toList()
        if (filters.isEmpty()) return null

        val coefficients = filters.map { Biquad.of(it, SAMPLE_RATE_HZ) }
        val preamp = PREAMP.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        return Target(preamp) { frequencyHz ->
            var db = preamp
            for (c in coefficients) db += Biquad.dbAt(c, frequencyHz, SAMPLE_RATE_HZ)
            db
        }
    }

    private fun parseGraphic(text: String): Target? {
        val body = text.substringAfter("GraphicEQ:", text)
        val points = POINT.findAll(body)
            .mapNotNull { match ->
                val frequency = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                val gain = match.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
                if (frequency > 0.0) frequency to gain else null
            }
            .sortedBy { it.first }
            .toList()
        if (points.size < 3) return null

        // AutoEQ's graphic files already fold the preamp into the values, so it is not applied twice.
        return Target(0.0) { frequencyHz -> interpolate(points, frequencyHz) }
    }

    private fun interpolate(points: List<Pair<Double, Double>>, frequencyHz: Double): Double {
        val first = points.first()
        val last = points.last()
        if (frequencyHz <= first.first) return first.second
        if (frequencyHz >= last.first) return last.second
        for (index in 1 until points.size) {
            val (upperHz, upperGain) = points[index]
            if (frequencyHz > upperHz) continue
            val (lowerHz, lowerGain) = points[index - 1]
            if (abs(upperHz - lowerHz) < 0.0001) return upperGain
            // Interpolate on a log axis: the points are spaced logarithmically, not linearly.
            val span = ln(upperHz / lowerHz)
            val position = ln(frequencyHz / lowerHz) / span
            return lowerGain + (upperGain - lowerGain) * position
        }
        return last.second
    }

    private fun bandType(token: String): BandType? = when (token.uppercase()) {
        "PK", "PEAK", "BP" -> BandType.PEAK
        "LS", "LSC", "LSH", "LOWSHELF" -> BandType.LOW_SHELF
        "HS", "HSC", "HSH", "HIGHSHELF" -> BandType.HIGH_SHELF
        "LP", "LPQ" -> BandType.LOW_CUT
        "HP", "HPQ" -> BandType.HIGH_CUT
        else -> null
    }
}
