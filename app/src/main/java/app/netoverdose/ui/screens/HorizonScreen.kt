package app.netoverdose.ui.screens

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.netoverdose.NetoverdoseApp
import app.netoverdose.R
import app.netoverdose.data.TaskItem
import app.netoverdose.data.nowTicker
import app.netoverdose.design.DesignPalette
import app.netoverdose.design.LocalDesign
import app.netoverdose.domain.TaskStatus
import app.netoverdose.domain.TimeText
import app.netoverdose.domain.WindowProgress
import app.netoverdose.ui.components.AppBackground
import app.netoverdose.ui.components.FileRow
import app.netoverdose.ui.components.MiniButton
import app.netoverdose.ui.components.RainbowHairline
import app.netoverdose.ui.components.SectionLabel
import app.netoverdose.ui.components.SegmentedControl
import app.netoverdose.ui.theme.MonoFamily
import app.netoverdose.ui.theme.SerifFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun ru(value: String): String = value.uppercase(Locale.forLanguageTag("ru"))

data class HorizonDay(
    val start: Long,
    val label: String,
    val today: Boolean,
    val tasks: List<TaskItem>
)

/** One cell of the month calendar: weight of closed work, burned deadlines, due tasks, notes. */
data class CalendarCell(
    val dayStart: Long,
    val dayOfMonth: Int,
    val inMonth: Boolean,
    val today: Boolean,
    val weight: Int,
    val burned: Int,
    val dueCount: Int,
    val noteCount: Int
)

data class HorizonUiState(
    val days: List<HorizonDay> = emptyList(),
    val soon: List<TaskItem> = emptyList(),
    val openCount: Int = 0,
    val now: Long = System.currentTimeMillis(),
    val cells: List<CalendarCell> = emptyList(),
    val monthTitle: String = "",
    val maxWeight: Int = 1
)

class HorizonViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NetoverdoseApp
    private val zone: ZoneId = ZoneId.systemDefault()
    private val dayMillis = 86_400_000L

    /** Month shown in the calendar mode; moved by the arrows. */
    private val month = MutableStateFlow(Instant.ofEpochMilli(System.currentTimeMillis())
        .atZone(zone).toLocalDate().withDayOfMonth(1))

    fun shiftMonth(delta: Long) {
        month.value = month.value.plusMonths(delta)
    }

    fun currentMonth() {
        month.value = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(zone).toLocalDate().withDayOfMonth(1)
    }

    val state: StateFlow<HorizonUiState> =
        combine(
            app.repository.openTasks,
            app.repository.allTasks,
            app.repository.notes,
            month,
            nowTicker()
        ) { open, all, notes, shownMonth, now ->
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val days = (0..6).map { offset ->
                val date = today.plusDays(offset.toLong())
                val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val tasks = open.filter { item ->
                    val deadline = item.task.deadline
                    if (offset == 0) deadline < end else deadline in start until end
                }.sortedBy { it.task.deadline }
                HorizonDay(
                    start = start,
                    label = if (offset == 0) app.getString(R.string.horizon_today)
                    else TimeText.dayLabel(start, zone),
                    today = offset == 0,
                    tasks = tasks
                )
            }

            // Calendar grid: 7 columns, weeks aligned to Monday, includes leading/trailing days.
            val first = shownMonth
            val lead = (first.dayOfWeek.value + 6) % 7
            val gridStart = first.minusDays(lead.toLong())
            val cells = ArrayList<CalendarCell>(42)
            var maxWeight = 1
            repeat(42) { index ->
                val date = gridStart.plusDays(index.toLong())
                val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val closedToday = all.filter {
                    it.task.status == TaskStatus.DONE &&
                        (it.task.closedAt ?: 0L) in start until end
                }
                val weight = closedToday.sumOf { it.task.difficulty }
                val burned = all.count { item ->
                    val closed = item.task.closedAt
                    item.task.deadline in start until end &&
                        item.task.deadline < now &&
                        (closed == null || closed > item.task.deadline)
                }
                val notesToday = notes.count { it.at in start until end }
                if (date.month == first.month && weight > maxWeight) maxWeight = weight
                cells += CalendarCell(
                    dayStart = start,
                    dayOfMonth = date.dayOfMonth,
                    inMonth = date.month == first.month,
                    today = date == today,
                    weight = weight,
                    burned = burned,
                    dueCount = open.count { it.task.deadline in start until end },
                    noteCount = notesToday
                )
            }

            HorizonUiState(
                days = days,
                soon = open.filter { it.task.deadline <= now + 2 * dayMillis }
                    .sortedBy { it.task.deadline },
                openCount = open.size,
                now = now,
                cells = cells,
                monthTitle = first.format(
                    DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("ru"))
                ),
                maxWeight = maxWeight
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HorizonUiState())
}

