/*
 * Copyright 2023 Vivek Singh
 *
 * @Author Vivek Singh
 * @Author Christian Schabesberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Original code was taken from: https://github.com/2307vivek/Seeker/
 *
 */

package net.newpipe.newplayer.ui.seeker

import androidx.annotation.FloatRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import kotlin.math.atan2

/**
 * A seekbar/slider with support for read ahead indicator and segments. The segments can be
 * separated with gaps in between or with their respective colors, or by both.
 *
 * Read ahead indicator shows the amount of content which is ready to use.
 *
 * @param modifier modifiers for the seeker layout
 * @param state state for Seeker
 * @param value current value of the seeker. If outside of [range] provided, value will be
 * coerced to this range.
 * @param thumbValue current value of the thumb. This allows the thumb to move independent of the
 * progress position. If outside of [range] provided, value will be coerced to this range.
 * @param progressStartPosition starting point of the indicator as a fraction of track width.
 * The passed value will be clamped between 0 and 1.
 * @param readAheadValue the read ahead value for seeker. If outside of [range] provided, value will be
 * coerced to this range.
 * @param onValueChange lambda in which value should be updated
 * @param onValueChangeFinished lambda to be invoked when value change has ended. This callback
 * shouldn't be used to update the slider value (use [onValueChange] for that), but rather to
 * know when the user has completed selecting a new value by ending a drag or a click.
 * @param segments a list of [Segment] for seeker. The track will be divided into different parts based
 * on the provided start values.
 * The first segment must start form the start value of the [range], and all the segments must lie in
 * the specified [range], else an [IllegalArgumentException] will be thrown.
 * will be thrown.
 * @param enabled whether or not component is enabled and can be interacted with or not
 * @param colors [SeekerColors] that will be used to determine the color of the Slider parts in
 * different state. See [SeekerDefaults.seekerColors] to customize.
 * @param dimensions [SeekerDimensions] that will be used to determine the dimensions of
 * different Seeker parts in different state. See [SeekerDefaults.seekerDimensions] to customize.
 * @param interactionSource the [MutableInteractionSource] representing the stream of
 * [Interaction]s for this Seeker. You can create and pass in your own remembered
 * [MutableInteractionSource] if you want to observe [Interaction]s and customize the
 * appearance / behavior of this Seeker in different [Interaction]s.
 * */
@Composable

