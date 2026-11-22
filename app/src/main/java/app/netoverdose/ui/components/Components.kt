package app.netoverdose.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
// API existence is proven by compilation. What is NOT proven is the visual result of the
// tiling: that Repeated over spectrum + first() hides the seam. That needs eyes on a device.
// VERIFY (visual only): no single-colour stretch appears on the strip at any phase.
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.netoverdose.design.LocalDesign
import app.netoverdose.domain.WindowProgress
import app.netoverdose.ui.theme.MonoFamily
import app.netoverdose.ui.theme.SansFamily
import app.netoverdose.ui.theme.SerifFamily
import java.util.Locale
import kotlin.math.roundToInt

private fun upper(value: String): String = value.uppercase(Locale.forLanguageTag("ru"))

/** Cold field, optional drafting grid, grain and a soft spectral glow. */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    val palette = LocalDesign.current
    Box(Modifier.fillMaxSize().background(palette.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            drawBackdrop(palette.gridAlpha, palette.glowAlpha, palette.spectrum)
            drawGrain(palette.grainAlpha)
        }
        Box(Modifier.fillMaxSize(), content = content)
    }
}

private fun DrawScope.drawBackdrop(gridAlpha: Float, glowAlpha: Float, spectrum: List<Color>) {
    val step = 24.dp.toPx()
    if (gridAlpha > 0f) {
        val grid = Color.Black.copy(alpha = gridAlpha)
        var x = 0f
        while (x < size.width) {
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }
    if (glowAlpha > 0f) {
        val radius = size.minDimension * 0.7f
        spectrum.forEachIndexed { index, color ->
            val fraction = index.toFloat() / (spectrum.size - 1).coerceAtLeast(1)
            drawCircle(
                color = color.copy(alpha = glowAlpha * 0.12f),
                radius = radius * (1f - fraction * 0.5f),
                center = Offset(size.width * (0.15f + fraction * 0.7f), size.height * 0.08f)
            )
        }
    }
}

private fun DrawScope.drawGrain(alpha: Float) {
    if (alpha <= 0f) return
    val dot = 1.dp.toPx()
    val step = 5.dp.toPx()
    val color = Color.Black.copy(alpha = alpha)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else step / 2f
        while (x < size.width) {
            drawRect(color, Offset(x, y), Size(dot, dot))
            x += step
        }
        y += step
        row++
    }
}

/**
 * 32-column waveform: the filled part tracks elapsed window share. A spectral sweep
 * plus a slow hue drift keep the bars visibly alive (B5).
 */