@Composable
fun HorizonScreen(viewModel: HorizonViewModel = viewModel(), onOpenTask: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalDesign.current
    // 0 = week columns, 1 = month calendar
    var mode by rememberSaveable { mutableStateOf(0) }
    var openDay by rememberSaveable { mutableStateOf<Long?>(null) }

    AppBackground {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = palette.pad, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(palette.gap)
        ) {
            Column {
                Text(
                    stringResource(R.string.horizon_title),
                    style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 23.sp),
                    color = palette.ink,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    ru(stringResource(R.string.horizon_meta, state.openCount)),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, letterSpacing = 0.8.sp),
                    color = palette.muted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
                RainbowHairline(Modifier.fillMaxWidth(0.18f), alpha = palette.rainAlpha)
            }

            SegmentedControl(
                options = listOf(
                    stringResource(R.string.horizon_week),
                    stringResource(R.string.horizon_month)
                ),
                selected = mode,
                onSelect = { mode = it }
            )

            if (mode == 0) {
                WeekGrid(state = state, onOpenTask = onOpenTask)
            } else {
                MonthGrid(
                    state = state,
                    onPrev = { viewModel.shiftMonth(-1) },
                    onNext = { viewModel.shiftMonth(1) },
                    onToday = { viewModel.currentMonth() },
                    onOpenDay = { dayStart -> openDay = dayStart }
                )
            }

            SectionLabel(stringResource(R.string.horizon_soon), Modifier.semantics { heading() })

            if (state.soon.isEmpty()) {
                Text(
                    stringResource(R.string.horizon_empty),
                    style = TextStyle(
                        fontFamily = SerifFamily, fontSize = 16.sp,
                        fontStyle = if (palette.italics) FontStyle.Italic else FontStyle.Normal
                    ),
                    color = palette.muted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                state.soon.forEach { item ->
                    val cd = stringResource(R.string.a11y_task_cd, item.task.title)
                    val remaining = TimeText.remaining(item.task.deadline, state.now)
                    FileRow(
                        title = item.task.title,
                        extension = item.attachment?.let { TimeText.extension(it.displayName) } ?: "",
                        deadline = item.task.deadline,
                        windowStart = item.task.windowStart,
                        remainingText = when {
                            remaining.showDays ->
                                stringResource(R.string.remaining_dh, remaining.days, remaining.hours)
                            remaining.showHours ->
                                stringResource(R.string.remaining_hm, remaining.hours, remaining.minutes)
                            else ->
                                stringResource(R.string.remaining_m, remaining.minutes.coerceAtLeast(1))
                        },
                        overdue = remaining.overdue,
                        now = state.now,
                        burning = WindowProgress.isBurning(item.task.windowStart, item.task.deadline, state.now),
                        onClick = { onOpenTask(item.task.id) },
                        modifier = Modifier.semantics { contentDescription = cd }
                    )
                }
            }
            Box(Modifier.height(24.dp))
        }
    }

    openDay?.let { dayStart ->
        DaySheet(
            dayStart = dayStart,
            now = state.now,
            onDismiss = { openDay = null },
            onOpenTask = onOpenTask
        )
    }
}

