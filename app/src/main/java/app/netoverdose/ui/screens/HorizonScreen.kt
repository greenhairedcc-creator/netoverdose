package app.netoverdose.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
import app.netoverdose.design.LocalDesign
import app.netoverdose.domain.TimeText
import app.netoverdose.ui.components.AppBackground
import app.netoverdose.ui.components.FileRow
import app.netoverdose.ui.components.RainbowHairline
import app.netoverdose.ui.components.SectionLabel
import app.netoverdose.ui.theme.MonoFamily
import app.netoverdose.ui.theme.SerifFamily
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private fun ru(value: String): String = value.uppercase(Locale.forLanguageTag("ru"))

data class HorizonDay(
    val start: Long,
    val label: String,
    val today: Boolean,
    val tasks: List<TaskItem>
)

data class HorizonUiState(
    val days: List<HorizonDay> = emptyList(),
    val soon: List<TaskItem> = emptyList(),
    val openCount: Int = 0,
    val now: Long = System.currentTimeMillis()
)

class HorizonViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NetoverdoseApp
    private val zone: ZoneId = ZoneId.systemDefault()
    private val dayMillis = 86_400_000L

    val state: StateFlow<HorizonUiState> =
        combine(app.repository.openTasks, nowTicker()) { open, now ->
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
            HorizonUiState(
                days = days,
                soon = open.filter { it.task.deadline <= now + 2 * dayMillis }
                    .sortedBy { it.task.deadline },
                openCount = open.size,
                now = now
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HorizonUiState())
}

@Composable
fun HorizonScreen(viewModel: HorizonViewModel = viewModel(), onOpenTask: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalDesign.current

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
                        remainingText = when {
                            remaining.showDays ->
                                stringResource(R.string.remaining_dh, remaining.days, remaining.hours)
                            remaining.showHours ->
                                stringResource(R.string.remaining_hm, remaining.hours, remaining.minutes)
                            else ->
                                stringResource(R.string.remaining_m, remaining.minutes.coerceAtLeast(1))
                        },
                        overdue = remaining.overdue,
                        onClick = { onOpenTask(item.task.id) },
                        modifier = Modifier.semantics { contentDescription = cd }
                    )
                }
            }
            Box(Modifier.height(24.dp))
        }
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