@Composable
fun WindowWave(
    windowStart: Long,
    deadline: Long,
    now: Long,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp
) {
    val palette = LocalDesign.current
    val filled = WindowProgress.filledColumns(windowStart, deadline, now)
    val percent = (WindowProgress.fraction(windowStart, deadline, now) * 100).roundToInt()
    val description = androidx.compose.ui.res.stringResource(app.netoverdose.R.string.wave_cd, percent)
    val duration = palette.waveDurationMs

    // Slow drift of the filled columns' hue; disabled when the wave speed token is 0.
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (duration > 0) duration else 1, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    // Slow pulse for the "now" edge marker.
    val edgePulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (duration > 0) 1800 else 1, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "edgePulse"
    )

    Canvas(
        modifier.fillMaxWidth().height(height)
            .semantics { contentDescription = description }
    ) {
        val count = WindowProgress.COLUMNS
        val gap = 2.dp.toPx()
        val columnWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val overdue = deadline < now
        val hatch = palette.hatch && overdue

        for (index in 0 until count) {
            val barHeight = size.height * WindowProgress.HEIGHTS[index] / 100f
            val left = index * (columnWidth + gap)
            val top = size.height - barHeight
            val active = index < filled
            val color = if (active) {
                // drift the spectrum so the filled part breathes
                val shift = if (duration > 0) (phase * palette.waveColors.size).toInt() else 0
                palette.waveColors[(index + shift) % palette.waveColors.size]
            } else {
                palette.off.copy(alpha = 0.55f)
            }
            drawRect(color, Offset(left, top), Size(columnWidth, barHeight))

            // Burnt window: finished columns get a diagonal danger hatch instead of flat colour.
            if (active && hatch) {
                val stripe = 2.dp.toPx()
                val period = 5.dp.toPx()
                clipRect(left, top, left + columnWidth, size.height) {
                    var offset = -barHeight
                    while (offset < barHeight) {
                        drawLine(
                            color = palette.danger,
                            start = Offset(left + offset, size.height),
                            end = Offset(left + offset + barHeight, top),
                            strokeWidth = stripe
                        )
                        offset += period
                    }
                }
            }
        }

        // Edge marker: the exact moment "now" sits at, pulsing like the reference.
        if (filled in 1 until count) {
            val edgeX = filled * (columnWidth + gap) - gap / 2f
            drawLine(
                color = palette.ink.copy(alpha = 0.25f + 0.45f * edgePulse),
                start = Offset(edgeX, -2.dp.toPx()),
                end = Offset(edgeX, size.height + 2.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

/**
 * Single source of the travelling phase for the whole app.
 *
 * One infinite transition is hoisted to the composition root and shared by every rainbow
 * element. Giving each element its own transition created a separate animation loop per
 * call site, which stalled the UI thread for seconds ("Skipped 307 frames").
 *
 * The value runs 0..1 and is used as a *fraction of the element width*, never as a pixel
 * offset: a pixel span made the loop point land inside the visible strip.
 */
val LocalSpectrumShift = compositionLocalOf { 0f }

@Composable
fun SpectrumShiftProvider(
    durationMs: Int,
    content: @Composable () -> Unit
) {
    if (durationMs <= 0) {
        CompositionLocalProvider(LocalSpectrumShift provides 0f, content = content)
        return
    }
    val transition = rememberInfiniteTransition(label = "spectrum")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spectrumShift"
    )
    CompositionLocalProvider(LocalSpectrumShift provides shift, content = content)
}

@Composable
fun rememberSpectrumShift(): Float = LocalSpectrumShift.current

/**
 * Repeating linear gradient over the design spectrum, phase-shifted by [shift].
 *
 * The pattern period equals the element width, and the shift over one full cycle equals
 * that same period, so `shift = 1` renders exactly the frame `shift = 0` renders. That
 * equality is what removes the seam.
 *
 * The previous version scaled the offset by `n/(n+1)` to line up the closing colour. With
 * `TileMode.Clamp` that made the travelled distance 8/9 of the period, so by the end of a
 * cycle 89% of the strip was painted flat with the last spectrum colour, then snapped back
 * to the start. TileMode.Repeated makes the decelerating ramp unnecessary.
 */
fun spectrumBrush(
    spectrum: List<Color>,
    shift: Float,
    sizePx: Float
): Brush {
    if (spectrum.isEmpty()) return SolidColor(Color.Transparent)
    // The closing colour repeats the first one so the ramp wraps without a hard cut.
    val colors = spectrum + spectrum.first()
    val span = sizePx.coerceAtLeast(1f)
    val offset = -shift * span
    return Brush.linearGradient(
        colors = colors,
        start = Offset(offset, 0f),
        end = Offset(offset + span, 0f),
        tileMode = TileMode.Repeated
    )
}

/**
 * Animated spectrum sized to a fixed span, for text brushes that cannot measure themselves.
 */
@Composable
fun animatedSpectrumBrush(spanPx: Float = 900f): Brush {
    val palette = LocalDesign.current
    val shift = LocalSpectrumShift.current
    return spectrumBrush(palette.spectrum, shift, spanPx)
}

/**
 * Paints the travelling spectrum behind the element, sized to its real width so the loop
 * never drifts. This is the sanctioned way to animate a rainbow on a surface.
 */
@Composable
fun Modifier.spectrumBackground(alpha: Float = 1f): Modifier {
    val spectrum = LocalDesign.current.spectrum
    val shift = LocalSpectrumShift.current
    return this.then(
        Modifier.drawWithContent {
            drawRect(
                spectrumBrush(spectrum, shift, size.width),
                alpha = alpha.coerceIn(0f, 1f)
            )
            drawContent()
        }
    )
}

@Composable
fun RainbowHairline(modifier: Modifier = Modifier, alpha: Float = 1f) {
    // rainbowHard gives the crisp 8-band ribbon from the reference rather than a soft blend.
    Box(modifier.height(2.dp).spectrumBackground(alpha))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val palette = LocalDesign.current
    Text(
        text = upper(text),
        modifier = modifier,
        style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, letterSpacing = 0.9.sp, fontWeight = FontWeight.Bold),
        color = palette.muted
    )
}

@Composable
fun ItalicAccent(
    text: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.TextUnit = 20.sp,
    gradient: Boolean = false
) {
    val palette = LocalDesign.current
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            brush = if (gradient) animatedSpectrumBrush() else SolidColor(palette.ink),
            fontFamily = SerifFamily,
            fontStyle = if (palette.italics) FontStyle.Italic else FontStyle.Normal,
            fontWeight = FontWeight.Bold,
            fontSize = size,
            lineHeight = size * 1.35f
        )
    )
}

