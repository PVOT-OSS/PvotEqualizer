package org.prauga.equalizer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.slider.PvotSliderDefaults
import kotlin.math.abs
import kotlin.math.min

/**
 * The gain slider used under the curve.
 *
 * It keeps PvotSlider's measurements but fills from the zero mark outward, marks where zero is, and
 * gives the thumb the same dot-and-ring shape as the handles on the curve, so the two controls read
 * as one instrument rather than a chart above a settings row.
 *
 * @param value Current value
 * @param valueRange Allowed range; [zeroValue] is where the fill starts
 * @param onValueChange Called with the value under the finger
 * @param zeroValue The value the fill grows from, usually 0 dB
 */
@Composable
fun EqSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    zeroValue: Float = 0f
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }

    // The gesture handlers outlive a single composition, so read the live value through a state.
    val liveValue by rememberUpdatedState(value)

    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    fun fractionOf(target: Float): Float =
        ((target - valueRange.start) / span).coerceIn(0f, 1f)

    fun emit(target: Float) {
        val clamped = target.coerceIn(valueRange.start, valueRange.endInclusive)
        val wasPositive = liveValue >= zeroValue
        val isPositive = clamped >= zeroValue
        if (wasPositive != isPositive) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onValueChange(clamped)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(PvotSliderDefaults.TouchHeight)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, 0)
                setProgress { target ->
                    emit(target.coerceIn(valueRange.start, valueRange.endInclusive))
                    true
                }
            }
            .pointerInput(valueRange, zeroValue) {
                detectTapGestures { offset ->
                    emit(valueRange.start + (offset.x / size.width).coerceIn(0f, 1f) * span)
                }
            }
            .pointerInput(valueRange, zeroValue) {
                detectHorizontalDragGestures(
                    onDragStart = { pressed = true },
                    onDragEnd = { pressed = false },
                    onDragCancel = { pressed = false }
                ) { change, _ ->
                    change.consume()
                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    emit(valueRange.start + fraction * span)
                }
            }
    ) {
        val trackHeight = PvotSliderDefaults.TrackHeight.toPx()
        val baseThumb = PvotSliderDefaults.ThumbSize.toPx() / 2f
        val thumbRadius = baseThumb *
            (if (pressed) PvotSliderDefaults.PressedThumbScale else 1f)
        val inset = baseThumb + 4.dp.toPx()
        val usable = (size.width - inset * 2f).coerceAtLeast(1f)
        val centerY = size.height / 2f
        val thumbX = inset + fractionOf(value) * usable
        val zeroX = inset + fractionOf(zeroValue) * usable
        val trackRadius = CornerRadius(trackHeight / 2f)

        drawRoundRect(
            color = colors.onSurface.copy(alpha = 0.14f),
            topLeft = Offset(inset, centerY - trackHeight / 2f),
            size = Size(usable, trackHeight),
            cornerRadius = trackRadius
        )

        val fromZero = fractionOf(value) >= fractionOf(zeroValue)
        val fillLeft = if (fromZero) zeroX else thumbX
        val fillRight = if (fromZero) thumbX else zeroX
        if (fillRight - fillLeft > 0.5f) {
            drawRoundRect(
                color = colors.primary,
                topLeft = Offset(fillLeft, centerY - trackHeight / 2f),
                size = Size(fillRight - fillLeft, trackHeight),
                cornerRadius = trackRadius
            )
        }

        // The zero mark: gain moves either side of it, so it has to be findable by eye.
        val tickHalf = 6.dp.toPx()
        drawLine(
            color = colors.onSurface.copy(alpha = 0.45f),
            start = Offset(zeroX, centerY - tickHalf),
            end = Offset(zeroX, centerY + tickHalf),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawCircle(
            color = colors.primary.copy(alpha = 0.28f),
            radius = thumbRadius + 6.dp.toPx(),
            center = Offset(thumbX, centerY)
        )
        drawCircle(
            color = colors.onSurface,
            radius = thumbRadius,
            center = Offset(thumbX, centerY)
        )
        drawCircle(
            color = colors.primary,
            radius = thumbRadius - 1.5.dp.toPx(),
            center = Offset(thumbX, centerY)
        )
    }
}
