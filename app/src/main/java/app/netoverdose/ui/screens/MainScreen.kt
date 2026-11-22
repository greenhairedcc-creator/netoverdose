@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package app.netoverdose.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import app.netoverdose.domain.PaceTone
import app.netoverdose.domain.Progress
import app.netoverdose.domain.ReserveSummary
import app.netoverdose.domain.TaskRules
import app.netoverdose.domain.TaskStatus
import app.netoverdose.domain.TimeText
import app.netoverdose.domain.WindowProgress
import app.netoverdose.ui.components.AccentButton
import app.netoverdose.ui.components.AppBackground
import app.netoverdose.ui.components.BrandMark
import app.netoverdose.ui.components.DifficultySquares
import app.netoverdose.ui.components.FileRow
import app.netoverdose.ui.components.FileTile
import app.netoverdose.ui.components.HotChip
import app.netoverdose.ui.components.LinkButton
import app.netoverdose.ui.components.MiniButton
import app.netoverdose.ui.components.OutlineButton
import app.netoverdose.ui.components.PrimaryButton
import app.netoverdose.ui.components.RainbowHairline
import app.netoverdose.ui.components.ReserveBars
import app.netoverdose.ui.components.SectionLabel
import app.netoverdose.ui.components.Subhead
import app.netoverdose.ui.components.WaveLabels
import app.netoverdose.ui.components.WindowWave
import app.netoverdose.ui.theme.MonoFamily
import app.netoverdose.ui.theme.SerifFamily
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private fun ru(value: String): String = value.uppercase(Locale.forLanguageTag("ru"))

