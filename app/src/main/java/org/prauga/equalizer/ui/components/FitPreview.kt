package org.prauga.equalizer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.slider.PvotSlider
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import org.prauga.equalizer.R
import org.prauga.equalizer.domain.BandType
import org.prauga.equalizer.domain.EqProfile
import org.prauga.equalizer.domain.ParametricBand
import org.prauga.equalizer.dsp.DpBandFit
import org.prauga.equalizer.dsp.DpFit
import org.prauga.equalizer.dsp.ProfileResponse

/**
 * A debug harness for the DSP core: the smooth analytic curve against the staircase the engine will
 * actually apply, with the worst error between them and how long the fit took on this device.
 *
 * Temporary. It exists so the fit can be judged by eye before the engine is wired up, and is
 * replaced by the real `EqCurve` in the curve phase.
 */
@Composable
fun FitPreview(modifier: Modifier = Modifier) {
    var gainDb by remember { mutableFloatStateOf(6f) }
    var q by remember { mutableFloatStateOf(1.5f) }

    val warmFitMillis = remember { measureWarmFitMillis() }

    val preview = remember(gainDb, q) {
        val profile = EqProfile(
            preampDb = -2.0,
            bands = listOf(
                ParametricBand(0, BandType.LOW_SHELF, 100.0, 0.7, 4.0),
                ParametricBand(
                    id = 1,
                    type = BandType.PEAK,
                    frequencyHz = 1_000.0,
                    q = q.toDouble(),
                    gainDb = gainDb.toDouble()
                ),
                ParametricBand(2, BandType.HIGH_SHELF, 10_000.0, 0.7, -3.0)
            )
        )
        val fit = DpBandFit.fit(profile, SAMPLE_RATE_HZ)
        val response = ProfileResponse(profile, SAMPLE_RATE_HZ)

        var worst = 0.0
        for (index in 0 until ERROR_SAMPLES) {
            val frequency = MIN_HZ * Math.pow(MAX_HZ / MIN_HZ, index.toDouble() / (ERROR_SAMPLES - 1))
            worst = maxOf(worst, abs(fit.dbAt(frequency) - response.dbAt(frequency)))
        }
        PreviewData(fit = fit, response = response, worstErrorDb = worst)
    }

    val analyticColor = MaterialTheme.colorScheme.primary
    val staircaseColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            val logSpan = ln(MAX_HZ / MIN_HZ)
            fun xOf(frequency: Double): Float =
                ((ln(frequency / MIN_HZ) / logSpan).coerceIn(0.0, 1.0) * size.width).toFloat()

            fun yOf(db: Double): Float =
                ((MAX_DB - db.coerceIn(-MAX_DB, MAX_DB)) / (2 * MAX_DB) * size.height).toFloat()

            drawLine(gridColor, Offset(0f, yOf(0.0)), Offset(size.width, yOf(0.0)), strokeWidth = 1f)
            for (frequency in listOf(100.0, 1_000.0, 10_000.0)) {
                drawLine(
                    color = gridColor.copy(alpha = 0.4f),
                    start = Offset(xOf(frequency), 0f),
                    end = Offset(xOf(frequency), size.height),
                    strokeWidth = 1f
                )
            }

            // What the engine applies: one flat gain per band.
            val staircase = Path()
            var previousGain = preview.fit.dbAt(MIN_HZ)
            staircase.moveTo(0f, yOf(previousGain))
            for (band in preview.fit.bands) {
                staircase.lineTo(xOf(band.cutoffHz), yOf(previousGain))
                staircase.lineTo(xOf(band.cutoffHz), yOf(band.gainDb))
                previousGain = band.gainDb
            }
            staircase.lineTo(size.width, yOf(previousGain))
            drawPath(staircase, staircaseColor.copy(alpha = 0.6f), style = Stroke(width = 2f))

            // What the user drew and what the curve shows.
            val curve = Path()
            for (index in 0..300) {
                val frequency = MIN_HZ * exp(logSpan * index / 300.0)
                val x = xOf(frequency)
                val y = yOf(preview.response.dbAt(frequency))
                if (index == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
            }
            drawPath(curve, analyticColor, style = Stroke(width = 3f))
        }

        Text(
            text = stringResource(R.string.preview_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = PvotSpacing.S, bottom = PvotSpacing.S)
        )
        Text(
            text = stringResource(
                R.string.preview_error,
                String.format(Locale.US, "%.2f", preview.worstErrorDb)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(
                R.string.preview_cost,
                String.format(Locale.US, "%.2f", warmFitMillis),
                preview.fit.bands.size
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = stringResource(R.string.preview_gain, gainDb.toInt()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = PvotSpacing.M)
        )
        PvotSlider(
            value = gainDb,
            onValueChange = { gainDb = it },
            valueRange = -12f..12f
        )
        Text(
            text = stringResource(
                R.string.preview_q,
                String.format(Locale.US, "%.1f", q)
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PvotSlider(
            value = q,
            onValueChange = { q = it },
            valueRange = 0.3f..10f
        )
    }
}

private data class PreviewData(
    val fit: DpFit,
    val response: ProfileResponse,
    val worstErrorDb: Double
)

/**
 * A representative cost for one refit on this device, not the first one: the first fit in a fresh
 * process pays class loading and JIT and reads around four times slower, which is not the number a
 * drag experiences. The best of several is used because the worst are scheduler noise.
 */
private fun measureWarmFitMillis(): Double {
    val profile = EqProfile(
        preampDb = -2.0,
        bands = listOf(
            ParametricBand(0, BandType.LOW_SHELF, 100.0, 0.7, 4.0),
            ParametricBand(1, BandType.PEAK, 1_000.0, 1.5, 6.0),
            ParametricBand(2, BandType.HIGH_SHELF, 10_000.0, 0.7, -3.0)
        )
    )
    repeat(20) { DpBandFit.fit(profile, SAMPLE_RATE_HZ) }
    var best = Double.MAX_VALUE
    repeat(10) {
        val startedAt = System.nanoTime()
        DpBandFit.fit(profile, SAMPLE_RATE_HZ)
        best = minOf(best, (System.nanoTime() - startedAt) / 1_000_000.0)
    }
    return best
}

private const val SAMPLE_RATE_HZ = 48_000.0
private const val MIN_HZ = 20.0
private const val MAX_HZ = 20_000.0
private const val MAX_DB = 12.0
private const val ERROR_SAMPLES = 500
