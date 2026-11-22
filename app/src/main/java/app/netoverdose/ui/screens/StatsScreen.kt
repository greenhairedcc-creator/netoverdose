package app.netoverdose.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import app.netoverdose.data.toProgress
import app.netoverdose.design.LocalDesign
import app.netoverdose.domain.DayReserve
import app.netoverdose.domain.Progress
import app.netoverdose.domain.ReserveSummary
import app.netoverdose.domain.SuccessMetrics
import app.netoverdose.domain.TaskStatus
import app.netoverdose.domain.TimeText
import app.netoverdose.ui.components.AppBackground
import app.netoverdose.ui.components.ChartLegend
import app.netoverdose.ui.components.DifficultySquares
import app.netoverdose.ui.components.FileTile
import app.netoverdose.ui.components.ItalicAccent
import app.netoverdose.ui.components.MetricRow
import app.netoverdose.ui.components.RainbowHairline
import app.netoverdose.ui.components.ReserveBars
import app.netoverdose.ui.components.SectionLabel
import app.netoverdose.ui.components.StatCell
import app.netoverdose.ui.components.StatGrid
import app.netoverdose.ui.components.percentText
import app.netoverdose.ui.components.signedReserve
import app.netoverdose.ui.theme.MonoFamily
import app.netoverdose.ui.theme.SerifFamily
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

private fun ru(value: String): String = value.uppercase(Locale.forLanguageTag("ru"))

data class StatsUiState(
    val summary: ReserveSummary = ReserveSummary(0, null, null, false),
    val days: List<DayReserve> = emptyList(),
    val metrics: SuccessMetrics = SuccessMetrics(null, null),
    val recent: List<TaskItem> = emptyList(),
    val openCount: Int = 0,
    val overdueCount: Int = 0,
    val now: Long = System.currentTimeMillis()
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NetoverdoseApp
    private val zone: ZoneId = ZoneId.systemDefault()

    val state: StateFlow<StatsUiState> =
        combine(app.repository.allTasks, nowTicker()) { all, now ->
            val progress = all.map { it.task.toProgress() }
            StatsUiState(
                summary = Progress.summarize(progress, now),
                days = Progress.dailyReserves(progress, now) { millis ->
                    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().atStartOfDay(zone)
                        .toInstant().toEpochMilli()
                },
                metrics = Progress.metrics(progress, now),
                recent = all.filter { it.task.status == TaskStatus.DONE }
                    .sortedByDescending { it.task.closedAt ?: 0L }.take(5),
                openCount = all.count {
                    it.task.status == TaskStatus.PLANNED || it.task.status == TaskStatus.IN_PROGRESS
                },
                overdueCount = all.count {
                    (it.task.status == TaskStatus.PLANNED || it.task.status == TaskStatus.IN_PROGRESS) &&
                        it.task.deadline < now
                },
                now = now
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}

@Composable
fun StatsScreen(viewModel: StatsViewModel = viewModel(), onOpenTask: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalDesign.current
    val zone = ZoneId.systemDefault()
    val reserve = state.summary.reserveDays
    val dash = stringResource(R.string.stats_dash)

    AppBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = palette.pad, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(palette.gap)
        ) {
            Column {
                Text(
                    stringResource(R.string.stats_title),
                    style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 23.sp),
                    color = palette.ink,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    ru(stringResource(R.string.stats_meta)),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, letterSpacing = 0.8.sp),
                    color = palette.muted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
                RainbowHairline(Modifier.fillMaxWidth(0.18f), alpha = palette.rainAlpha)
            }

            Column {
                if (reserve == null) {
                    ItalicAccent(dash, size = 46.sp)
                    Text(
                        ru(stringResource(R.string.stats_no_data_caption)),
                        style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, letterSpacing = 0.7.sp),
                        color = palette.muted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                } else {
                    // Positive reserve is gradient text per the reference (.grad); negative keeps danger.
                    ItalicAccent(signedReserve(reserve), size = 46.sp, gradient = reserve >= 0)
                    Text(
                        ru(stringResource(R.string.stats_reserve_caption)),
                        style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, letterSpacing = 0.7.sp),
                        color = palette.muted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            StatGrid(
                listOf(
                    StatCell(state.summary.closedCount.toString(), stringResource(R.string.stats_closed)),
                    StatCell(state.openCount.toString(), stringResource(R.string.stats_open)),
                    StatCell(
                        state.overdueCount.toString(),
                        stringResource(R.string.stats_overdue),
                        danger = state.overdueCount > 0
                    ),
                    StatCell(
                        state.summary.startDelayHours?.let {
                            stringResource(
                                R.string.stats_hours,
                                String.format(Locale.forLanguageTag("ru"), "%.1f", it)
                            )
                        } ?: dash,
                        stringResource(R.string.stats_median)
                    )
                )
            )

            Column(
                Modifier.fillMaxWidth().clip(palette.shape).border(1.dp, palette.rule, palette.shape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionLabel(stringResource(R.string.stats_chart), Modifier.semantics { heading() })
                ReserveBars(days = state.days, barHeight = 74.dp, showAxis = true)
                ChartLegend()
            }

            Column(
                Modifier.fillMaxWidth().clip(palette.shape).border(1.dp, palette.rule, palette.shape)
                    .padding(12.dp)
            ) {
                SectionLabel(stringResource(R.string.stats_metrics), Modifier.semantics { heading() })
                val fast = percentText(state.metrics.startedFastShare)
                val withReserve = percentText(state.metrics.closedWithReserveShare)
                MetricRow(
                    stringResource(R.string.stats_fast_start),
                    fast?.let { stringResource(R.string.stats_percent, it) } ?: dash
                )
                MetricRow(
                    stringResource(R.string.stats_with_reserve),
                    withReserve?.let { stringResource(R.string.stats_percent, it) } ?: dash
                )
            }

            SectionLabel(stringResource(R.string.stats_recent), Modifier.semantics { heading() })

            if (state.recent.isEmpty()) {
                Text(
                    stringResource(R.string.stats_recent_empty),
                    style = TextStyle(fontFamily = SerifFamily, fontSize = 16.sp),
                    color = palette.muted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                state.recent.forEach { item ->
                    val task = item.task
                    val value = Progress.reserveDays(task.toProgress(), state.now)
                    val ext = item.attachment?.let { TimeText.extension(it.displayName) } ?: ""
                    val cd = stringResource(R.string.a11y_task_cd, task.title)
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenTask(task.id) }
                                .padding(vertical = palette.rowPad, horizontal = 4.dp)
                                .semantics { contentDescription = cd },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FileTile(ext.ifBlank { "·" }, 48.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    task.title,
                                    style = TextStyle(
                                        fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                                    ),
                                    color = palette.ink,
                                    maxLines = 2
                                )
                                Text(
                                    stringResource(
                                        R.string.meta_file,
                                        TimeText.dayLabel(task.closedAt ?: state.now, zone),
                                        ext
                                    ),
                                    style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp),
                                    color = palette.muted
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    stringResource(
                                        R.string.reserve_signed,
                                        if (value >= 0) "+" else "−",
                                        String.format(Locale.forLanguageTag("ru"), "%.1f", abs(value))
                                    ),
                                    style = TextStyle(
                                        fontFamily = MonoFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold
                                    ),
                                    color = if (value >= 0) palette.accentText else palette.danger
                                )
                                DifficultySquares(task.difficulty)
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rowRule))
                    }
                }
            }
            Box(Modifier.height(24.dp))
        }
    }
}