@Composable
fun BrandMark(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.TextUnit = 22.sp) {
    val palette = LocalDesign.current
    // "overdose" is gradient text: the same travelling spectrum the rest of the UI uses.
    val brush = animatedSpectrumBrush()

    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            "net",
            style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = size),
            color = palette.ink
        )
        Text(
            "overdose",
            style = TextStyle(
                brush = brush,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontStyle = if (palette.italics) FontStyle.Italic else FontStyle.Normal,
                fontSize = size
            )
        )
    }
}

@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    enabled: Boolean = true
) {
    val palette = LocalDesign.current
    // Press feedback: scale-down plus a synthesised tick, both gated by the motion/sound tokens.
    val haptics = rememberHaptics()
    Box(
        modifier.height(height).clip(palette.shape).pressScale(enabled = enabled)
            .background(if (enabled) palette.accent else palette.off.copy(alpha = 0.55f))
            .clickable(role = Role.Button, enabled = enabled) {
                haptics()
                onClick()
            }
            .semantics { if (!enabled) stateDescription = "disabled" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            upper(text),
            style = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp),
            color = if (enabled) palette.onAccent else palette.muted
        )
    }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    danger: Boolean = false
) {
    val palette = LocalDesign.current
    val tone = if (danger) palette.danger else palette.text
    Box(
        modifier.height(height).clip(palette.shape).border(1.dp, if (danger) palette.danger else palette.rule, palette.shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            upper(text),
            style = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.sp),
            color = tone
        )
    }
}

@Composable
fun MiniButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalDesign.current
    Box(
        modifier.height(38.dp).clip(palette.shape).border(1.dp, palette.rule, palette.shape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            upper(text),
            style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, letterSpacing = 0.7.sp),
            color = palette.muted,
            maxLines = 1
        )
    }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalDesign.current
    Text(
        text,
        modifier = modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 0.7.sp),
        color = palette.accentText
    )
}

@Composable
fun SwatchDot(color: Color, size: Dp = 14.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color).border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape))
}

