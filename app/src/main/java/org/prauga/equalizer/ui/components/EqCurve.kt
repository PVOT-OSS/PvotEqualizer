package org.prauga.equalizer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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
import androidx.compose.ui.unit.Dp
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
 * @param onTargetGain Called continuously while a handle is dragged
 * @param onResetBand Called when a band is held, to put it back to 0 dB
 * @param modifier Modifier for the control
 * @param height How tall the plot is; the screen decides so it fits the device
 */
@Composable
fun EqCurve(
    profile: EqProfile,
    selectedBandId: Int?,
    onSelectBand: (Int) -> Unit,
    onTargetGain: (id: Int, gainDb: Double) -> Unit,
    onResetBand: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = CurveHeight
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
    val changeGain by rememberUpdatedState(onTargetGain)
    val resetBand by rememberUpdatedState(onResetBand)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                val touchSlop = 22.dp.toPx()

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

                // Anywhere across the band's column grabs it, not just the handle: the handles are
                // small targets and the track is what the eye aims at.
                fun bandAtX(x: Float): ParametricBand? {
                    val nearest = bands.minByOrNull { abs(plotX(it.frequencyHz) - x) }
                        ?: return null
                    val distance = abs(plotX(nearest.frequencyHz) - x)
                    val reach = maxOf(touchSlop, (size.width - LabelWidth.toPx()) /
                        (bands.size * 1.7f))
                    return if (distance <= reach) nearest else null
                }

                fun gainAt(y: Float): Double {
                    val top = TopBand.toPx()
                    val plotHeight = size.height - top - BottomBand.toPx()
                    val fraction = ((y - top) / plotHeight).coerceIn(0f, 1f)
                    return DISPLAY_DB - fraction.toDouble() * (2 * DISPLAY_DB)
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val band = bandAtX(down.position.x) ?: return@awaitEachGesture
                    selectBand(band.id)
                    down.consume()

                    // Holding still on a band zeroes it; moving drags it.
                    val held = awaitLongPressOrCancellation(down.id)
                    if (held != null) {
                        resetBand(band.id)
                        held.consume()
                        return@awaitEachGesture
                    }

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

        // Labels last: gains above each track, frequencies below. Two rows each, so ten bands can
        // still be read instead of dropping half the labels.
        val labelGap = 6.dp.toPx()
        val gainRowTop = listOf(0f, 14.dp.toPx())
        val frequencyRowTop = listOf(
            top + plotHeight + 3.dp.toPx(),
            top + plotHeight + 18.dp.toPx()
        )
        val gainRowEdge = MutableList(gainRowTop.size) { -Float.MAX_VALUE }
        val frequencyRowEdge = MutableList(frequencyRowTop.size) { -Float.MAX_VALUE }
        val gainRowOf = HashMap<Int, Int>()
        val frequencyRowOf = HashMap<Int, Int>()

        val ordered = profile.bands.withIndex().sortedBy { plotX(it.value.frequencyHz) }

        fun place(
            index: Int,
            centreX: Float,
            width: Float,
            rowEdge: MutableList<Float>,
            rowOf: MutableMap<Int, Int>
        ) {
            for (row in rowEdge.indices) {
                if (centreX - width / 2f >= rowEdge[row] + labelGap) {
                    rowOf[index] = row
                    rowEdge[row] = centreX + width / 2f
                    return
                }
            }
            // A selected band keeps its label even when the row is full.
            if (index == ordered.firstOrNull { it.value.id == selectedBandId }?.index) {
                rowOf[index] = 0
            }
        }

        for ((index, band) in ordered) {
            val x = plotX(band.frequencyHz)
            place(
                index,
                x,
                textMeasurer.measure(gainLabels[index], labelStyle).size.width.toFloat(),
                gainRowEdge,
                gainRowOf
            )
            place(
                index,
                x,
                textMeasurer.measure(formatFrequency(band.frequencyHz), labelStyle)
                    .size.width.toFloat(),
                frequencyRowEdge,
                frequencyRowOf
            )
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

            gainRowOf[index]?.let { row ->
                drawCentredText(
                    textMeasurer = textMeasurer,
                    text = gainLabels[index],
                    centreX = x,
                    top = gainRowTop[row],
                    style = if (selected) selectedLabelStyle else labelStyle
                )
            }
            frequencyRowOf[index]?.let { row ->
                drawCentredText(
                    textMeasurer = textMeasurer,
                    text = formatFrequency(band.frequencyHz),
                    centreX = x,
                    top = frequencyRowTop[row],
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

val CurveHeight = 300.dp
private val LabelWidth = 24.dp
private val TopBand = 30.dp
private val BottomBand = 34.dp
private const val MIN_HZ = 20.0
private const val MAX_HZ = 20_000.0
private val LOG_SPAN = ln(MAX_HZ / MIN_HZ)
private const val DISPLAY_DB = 13.5
private const val SAMPLE_RATE_HZ = 48_000.0
private const val SAMPLES = 160
private val GRID_HZ = listOf(100.0, 1_000.0, 10_000.0)
private val DB_LABELS = listOf(9.0, 6.0, 3.0, 0.0, -3.0, -6.0, -9.0)