data class MainUiState(
    val hero: TaskItem? = null,
    val rest: List<TaskItem> = emptyList(),
    val postponed: List<TaskItem> = emptyList(),
    val openCount: Int = 0,
    val summary: ReserveSummary = ReserveSummary(0, null, null, false),
    val days: List<DayReserve> = emptyList(),
    val now: Long = System.currentTimeMillis()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NetoverdoseApp
    private val repo = app.repository
    private val zone: ZoneId = ZoneId.systemDefault()

    val state: StateFlow<MainUiState> = combine(
        repo.openTasks, repo.postponedTasks, repo.allTasks, nowTicker()
    ) { open, postponed, all, now ->
        val current = TaskRules.current(open.map { it.task })
        val planIds = TaskRules.plan(open.map { it.task }, current?.id).map { it.id }
        val progress = all.map { it.task.toProgress() }
        MainUiState(
            hero = open.firstOrNull { it.task.id == current?.id },
            rest = planIds.mapNotNull { id -> open.firstOrNull { it.task.id == id } },
            postponed = postponed,
            openCount = open.size,
            summary = Progress.summarize(progress, now),
            days = Progress.dailyReserves(progress, now) { millis ->
                Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().atStartOfDay(zone)
                    .toInstant().toEpochMilli()
            },
            now = now
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun start(id: Long) = launchWithMessage({ repo.start(id) }, R.string.msg_started)
    fun close(id: Long) = launchWithMessage({ repo.close(id) }, R.string.msg_closed)
    fun reopen(id: Long) = launchWithMessage({ repo.reopen(id) }, R.string.msg_reopened)

    private fun launchWithMessage(block: suspend () -> Unit, messageRes: Int) {
        viewModelScope.launch {
            block()
            app.messages.emit(app.getString(messageRes))
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(), onOpenTask: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val intakeViewModel: IntakeViewModel = viewModel()
    var showIntake by rememberSaveable { mutableStateOf(false) }
    val palette = LocalDesign.current
    val zone = ZoneId.systemDefault()

    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = palette.pad, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(palette.gap)
        ) {
            item {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(Modifier.weight(1f)) {
                            BrandMark(Modifier.semantics { heading() })
                            val date = stringResource(
                                R.string.header_date,
                                TimeText.weekdayUpper(state.now, zone),
                                TimeText.dateShort(state.now, zone)
                            )
                            val count = pluralStringResource(
                                R.plurals.open_count, state.openCount, state.openCount
                            )
                            Text(
                                "$date · $count".let(::ru),
                                style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, letterSpacing = 0.8.sp),
                                color = palette.muted,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        OutlineButton(
                            text = stringResource(R.string.action_add),
                            onClick = { showIntake = true },
                            height = 44.dp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
                    RainbowHairline(Modifier.fillMaxWidth(0.18f), alpha = palette.rainAlpha)
                }
            }

            state.hero?.let { hero ->
                item(key = "hero") {
                    HeroCard(
                        item = hero,
                        now = state.now,
                        onStart = { viewModel.start(hero.task.id) },
                        onClose = { viewModel.close(hero.task.id) },
                        onOpen = { onOpenTask(hero.task.id) }
                    )
                }
            }

            item { TempoBlock(state.summary, state.days) }

            item { SectionLabel(stringResource(R.string.section_plan), Modifier.semantics { heading() }) }

            if (state.hero == null && state.rest.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.empty_tasks),
                        style = TextStyle(fontFamily = SerifFamily, fontSize = 16.sp),
                        color = palette.muted,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                }
            }

            items(state.rest, key = { it.task.id }) { item ->
                val description = stringResource(R.string.a11y_task_cd, item.task.title)
                val remaining = TimeText.remaining(item.task.deadline, state.now)
                FileRow(
                    title = item.task.title,
                    extension = item.attachment?.let { TimeText.extension(it.displayName) } ?: "",
                    deadline = item.task.deadline,
                    remainingText = remainingLabel(remaining),
                    overdue = remaining.overdue,
                    onClick = { onOpenTask(item.task.id) },
                    modifier = Modifier.semantics { contentDescription = description }
                )
            }

            if (state.postponed.isNotEmpty()) {
                item { Subhead(stringResource(R.string.section_postponed, state.postponed.size)) }
                items(state.postponed, key = { "postponed-${it.task.id}" }) { item ->
                    PostponedRow(
                        item = item,
                        onReopen = { viewModel.reopen(item.task.id) },
                        onOpen = { onOpenTask(item.task.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showIntake) {
        IntakeSheet(
            viewModel = intakeViewModel,
            onDismiss = { showIntake = false },
            onSaved = { showIntake = false }
        )
    }
}

@Composable
private fun remainingLabel(remaining: app.netoverdose.domain.Remaining): String = when {
    remaining.showDays -> stringResource(R.string.remaining_dh, remaining.days, remaining.hours)
    remaining.showHours -> stringResource(R.string.remaining_hm, remaining.hours, remaining.minutes)
    else -> stringResource(R.string.remaining_m, remaining.minutes.coerceAtLeast(1))
}

@Composable
private fun TempoBlock(summary: ReserveSummary, days: List<DayReserve>) {
    val palette = LocalDesign.current
    val pace = Progress.pace(summary)
    val value = String.format(Locale.forLanguageTag("ru"), "%.1f", pace.days)
    val aheadHead = stringResource(R.string.tempo_ahead_head)
    val aheadValue = stringResource(R.string.tempo_ahead_value, value)
    val behindHead = stringResource(R.string.tempo_behind_head)
    val behindValue = stringResource(R.string.tempo_behind_value, value)
    val onTime = stringResource(R.string.tempo_on_time)
    val noData = stringResource(R.string.tempo_no_data)
    val accent = SpanStyle(
        brush = palette.rainbowBrush,
        fontStyle = if (palette.italics) FontStyle.Italic else FontStyle.Normal,
        fontWeight = FontWeight.Bold
    )
    val phrase = buildAnnotatedString {
        when (pace.tone) {
            PaceTone.AHEAD -> {
                append(aheadHead)
                withStyle(accent) { append(aheadValue) }
            }
            PaceTone.BEHIND -> {
                append(behindHead)
                withStyle(accent) { append(behindValue) }
            }
            PaceTone.ON_TIME -> withStyle(accent) { append(onTime) }
            PaceTone.NO_DATA -> withStyle(accent) { append(noData) }
        }
    }
    val meta = if (summary.startDelayHours == null) {
        stringResource(R.string.tempo_meta_no_start, summary.closedCount)
    } else {
        stringResource(
            R.string.tempo_meta,
            summary.closedCount,
            String.format(Locale.forLanguageTag("ru"), "%.1f", summary.startDelayHours)
        )
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.tempo_label), Modifier.semantics { heading() })
        Text(
            phrase,
            style = TextStyle(
                fontFamily = SerifFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                lineHeight = 24.sp, color = palette.ink
            )
        )
        if (days.isNotEmpty()) {
            ReserveBars(days = days, barHeight = 30.dp)
        }
        Text(
            ru(meta),
            style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, letterSpacing = 0.7.sp),
            color = palette.muted
        )
    }
}

@Composable
private fun HeroCard(
    item: TaskItem,
    now: Long,
    onStart: () -> Unit,
    onClose: () -> Unit,
    onOpen: () -> Unit
) {
    val palette = LocalDesign.current
    val task = item.task
    val attachment = item.attachment
    val ext = attachment?.let { TimeText.extension(it.displayName) } ?: ""
    val burning = WindowProgress.isBurning(task.windowStart, task.deadline, now)
    val started = task.status == TaskStatus.IN_PROGRESS
    val caption = if (attachment == null) {
        stringResource(R.string.file_missing)
    } else {
        val size = if (TimeText.isMegabytes(attachment.sizeBytes)) {
            stringResource(R.string.size_mb, TimeText.sizeValue(attachment.sizeBytes))
        } else {
            stringResource(R.string.size_kb, TimeText.sizeValue(attachment.sizeBytes))
        }
        stringResource(R.string.meta_file, ext, size)
    }

    Column(
        Modifier.fillMaxWidth().clip(palette.shape).border(1.dp, palette.rule, palette.shape)
            .background(palette.bg).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RainbowHairline(Modifier.fillMaxWidth(), alpha = palette.rainAlpha)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel(stringResource(R.string.section_now), Modifier.semantics { heading() })
            if (burning) HotChip()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FileTile(ext.ifBlank { "·" }, 72.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = TextStyle(
                        fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp, lineHeight = 25.sp
                    ),
                    color = palette.ink
                )
                Text(
                    caption,
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp),
                    color = palette.muted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                )
                DifficultySquares(task.difficulty)
            }
        }
        Column {
            WindowWave(task.windowStart, task.deadline, now)
            WaveLabels(task.windowStart, task.deadline, now, Modifier.padding(top = 6.dp))
        }
        if (started) {
            PrimaryButton(stringResource(R.string.action_close), onClose, Modifier.fillMaxWidth())
        } else {
            AccentButton(stringResource(R.string.action_start), onStart, Modifier.fillMaxWidth())
        }
        LinkButton(stringResource(R.string.action_card), onOpen)
    }
}

@Composable
private fun PostponedRow(item: TaskItem, onReopen: () -> Unit, onOpen: () -> Unit) {
    val palette = LocalDesign.current
    val attachment = item.attachment
    val ext = attachment?.let { TimeText.extension(it.displayName) } ?: ""
    val description = stringResource(R.string.a11y_task_cd, item.task.title)
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen)
                .padding(vertical = palette.rowPad, horizontal = 4.dp)
                .semantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FileTile(ext.ifBlank { "·" }, 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.task.title,
                    style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    color = palette.muted,
                    maxLines = 2
                )
                Text(
                    TimeText.dayLabel(item.task.deadline, ZoneId.systemDefault()),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp),
                    color = palette.muted
                )
            }
            MiniButton(stringResource(R.string.action_reopen_short), onReopen)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rowRule))
    }
}