/** File tile: tile radius, spectral corner fold, danger hatch when the deadline burnt (reference). */
@Composable
fun FileTile(label: String, size: Dp = 44.dp, burnt: Boolean = false) {
    val palette = LocalDesign.current
    // Resolved during composition; Brush is not @Composable and cannot be built in Canvas.
    val foldBrush = animatedSpectrumBrush()
    Box(
        Modifier.size(size).clip(palette.tileShape).background(palette.tile)
            .border(1.dp, palette.rule, palette.tileShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val corner = 15.dp.toPx()
            val path = Path().apply {
                moveTo(this@Canvas.size.width, 0f)
                lineTo(this@Canvas.size.width - corner, 0f)
                lineTo(this@Canvas.size.width, corner)
                close()
            }
            drawPath(path, foldBrush, alpha = palette.rainAlpha)
        }
        if (burnt && palette.hatch) {
            Canvas(Modifier.matchParentSize()) {
                val side = this.size.width
                val step = 4.dp.toPx()
                var offset = -side
                while (offset < side * 2f) {
                    drawLine(
                        color = palette.danger,
                        start = Offset(offset, side),
                        end = Offset(offset + side, 0f),
                        strokeWidth = 2.dp.toPx()
                    )
                    offset += step
                }
            }
        }
        Text(
            label,
            style = TextStyle(
                fontFamily = MonoFamily,
                fontSize = if (size >= 64.dp) 13.sp else 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = palette.ink,
            maxLines = 1
        )
    }
}

/**
 * Single-line sharp input field. Uses SansFamily by default: the bundled mono font
 * has no Cyrillic subset, so typing Russian in MonoFamily renders blanks (B02).
 */
@Composable
fun SharpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    serif: Boolean = false,
    singleLine: Boolean = true
) {
    val palette = LocalDesign.current
    val style = TextStyle(
        fontFamily = if (serif) SerifFamily else SansFamily,
        fontWeight = if (serif) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = if (serif) 16.sp else 14.sp,
        lineHeight = if (singleLine) TextUnit.Unspecified else 19.sp,
        color = palette.ink
    )
    Box(
        modifier.fillMaxWidth()
            .heightIn(min = if (singleLine) 44.dp else 64.dp)
            .clip(palette.shape).background(palette.bg)
            .border(1.dp, palette.rule, palette.shape)
            .padding(horizontal = 10.dp, vertical = if (singleLine) 0.dp else 10.dp),
        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = style,
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (singleLine) ImeAction.Done else ImeAction.Default
            ),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = style.copy(color = palette.muted),
                        maxLines = if (singleLine) 1 else 3
                    )
                }
                inner()
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Sharp rectangle switch: ink track, accent thumb. */
@Composable
fun SharpSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalDesign.current
    val width = 44.dp
    val travel = width - 22.dp
    Box(
        modifier.width(width).height(24.dp).clip(palette.shape)
            .background(if (checked) palette.ink else palette.off.copy(alpha = 0.5f))
            .border(1.dp, if (checked) palette.ink else palette.rule, palette.shape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = if (checked) travel else 2.dp)
                .size(20.dp)
                .background(if (checked) palette.accent else palette.bg)
        )
    }
}

@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalDesign.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { index, option ->
            val active = index == selected
            Box(
                Modifier.weight(1f).height(36.dp).clip(palette.shape)
                    .background(if (active) palette.ink else Color.Transparent)
                    .border(1.dp, if (active) palette.ink else palette.rule, palette.shape)
                    .clickable(role = Role.RadioButton) { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    upper(option),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp, letterSpacing = 0.5.sp),
                    color = if (active) palette.bg else palette.muted,
                    maxLines = 1
                )
            }
        }
    }
}

/** Discrete slider drawn without Material ripple so the sharp style holds. */
@Composable
fun SharpSlider(
    value: Float,
    min: Float,
    max: Float,
    onValue: (Float) -> Unit,
    modifier: Modifier = Modifier,
    rainbowTrack: Boolean = false
) {
    val palette = LocalDesign.current
    val range = (max - min).coerceAtLeast(1f)
    val fraction = ((value - min) / range).coerceIn(0f, 1f)

    fun emit(x: Float, width: Int) {
        if (width <= 0) return
        val next = min + (x / width).coerceIn(0f, 1f) * range
        onValue(next.roundToInt().toFloat())
    }

    Column(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(20.dp).pointerInput(min, max) {
                detectTapGestures { offset -> emit(offset.x, size.width) }
            }.pointerInput(min, max) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> emit(offset.x, size.width) },
                    onHorizontalDrag = { change, _ -> emit(change.position.x, size.width) }
                )
            }
        ) {
            val trackHeight = 4.dp.toPx()
            val top = (size.height - trackHeight) / 2f
            drawRect(
                color = palette.off.copy(alpha = 0.6f),
                topLeft = Offset(0f, top),
                size = Size(size.width, trackHeight)
            )
            if (rainbowTrack) {
                drawRect(palette.rainbowBrush, Offset(0f, top), Size(size.width * fraction, trackHeight))
            } else {
                drawRect(
                    color = palette.accent,
                    topLeft = Offset(0f, top),
                    size = Size(size.width * fraction, trackHeight)
                )
            }
            val knobX = (size.width * fraction).coerceIn(0f, size.width)
            drawRect(
                color = palette.ink,
                topLeft = Offset(knobX - 1.dp.toPx(), 0f),
                size = Size(3.dp.toPx(), size.height)
            )
        }
    }
}