/** Seven columns of chips, one per day, with today highlighted. */
@Composable
private fun WeekGrid(state: HorizonUiState, onOpenTask: (Long) -> Unit) {
    val palette = LocalDesign.current
    Row(
        Modifier.fillMaxWidth().border(1.dp, palette.rule, palette.shape)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        state.days.forEach { day ->
            val dayCd = stringResource(R.string.horizon_day_cd, day.label, day.tasks.size)
            Column(
                Modifier.weight(1f).semantics { contentDescription = dayCd },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    ru(day.label),
                    style = TextStyle(
                        fontFamily = MonoFamily, fontSize = 9.sp,
                        fontWeight = if (day.today) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (day.today) palette.ink else palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    Modifier.fillMaxWidth().height(2.dp).padding(top = 1.dp)
                        .background(if (day.today) palette.accent else palette.rowRule)
                )
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    day.tasks.forEach { item ->
                        HorizonChip(item, onClick = { onOpenTask(item.task.id) })
                    }
                }
                Text(
                    if (day.tasks.isEmpty()) "·" else day.tasks.size.toString(),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp),
                    color = palette.muted,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/** Month calendar: heat of closed work, hatched burnt deadlines, due and note dots. */
@Composable
private fun MonthGrid(
    state: HorizonUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onOpenDay: (Long) -> Unit
) {
    val palette = LocalDesign.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "‹",
                style = TextStyle(fontFamily = MonoFamily, fontSize = 18.sp),
                color = palette.ink,
                modifier = Modifier.clip(palette.shape)
                    .clickable(onClick = onPrev)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
            Text(
                ru(state.monthTitle),
                style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                color = palette.ink,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            MiniButton(stringResource(R.string.horizon_today), onToday)
            Text(
                "›",
                style = TextStyle(fontFamily = MonoFamily, fontSize = 18.sp),
                color = palette.ink,
                modifier = Modifier.clip(palette.shape)
                    .clickable(onClick = onNext)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс").forEach { label ->
                Text(
                    ru(label),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 8.sp, letterSpacing = 0.4.sp),
                    color = palette.muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        state.cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { cell -> CalendarCellBox(cell, state.maxWeight, palette, onOpenDay) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(palette.waveColors[20], stringResource(R.string.horizon_legend_heat))
            LegendDot(palette.danger, stringResource(R.string.horizon_legend_burn))
            LegendDot(palette.accentText, stringResource(R.string.horizon_legend_due))
            LegendDot(palette.ink, stringResource(R.string.horizon_legend_note))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CalendarCellBox(
    cell: CalendarCell,
    maxWeight: Int,
    palette: DesignPalette,
    onOpenDay: (Long) -> Unit
) {
    val heat = if (cell.weight == 0) 0f
    else (0.18f + cell.weight.toFloat() / maxWeight * 0.52f).coerceIn(0f, 0.7f)
    val heatColor = palette.waveColors[
        ((cell.weight.toFloat() / maxWeight) * (palette.waveColors.size - 1)).toInt()
            .coerceIn(0, palette.waveColors.size - 1)
    ]
    val cd = stringResource(
        R.string.horizon_cell_cd, cell.dayOfMonth, cell.weight, cell.dueCount, cell.noteCount
    )
    Box(
        Modifier.weight(1f).aspectRatio(1f)
            .clip(palette.shape)
            .background(if (cell.inMonth) palette.tile else palette.bg)
            .border(
                width = if (cell.today) 2.dp else 1.dp,
                color = if (cell.today) palette.accent else palette.rule,
                shape = palette.shape
            )
            .then(if (cell.inMonth) Modifier.clickable { onOpenDay(cell.dayStart) } else Modifier)
            .semantics { contentDescription = cd }
    ) {
        if (heat > 0f) {
            Box(Modifier.matchParentSize().background(heatColor.copy(alpha = heat)))
        }
        if (cell.burned > 0 && palette.hatch) {
            Canvas(Modifier.matchParentSize()) {
                val step = 5.dp.toPx()
                var offset = -size.height
                while (offset < size.width) {
                    drawLine(
                        color = palette.danger.copy(alpha = 0.55f),
                        start = Offset(offset, size.height),
                        end = Offset(offset + size.height, 0f),
                        strokeWidth = 2.dp.toPx()
                    )
                    offset += step
                }
            }
        }
        Text(
            cell.dayOfMonth.toString(),
            style = TextStyle(
                fontFamily = MonoFamily, fontSize = 10.sp,
                fontWeight = if (cell.today) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (cell.inMonth) palette.ink.copy(alpha = 0.9f) else palette.muted.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp, top = 3.dp)
        )
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 3.dp, bottom = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (cell.dueCount > 0) Box(Modifier.size(4.dp).background(palette.accentText))
            if (cell.noteCount > 0) Box(Modifier.size(4.dp).background(palette.ink))
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    val palette = LocalDesign.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).background(color))
        Text(
            ru(label),
            style = TextStyle(fontFamily = MonoFamily, fontSize = 8.sp, letterSpacing = 0.45.sp),
            color = palette.muted
        )
    }
}

@Composable
private fun HorizonChip(item: TaskItem, onClick: () -> Unit) {
    val palette = LocalDesign.current
    val ext = item.attachment?.let { TimeText.extension(it.displayName) } ?: ""
    val color = palette.waveColors[((item.task.difficulty - 1) * 8).coerceIn(0, palette.waveColors.size - 1)]
    val cd = stringResource(R.string.horizon_chip_cd, ext, item.task.title)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .border(1.dp, palette.rule, palette.shape).background(palette.tile)
            .clickable(onClick = onClick)
            .semantics { contentDescription = cd }
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(color))
        Column(Modifier.padding(horizontal = 4.dp, vertical = 5.dp)) {
            Text(
                ext.ifBlank { "·" },
                style = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = palette.ink
            )
            Text(
                item.task.title,
                style = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp, lineHeight = 12.sp),
                color = palette.text,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
