package app.netoverdose.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.netoverdose.R
import app.netoverdose.design.LocalDesign
import app.netoverdose.domain.DayReserve
import app.netoverdose.domain.TimeText
import app.netoverdose.ui.theme.MonoFamily
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun ru(value: String): String = value.uppercase(Locale.forLanguageTag("ru"))

/** Visual checkbox for checklist rows; the row itself owns the touch target. */
@Composable
fun StepCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalDesign.current
    Box(
        modifier.size(20.dp)
            .background(if (checked) palette.accent else palette.bg)
            .border(1.5.dp, if (checked) palette.accent else palette.rule),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Canvas(Modifier.size(12.dp)) {
                val stroke = 2.dp.toPx()
                drawLine(
                    palette.onAccent,
                    Offset(size.width * 0.15f, size.height * 0.55f),
                    Offset(size.width * 0.42f, size.height * 0.82f),
                    strokeWidth = stroke
                )
                drawLine(
                    palette.onAccent,
                    Offset(size.width * 0.42f, size.height * 0.82f),
                    Offset(size.width * 0.88f, size.height * 0.2f),
                    strokeWidth = stroke
                )
            }
        }
    }
}

/** 14 buckets of average reserve. Positive uses the spectrum, negative uses danger. */
@Composable
fun ReserveBars(
    days: List<DayReserve>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 30.dp,
    showAxis: Boolean = false
) {
    val palette = LocalDesign.current
    val zone = ZoneId.systemDefault()
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(barHeight),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEachIndexed { index, day ->
                val average = day.averageReserveDays
                val label = TimeText.dayLabel(day.dayStart, zone)
                val description = if (average == null) {
                    stringResource(R.string.stats_day_empty_cd, label)
                } else {
                    stringResource(
                        R.string.stats_day_cd,
                        label,
                        day.closedCount,
                        String.format(Locale.forLanguageTag("ru"), "%.1f", average)
                    )
                }
                val fraction = if (average == null) 0.08f
                else (abs(average) / 3.0).coerceIn(0.12, 1.0).toFloat()
                val overdue = average != null && average < 0
                val color = when {
                    average == null -> palette.off
                    overdue -> palette.danger
                    else -> palette.waveColors[(index * 31 / 13).coerceIn(0, palette.waveColors.size - 1)]
                }
                Box(
                    Modifier.weight(1f).fillMaxHeight(fraction).background(color)
                        .semantics { contentDescription = description }
                ) {
                    // Overdue days keep the reference's diagonal hatch instead of a flat block.
                    if (overdue && palette.hatch) {
                        Canvas(Modifier.matchParentSize()) {
                            val step = 4.dp.toPx()
                            var offset = -size.height
                            while (offset < size.width) {
                                drawLine(
                                    color = palette.bg.copy(alpha = 0.85f),
                                    start = Offset(offset, size.height),
                                    end = Offset(offset + size.height, 0f),
                                    strokeWidth = 2.dp.toPx()
                                )
                                offset += step
                            }
                        }
                    }
                }
            }
        }
        if (showAxis) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                days.forEachIndexed { index, day ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (index % 2 == 0) {
                            Text(
                                Instant.ofEpochMilli(day.dayStart).atZone(zone).dayOfMonth.toString(),
                                style = TextStyle(fontFamily = MonoFamily, fontSize = 8.sp),
                                color = palette.muted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChartLegend(modifier: Modifier = Modifier) {
    val palette = LocalDesign.current
    Row(
        modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LegendItem(palette.waveColors.getOrElse(8) { palette.accentText }, stringResource(R.string.stats_legend_pos))
        LegendItem(palette.danger, stringResource(R.string.stats_legend_neg))
        LegendItem(palette.off, stringResource(R.string.stats_legend_none))
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    val palette = LocalDesign.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).background(color))
        Text(
            ru(text),
            style = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp, letterSpacing = 0.45.sp),
            color = palette.muted
        )
    }
}

data class StatCell(val value: String, val label: String, val danger: Boolean = false)

@Composable
fun StatGrid(cells: List<StatCell>, modifier: Modifier = Modifier) {
    val palette = LocalDesign.current
    Column(modifier.fillMaxWidth().border(1.dp, palette.rule, palette.shape)) {
        cells.chunked(2).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rowRule))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { columnIndex, cell ->
                    if (columnIndex > 0) {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(palette.rowRule))
                    }
                    Column(
                        Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            cell.value,
                            style = TextStyle(fontFamily = MonoFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                            color = if (cell.danger) palette.danger else palette.ink
                        )
                        Text(
                            ru(cell.label),
                            style = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp, letterSpacing = 0.72.sp),
                            color = palette.muted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalDesign.current
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp),
            color = palette.text,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = palette.accentText
        )
    }
}

@Composable
fun Subhead(text: String, modifier: Modifier = Modifier) {
    val palette = LocalDesign.current
    Row(
        modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionLabel(text)
        Box(Modifier.weight(1f).height(1.dp).background(palette.rowRule))
    }
}

fun signedReserve(value: Double): String {
    val sign = if (value >= 0) "+" else "−"
    return sign + String.format(Locale.forLanguageTag("ru"), "%.1f", abs(value))
}

fun percentText(share: Double?): Int? = share?.let { (it * 100).roundToInt() }