/** Filled ink button used for the primary ("Close") action of the hero card. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp
) {
    val palette = LocalDesign.current
    Box(
        modifier.height(height).clip(palette.shape).background(palette.ink).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            upper(text),
            style = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp),
            color = palette.bg
        )
    }
}

/** Small outlined warning chip shown when a window is burning out. */
@Composable
fun HotChip(modifier: Modifier = Modifier, text: String? = null) {
    val palette = LocalDesign.current
    val label = text ?: androidx.compose.ui.res.stringResource(app.netoverdose.R.string.chip_burning)
    Text(
        upper(label),
        modifier = modifier.clip(palette.shape).border(1.dp, palette.warn, palette.shape)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        style = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp, letterSpacing = 0.6.sp),
        color = palette.warn
    )
}

/** Difficulty shown as five squares filled up to the given level. */
@Composable
fun DifficultySquares(level: Int, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val palette = LocalDesign.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(5) { index ->
            Box(
                Modifier.size(size).background(
                    if (index < level) palette.accentText else palette.off.copy(alpha = 0.5f)
                )
            )
        }
    }
}

/** Day labels under the waveform: window start, centred remaining (urgency toned), deadline. */
@Composable
fun WaveLabels(windowStart: Long, deadline: Long, now: Long, modifier: Modifier = Modifier) {
    val palette = LocalDesign.current
    val zone = java.time.ZoneId.systemDefault()
    val remaining = app.netoverdose.domain.TimeText.remaining(deadline, now)
    val value = when {
        remaining.showDays ->
            "${remaining.days}д ${remaining.hours.toString().padStart(2, '0')}ч"
        remaining.showHours ->
            "${remaining.hours}ч ${remaining.minutes.toString().padStart(2, '0')}м"
        else -> "${remaining.minutes.coerceAtLeast(1)}м"
    }
    val label = if (remaining.overdue) "Просрочено $value" else "Осталось $value"
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            app.netoverdose.domain.TimeText.dayLabel(windowStart, zone),
            style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp),
            color = palette.muted,
            modifier = Modifier.weight(1f)
        )
        Text(
            label,
            style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = app.netoverdose.design.DesignEngine.urgency(deadline, windowStart, now, palette),
            maxLines = 1
        )
        Text(
            app.netoverdose.domain.TimeText.dayLabel(deadline, zone),
            style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp),
            color = palette.muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/** One row of the plan list: file tile, title, deadline meta and remaining time. */
@Composable
fun FileRow(
    title: String,
    extension: String,
    deadline: Long,
    windowStart: Long,
    remainingText: String,
    overdue: Boolean,
    now: Long,
    burning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDesign.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = palette.rowPad, horizontal = 8.dp)
                .defaultMinSize(minHeight = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FileTile(extension.ifBlank { "·" }, burnt = overdue)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = TextStyle(
                        fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp, lineHeight = 20.sp
                    ),
                    color = palette.ink,
                    minLines = 1,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    if (extension.isBlank()) stringResourceOrFallback()
                    else stringResource(app.netoverdose.R.string.file_type_meta, extension),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp),
                    color = palette.muted
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (burning) HotChip()
                Text(
                    remainingText,
                    style = TextStyle(
                        fontFamily = MonoFamily, fontSize = 12.sp,
                        fontWeight = if (overdue) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = app.netoverdose.design.DesignEngine.urgency(deadline, windowStart, now, palette),
                    maxLines = 1
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rowRule))
    }
}

@Composable
private fun stringResourceOrFallback(): String =
    androidx.compose.ui.res.stringResource(app.netoverdose.R.string.file_missing)
