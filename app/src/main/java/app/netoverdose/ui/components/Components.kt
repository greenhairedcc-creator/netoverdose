package app.netoverdose.ui.components

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.netoverdose.design.LocalDesign
import app.netoverdose.domain.WindowProgress
import app.netoverdose.ui.theme.MonoFamily
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

/** 32-column waveform: filled part tracks elapsed window share. */
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
    Canvas(
        modifier.fillMaxWidth().height(height)
            .semantics { contentDescription = description }
    ) {
        val count = WindowProgress.COLUMNS
        val slot = size.width / count
        val barWidth = (slot * 0.56f).coerceAtLeast(1f)
        WindowProgress.HEIGHTS.forEachIndexed { index, percent ->
            val barHeight = size.height * (percent / 100f)
            val top = size.height - barHeight
            val left = index * slot + (slot - barWidth) / 2f
            val active = index < filled
            drawRect(
                color = if (active) palette.waveColors[index % palette.waveColors.size]
                else palette.off.copy(alpha = 0.55f),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

@Composable
fun RainbowHairline(modifier: Modifier = Modifier, alpha: Float = 1f) {
    val palette = LocalDesign.current
    Box(modifier.height(2.dp).background(palette.rainbowBrush, alpha = alpha.coerceIn(0f, 1f)))
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
fun ItalicAccent(text: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.TextUnit = 20.sp) {
    val palette = LocalDesign.current
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = SerifFamily,
            fontStyle = if (palette.italics) FontStyle.Italic else FontStyle.Normal,
            fontWeight = FontWeight.Bold,
            fontSize = size,
            lineHeight = size * 1.25f
        ),
        color = palette.ink
    )
}

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    val palette = LocalDesign.current
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            "net",
            style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp),
            color = palette.ink
        )
        Text(
            "overdose",
            style = TextStyle(
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontStyle = if (palette.italics) FontStyle.Italic else FontStyle.Normal,
                fontSize = 22.sp
            ),
            color = palette.accentText
        )
    }
}

@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp
) {
    val palette = LocalDesign.current
    Box(
        modifier.height(height).clip(palette.shape).background(palette.accent).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            upper(text),
            style = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp),
            color = palette.onAccent
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

@Composable
fun FileTile(label: String, size: Dp = 44.dp) {
    val palette = LocalDesign.current
    Box(
        Modifier.size(size).clip(palette.shape).background(palette.tile).border(1.dp, palette.rule, palette.shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
            color = palette.muted,
            maxLines = 1
        )
    }
}

/** Single-line sharp input field used by the ideas composer. */
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
        fontFamily = if (serif) SerifFamily else MonoFamily,
        fontWeight = if (serif) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = if (serif) 16.sp else 13.sp,
        lineHeight = if (singleLine) TextUnit.Unspecified else 18.sp,
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

/** Day labels under the waveform: window start on the left, deadline on the right. */
@Composable
fun WaveLabels(windowStart: Long, deadline: Long, now: Long, modifier: Modifier = Modifier) {
    val palette = LocalDesign.current
    val zone = java.time.ZoneId.systemDefault()
    val remaining = app.netoverdose.domain.TimeText.remaining(deadline, now)
    val value = when {
        remaining.showDays -> "${remaining.days}d ${remaining.hours}h"
        remaining.showHours -> "${remaining.hours}h ${remaining.minutes}m"
        else -> "${remaining.minutes.coerceAtLeast(1)}m"
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            app.netoverdose.domain.TimeText.dayLabel(windowStart, zone),
            style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp),
            color = palette.muted
        )
        Text(
            "$value · ${app.netoverdose.domain.TimeText.dayLabel(deadline, zone)}",
            style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = when {
                remaining.overdue -> palette.danger
                app.netoverdose.domain.WindowProgress.isBurning(windowStart, deadline, now) -> palette.warn
                else -> palette.muted
            }
        )
    }
}

/** One row of the plan list: file tile, title, deadline meta and remaining time. */
@Composable
fun FileRow(
    title: String,
    extension: String,
    deadline: Long,
    remainingText: String,
    overdue: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDesign.current
    val zone = java.time.ZoneId.systemDefault()
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = palette.rowPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FileTile(extension.ifBlank { "·" })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                    color = palette.ink,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    "${app.netoverdose.domain.TimeText.dayLabel(deadline, zone)} · " +
                        app.netoverdose.domain.TimeText.weekdayUpper(deadline, zone),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp),
                    color = palette.muted
                )
            }
            Text(
                remainingText,
                style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = if (overdue) palette.danger else palette.muted
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rowRule))
    }
}
