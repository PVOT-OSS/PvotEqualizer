package org.prauga.equalizer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import org.prauga.equalizer.domain.EqProfile
import org.prauga.equalizer.domain.ParametricBand
import org.prauga.equalizer.dsp.ProfileResponse

/**
 * The equalizer as one control: the curve and its faders are the same drawing.
 *
 * Each band is a vertical track at its fixed frequency, with a handle on the curve and its gain
 * printed above. Dragging a handle moves only its band's gain, so there is nothing else to get
 * wrong while dragging.
 *
 * @param profile The curve to draw
 * @param selectedBandId The band drawn as selected
 * @param onSelectBand Called when a handle is touched
 * @param onGainChange Called continuously while a handle is dragged
 * @param modifier Modifier for the control
 */
@Composable
fun EqCurve(
    profile: EqProfile,
    selectedBandId: Int?,
    onSelectBand: (Int) -> Unit,
    onGainChange: (id: Int, gainDb: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val selectedLabelStyle =
        MaterialTheme.typography.labelSmall.copy(color = colors.primary)

    val response = remember(profile) { ProfileResponse(profile, SAMPLE_RATE_HZ) }
    // A handle sits where the curve is at its frequency, so the label is what is heard there.
    val gainLabels = profile.bands.map { formatGain(response.dbAt(it.frequencyHz)) }

    // Keys must stay stable: touching a handle selects a band, which recomposes the control, and a
    // restarted gesture handler would cancel the drag that just began.
    val bands by rememberUpdatedState(profile.bands)
    val selectBand by rememberUpdatedState(onSelectBand)
    val changeGain by rememberUpdatedState(onGainChange)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(CurveHeight)
            .pointerInput(Unit) {
                val hitRadius = 30.dp.toPx()

                // Read the canvas size at use time, not when the handler is installed: at install
                // time layout may not have run, and a stale size shifts every drag off the finger.
                fun plotX(frequencyHz: Double): Float {
                    val left = LabelWidth.toPx()
                    val plotWidth = size.width - left
                    return left + ((ln(frequencyHz / MIN_HZ) / LOG_SPAN).coerceIn(0.0, 1.0) *
                        plotWidth).toFloat()
                }

                fun plotY(db: Double): Float {
                    val top = TopBand.toPx()
                    val plotHeight = size.height - top - BottomBand.toPx()
                    return top + ((DISPLAY_DB - db.coerceIn(-DISPLAY_DB, DISPLAY_DB)) /
                        (2 * DISPLAY_DB) * plotHeight).toFloat()
                }

                fun bandAt(position: Offset): ParametricBand? {
                    var nearest: ParametricBand? = null
                    var nearestDistance = Float.MAX_VALUE
                    for (band in bands) {
                        val dx = plotX(band.frequencyHz) - position.x
                        val dy = plotY(band.gainDb) - position.y
                        val distance = sqrt(dx * dx + dy * dy)
                        if (distance < hitRadius && distance < nearestDistance) {
                            nearestDistance = distance
                            nearest = band
                        }
                    }
                    return nearest
                }

                fun gainAt(y: Float): Double {
                    val top = TopBand.toPx()
                    val plotHeight = size.height - top - BottomBand.toPx()
                    val fraction = ((y - top) / plotHeight).coerceIn(0f, 1f)
                    return DISPLAY_DB - fraction.toDouble() * (2 * DISPLAY_DB)
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val band = bandAt(down.position) ?: return@awaitEachGesture
                    selectBand(band.id)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) break
                        if (!change.positionChanged()) continue
                        changeGain(band.id, gainAt(change.position.y))
                        change.consume()
                    }
                }
            }
    ) {
        val left = LabelWidth.toPx()
        val top = TopBand.toPx()
        val bottom = BottomBand.toPx()
        val plotWidth = size.width - left
        val plotHeight = size.height - top - bottom

        fun plotX(frequencyHz: Double): Float =
            left + ((ln(frequencyHz / MIN_HZ) / LOG_SPAN).coerceIn(0.0, 1.0) * plotWidth).toFloat()

        fun plotY(db: Double): Float =
            top + ((DISPLAY_DB - db.coerceIn(-DISPLAY_DB, DISPLAY_DB)) /
                (2 * DISPLAY_DB) * plotHeight).toFloat()

        val curve = Path()
        for (index in 0..SAMPLES) {
            val frequency = MIN_HZ * exp(LOG_SPAN * index / SAMPLES)
            val x = plotX(frequency)
            val y = plotY(response.dbAt(frequency))
            if (index == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
        }

        // The area between the curve and the floor of the plot, fading out as it descends: it reads
        // as the curve's own shadow, not as a band tied to 0 dB.
        val area = Path().apply {
            addPath(curve)
            lineTo(size.width, top + plotHeight)
            lineTo(left, top + plotHeight)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                0f to colors.primary.copy(alpha = 0.34f),
                1f to Color.Transparent,
                startY = top,
                endY = top + plotHeight
            )
        )

        for (db in DB_LABELS) {
            val y = plotY(db)
            drawLine(
                color = colors.outlineVariant.copy(alpha = if (db == 0.0) 0.9f else 0.3f),
                start = Offset(left, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            val label = if (db > 0) "+${db.toInt()}" else db.toInt().toString()
            val measured = textMeasurer.measure(label, labelStyle)
            drawText(measured, topLeft = Offset(0f, y - measured.size.height / 2f))
        }

        for (frequency in GRID_HZ) {
            val x = plotX(frequency)
            drawLine(
                color = colors.outlineVariant.copy(alpha = 0.18f),
                start = Offset(x, top),
                end = Offset(x, top + plotHeight),
                strokeWidth = 1f
            )
        }

        // Every band's fader track, drawn before the curve so the curve reads on top.
        for (band in profile.bands) {
            val selected = band.id == selectedBandId
            val x = plotX(band.frequencyHz)
            val trackWidth = 3.dp.toPx()
            drawRoundRect(
                color = if (selected) colors.primary.copy(alpha = 0.55f)
                else colors.outlineVariant.copy(alpha = 0.55f),
                topLeft = Offset(x - trackWidth / 2f, top),
                size = Size(trackWidth, plotHeight),
                cornerRadius = CornerRadius(trackWidth / 2f)
            )
            val zeroY = plotY(0.0)
            val heard = response.dbAt(band.frequencyHz)
            val handleY = plotY(heard)
            drawRoundRect(
                color = if (selected) colors.primary else colors.secondary,
                topLeft = Offset(x - trackWidth / 2f, minOf(zeroY, handleY)),
                size = Size(trackWidth, abs(handleY - zeroY)),
                cornerRadius = CornerRadius(trackWidth / 2f)
            )
        }

        drawPath(curve, colors.primary, style = Stroke(width = 3f))

        // Labels last: gains above each track, fixed frequencies below, skipped when they collide.
        val labelGap = 8.dp.toPx()
        val gainsShown = HashSet<Int>()
        val frequenciesShown = HashSet<Int>()
        var topEdge = -Float.MAX_VALUE
        var bottomEdge = -Float.MAX_VALUE

        val ordered = profile.bands.withIndex().sortedBy { plotX(it.value.frequencyHz) }
        for ((index, band) in ordered) {
            val forced = band.id == selectedBandId
            val x = plotX(band.frequencyHz)

            val gainWidth = textMeasurer.measure(gainLabels[index], labelStyle).size.width.toFloat()
            val gainFits = x - gainWidth / 2f >= topEdge + labelGap && x + gainWidth / 2f <= size.width
            if (forced || gainFits) {
                gainsShown.add(index)
                topEdge = maxOf(topEdge, x + gainWidth / 2f)
            }

            val frequencyText = formatFrequency(band.frequencyHz)
            val frequencyWidth =
                textMeasurer.measure(frequencyText, labelStyle).size.width.toFloat()
            val frequencyFits = x - frequencyWidth / 2f >= bottomEdge + labelGap &&
                x + frequencyWidth / 2f <= size.width
            if (forced || frequencyFits) {
                frequenciesShown.add(index)
                bottomEdge = maxOf(bottomEdge, x + frequencyWidth / 2f)
            }
        }

        profile.bands.forEachIndexed { index, band ->
            val selected = band.id == selectedBandId
            val x = plotX(band.frequencyHz)
            val y = plotY(response.dbAt(band.frequencyHz))

            drawCircle(
                color = if (selected) colors.primary else colors.secondary,
                radius = if (selected) 13f else 9f,
                center = Offset(x, y)
            )
            drawCircle(
                color = colors.surface,
                radius = if (selected) 7f else 4.5f,
                center = Offset(x, y)
            )

            if (index in gainsShown) {
                drawCentredText(
                    textMeasurer = textMeasurer,
                    text = gainLabels[index],
                    centreX = x,
                    top = 0f,
                    style = if (selected) selectedLabelStyle else labelStyle
                )
            }
            if (index in frequenciesShown) {
                drawCentredText(
                    textMeasurer = textMeasurer,
                    text = formatFrequency(band.frequencyHz),
                    centreX = x,
                    top = top + plotHeight + 4.dp.toPx(),
                    style = if (selected) selectedLabelStyle else labelStyle
                )
            }
        }
    }
}

private fun DrawScope.drawCentredText(
    textMeasurer: TextMeasurer,
    text: String,
    centreX: Float,
    top: Float,
    style: androidx.compose.ui.text.TextStyle
) {
    val measured = textMeasurer.measure(text, style)
    val x = (centreX - measured.size.width / 2f)
        .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
    drawText(measured, topLeft = Offset(x, top))
}

private fun formatGain(db: Double): String = String.format(Locale.US, "%.1f", db)

private fun formatFrequency(hz: Double): String {
    val kilo = hz >= 1_000.0
    val value = if (kilo) hz / 1_000.0 else hz
    val number = if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
    return if (kilo) "${number}k" else number
}

private val CurveHeight = 300.dp
private val LabelWidth = 24.dp
private val TopBand = 20.dp
private val BottomBand = 22.dp
private const val MIN_HZ = 20.0
private const val MAX_HZ = 20_000.0
private val LOG_SPAN = ln(MAX_HZ / MIN_HZ)
private const val DISPLAY_DB = 13.5
private const val SAMPLE_RATE_HZ = 48_000.0
private const val SAMPLES = 160
private val GRID_HZ = listOf(100.0, 1_000.0, 10_000.0)
private val DB_LABELS = listOf(9.0, 6.0, 3.0, 0.0, -3.0, -6.0, -9.0)