/** @hide */
internal fun Seeker(
    modifier: Modifier = Modifier,
    state: SeekerState = rememberSeekerState(),
    value: Float,
    thumbValue: Float = value,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    @FloatRange(from = 0.0, to = 1.0)
    progressStartPosition: Float = 0f,
    readAheadValue: Float = lerp(range.start, range.endInclusive, progressStartPosition),
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    segments: List<Segment> = emptyList(),
    chapterSegments: List<ChapterSegment> = emptyList(),
    enabled: Boolean = true,
    colors: SeekerColors = SeekerDefaults.seekerColors(),
    dimensions: SeekerDimensions = SeekerDefaults.seekerDimensions(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    if (segments.isNotEmpty()) {
        segments.forEach {
            require(it.start in range && it.end in range) {
                "segment must lie withing the range: segment: ${it.name} start: ${it.start}, end: ${it.end}, range: ${range}"
            }
        }
    }


    if (chapterSegments.isNotEmpty()) {
        chapterSegments.forEach {
            require(it.start in range) {
                "chapter segment must lie withing the range: segment: ${it.name} start: ${it.start} range: ${range}"
            }
        }
    }

    val onValueChangeState by rememberUpdatedState(onValueChange)

    BoxWithConstraints(
        modifier = modifier
            .requiredSizeIn(
                minHeight = SeekerDefaults.ThumbRippleRadius * 2,
                minWidth = SeekerDefaults.ThumbRippleRadius * 2
            )
            .progressSemantics(value, range, onValueChange, onValueChangeFinished, enabled)
            .focusable(enabled, interactionSource)
    ) {
        val thumbRadius by dimensions.thumbRadius()
        val trackStart: Float
        val endPx = constraints.maxWidth.toFloat()
        val widthPx: Float

        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

        with(LocalDensity.current) {
            trackStart = thumbRadius.toPx()
            widthPx = endPx - (trackStart * 2)
        }

        val segmentStarts = remember(segments, range, widthPx) {
            segmentToPxValues(segments, range, widthPx)
        }

        val chapterSegmentsPx = remember(chapterSegments, range, widthPx) {
            chapterSegmentToPxValues(chapterSegments, range, widthPx)
        }

        LaunchedEffect(thumbValue, segments) {
            state.currentSegment(thumbValue, segments)
        }

        val valuePx = remember(value, widthPx, range) {
            valueToPx(value, widthPx, range)
        }

        val thumbValuePx = remember(thumbValue, widthPx, range) {
            when (thumbValue) {
                value -> valuePx // reuse valuePx if thumbValue equal to value
                else -> valueToPx(thumbValue, widthPx, range)
            }
        }

        val readAheadValuePx = remember(readAheadValue, widthPx, range) {
            valueToPx(readAheadValue, widthPx, range)
        }

        var dragPositionX by remember { mutableStateOf(0f) }
        var pressOffset by remember { mutableStateOf(0f) }

        val scope = rememberCoroutineScope()

        val draggableState = state.draggableState

        LaunchedEffect(widthPx, range) {
            state.onDrag = {
                dragPositionX += it + pressOffset

                pressOffset = 0f
                onValueChangeState(pxToValue(dragPositionX, widthPx, range))
            }
        }

        val press =
            Modifier.pointerInput(
                range,
                widthPx,
                endPx,
                isRtl,
                enabled,
                thumbRadius,
                interactionSource
            ) {
                detectTapGestures(
                    onPress = { position ->
                        dragPositionX = 0f
                        pressOffset =
                            if (!isRtl) position.x - trackStart else (endPx - position.x) - trackStart
                    },
                    onTap = {
                        scope.launch {
                            draggableState.drag(MutatePriority.UserInput) {
                                dragBy(0f)
                            }
                            onValueChangeFinished?.invoke()
                        }
                    }
                )
            }

        val drag = Modifier.draggable(
            state = draggableState,
            reverseDirection = isRtl,
            orientation = Orientation.Horizontal,
            onDragStopped = {
                onValueChangeFinished?.invoke()
            },
            interactionSource = interactionSource
        )

        Seeker(
            modifier = if (enabled) press.then(drag) else Modifier,
            widthPx = widthPx,
            valuePx = valuePx,
            thumbValuePx = thumbValuePx,
            progressStartPosition = progressStartPosition.coerceIn(0f, 1f),
            readAheadValuePx = readAheadValuePx,
            enabled = enabled,
            segments = segmentStarts,
            chapterSegments = chapterSegmentsPx,
            colors = colors,
            dimensions = dimensions,
            interactionSource = interactionSource
        )
    }
}

@Composable
private fun Seeker(
    modifier: Modifier,
    widthPx: Float,
    valuePx: Float,
    thumbValuePx: Float,
    progressStartPosition: Float,
    readAheadValuePx: Float,
    enabled: Boolean,
    segments: List<SegmentPxs>,
    chapterSegments: List<SegmentPxs>,
    colors: SeekerColors,
    dimensions: SeekerDimensions,
    interactionSource: MutableInteractionSource
) {
    Box(
        modifier = modifier.defaultSeekerDimensions(dimensions),
        contentAlignment = Alignment.CenterStart
    ) {
        Track(
            modifier = Modifier.fillMaxSize(),
            enabled = enabled,
            segments = segments,
            colors = colors,
            widthPx = widthPx,
            valuePx = valuePx,
            progressStartPosition = progressStartPosition,
            readAheadValuePx = readAheadValuePx,
            dimensions = dimensions
        )
        Thumb(
            valuePx = { thumbValuePx },
            dimensions = dimensions,
            colors = colors,
            enabled = enabled,
            interactionSource = interactionSource
        )
        ChapterDots(
            modifier = Modifier.fillMaxSize(),
            chapterSegments = chapterSegments,
            widthPx = widthPx,
            dimensions = dimensions
        )
    }
}

@Composable
private fun ChapterDots(
    modifier: Modifier,
    chapterSegments: List<SegmentPxs>,
    widthPx: Float,
    dimensions: SeekerDimensions
) {
    val thumbRadius by dimensions.thumbRadius()
    val trackHeight by dimensions.trackHeight()

    Canvas(
        modifier = modifier.graphicsLayer {
            alpha = 1.0f
        }
    ) {
        val isRtl = layoutDirection == LayoutDirection.Rtl
        val left = thumbRadius.toPx()

        translate(left = left) {
            // draw chapters
            for (index in chapterSegments.indices) {
                val segment = chapterSegments[index]
                drawDot(
                    x = rtlAware(segment.startPx, widthPx, isRtl),
                    trackHeight = trackHeight.toPx(),
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun Track(
    modifier: Modifier,
    enabled: Boolean,
    segments: List<SegmentPxs>,
    colors: SeekerColors,
    widthPx: Float,
    valuePx: Float,
    progressStartPosition: Float,
    readAheadValuePx: Float,
    dimensions: SeekerDimensions
) {
    val trackColor by colors.trackColor(enabled)
    val progressColor by colors.progressColor(enabled)
    val readAheadColor by colors.readAheadColor(enabled)
    val thumbRadius by dimensions.thumbRadius()
    val trackHeight by dimensions.trackHeight()
    val progressHeight by dimensions.progressHeight()

    Canvas(
        modifier = modifier.graphicsLayer {
            alpha = 1.0f
        }
    ) {
        val isRtl = layoutDirection == LayoutDirection.Rtl
        val left = thumbRadius.toPx()

        translate(left = left) {

            // draw the track with a single line.
            drawLine(
                start = Offset(rtlAware(0f, widthPx, isRtl), center.y),
                end = Offset(rtlAware(widthPx, widthPx, isRtl), center.y),
                color = trackColor,
                strokeWidth = trackHeight.toPx(),
                cap = StrokeCap.Round
            )


            // readAhead indicator
            drawLine(
                start = Offset(rtlAware(widthPx * progressStartPosition, widthPx, isRtl), center.y),
                end = Offset(rtlAware(readAheadValuePx, widthPx, isRtl), center.y),
                color = readAheadColor,
                strokeWidth = progressHeight.toPx(),
                cap = StrokeCap.Round
            )

            // progress indicator
            drawLine(
                start = Offset(rtlAware(widthPx * progressStartPosition, widthPx, isRtl), center.y),
                end = Offset(rtlAware(valuePx, widthPx, isRtl), center.y),
                color = progressColor,
                strokeWidth = progressHeight.toPx(),
                cap = StrokeCap.Round
            )

            // draw segments in their respective color,
            for (index in segments.indices) {
                val segment = segments[index]
                val segmentColor = when (segment.color) {
                    Color.Unspecified -> trackColor
                    else -> segment.color
                }
                drawSegment(
                    startPx = rtlAware(segment.startPx, widthPx, isRtl),
                    endPx = rtlAware(segment.endPx, widthPx, isRtl),
                    trackColor = segmentColor,
                    trackHeight = trackHeight.toPx(),
                    blendMode = BlendMode.SrcOver,
                )
            }
        }
    }
}

private fun DrawScope.drawLine(
    color: Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float = Stroke.HairlineWidth,
    startCap: StrokeCap? = null,
    endCap: StrokeCap? = null,
    blendMode: BlendMode
) {
    val endOffset = if (endCap != null) {
        end.copy(x = end.x - strokeWidth)
    } else {
        end
    }
    inset(horizontal = strokeWidth / 2) {
        drawLine(
            color = color,
            start = start,
            end = endOffset,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt,
        )

        startCap?.let {
            drawCap(
                color = color,
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = it,
                blendMode = blendMode
            )
        }

        endCap?.let {
            drawCap(
                color = color,
                start = endOffset,
                end = start,
                strokeWidth = strokeWidth,
                cap = it,
                blendMode = blendMode
            )
        }
    }
}

private fun DrawScope.drawCap(
    color: Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float = Stroke.HairlineWidth,
    cap: StrokeCap,
    blendMode: BlendMode
) {
    when (cap) {
        StrokeCap.Butt -> Unit
        StrokeCap.Round -> {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = start - Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(strokeWidth, strokeWidth),
                blendMode = blendMode,
            )
        }

        StrokeCap.Square -> {
            val offset = Offset(strokeWidth / 2, strokeWidth / 2)
            val size = Size(strokeWidth, strokeWidth)

            rotateRad(
                radians = (end - start).run { atan2(x, y) },
                pivot = start
            ) {
                drawRect(color, topLeft = start - offset, size = size, blendMode = blendMode)
            }
        }
    }
}

private fun DrawScope.drawSegment(
    startPx: Float,
    endPx: Float,
    trackColor: Color,
    trackHeight: Float,
    blendMode: BlendMode,
) {
    drawLine(
        start = Offset(startPx, center.y),
        end = Offset(endPx, center.y),
        color = trackColor,
        strokeWidth = trackHeight,
        blendMode = blendMode,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawGap(
    startPx: Float,
    endPx: Float,
    trackHeight: Float,
) {
    drawLine(
        start = Offset(startPx, center.y),
        end = Offset(endPx, center.y),
        color = Color.Black, // any color will do
        strokeWidth = trackHeight + 2, // add 2 to prevent hairline borders from rounding
        blendMode = BlendMode.Clear
    )
}

private fun DrawScope.drawDot(
    x: Float,
    trackHeight: Float,
    color: Color = Color.Gray.copy(alpha = 0.9f)
) {
    drawCircle(
        radius = (trackHeight / 2f) * 0.8f,
        center = Offset(x = x, y = center.y),
        color = color,
        blendMode = BlendMode.SrcOver
    )
}

@Composable
private fun Thumb(
    valuePx: () -> Float,
    dimensions: SeekerDimensions,
    colors: SeekerColors,
    enabled: Boolean,
    interactionSource: MutableInteractionSource
) {
    Spacer(
        modifier = Modifier
            .offset {
                IntOffset(x = valuePx().toInt(), 0)
            }
            .indication(
                interactionSource = interactionSource,
                indication = rememberRipple(
                    bounded = false,
                    radius = SeekerDefaults.ThumbRippleRadius
                )
            )
            .hoverable(interactionSource)
            .size(dimensions.thumbRadius().value * 2)
            .clip(CircleShape)
            .background(colors.thumbColor(enabled = enabled).value)
    )
}

private fun Modifier.defaultSeekerDimensions(dimensions: SeekerDimensions) = composed {
    this.then(
        Modifier
            .heightIn(
                max = (dimensions.thumbRadius().value * 2).coerceAtLeast(SeekerDefaults.MinSliderHeight)
            )
            .widthIn(
                min = SeekerDefaults.MinSliderWidth
            )
    )
}

private fun Modifier.progressSemantics(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean
): Modifier {
    val coerced = value.coerceIn(range.start, range.endInclusive)
    return semantics {
        if (!enabled) disabled()
        setProgress { targetValue ->
            val newValue = targetValue.coerceIn(range.start, range.endInclusive)

            if (newValue == coerced) {
                false
            } else {
                onValueChange(newValue)
                onValueChangeFinished?.invoke()
                true
            }
        }
    }.progressSemantics(value, range, 0)
}

@Preview(showBackground = true)
@Composable
/** @hide */
internal fun SeekerPreview() {
    val segments = listOf(
        Segment(name = "Intro", start = 0.1f, end = 0.3f, color = Color.Green),
        Segment(name = "Talk 1", start = 0.5f, end = 0.6f, color = Color.Cyan),
        Segment(name = "Talk 2", start = 0.8f, end = 0.85f, color = Color.Blue),
    )

    val chapterSegments = listOf(
        ChapterSegment(name = "Intro", start = 0.0f, color = Color.Green),
        ChapterSegment(name = "Talk 1", start = 0.55f, color = Color.Cyan),
        ChapterSegment(name = "Talk 2", start = 0.9f, color = Color.Blue),
    )
    Seeker(
        value = 0.55f,
        range = 0f..1f,
        segments = segments,
        chapterSegments = chapterSegments,
        onValueChange = { },
    )
}