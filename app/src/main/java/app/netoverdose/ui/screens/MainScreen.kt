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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.netoverdose.data.StoredAttachment
import app.netoverdose.data.TaskItem
import app.netoverdose.data.nowTicker
import app.netoverdose.data.toProgress
import app.netoverdose.design.LocalDesign
import app.netoverdose.domain.DayReserve
import app.netoverdose.domain.PaceTone
import app.netoverdose.domain.PlanRow
import app.netoverdose.domain.Progress
import app.netoverdose.domain.ReserveSummary
import app.netoverdose.domain.TaskFilter
import app.netoverdose.domain.TaskRules
import app.netoverdose.domain.TaskSort
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
import app.netoverdose.ui.components.SharpTextField
import app.netoverdose.ui.components.Subhead
import app.netoverdose.ui.components.WaveLabels
import app.netoverdose.ui.components.WindowWave
import app.netoverdose.ui.theme.MonoFamily
import app.netoverdose.ui.theme.SerifFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private fun ru(value: String): String = value.uppercase(Locale.forLanguageTag("ru"))

data class MainUiState(
    /** All tasks currently in progress, in deadline order (hero pager). */
    val heroes: List<TaskItem> = emptyList(),
    val rest: List<TaskItem> = emptyList(),
    val postponed: List<TaskItem> = emptyList(),
    val openCount: Int = 0,
    val summary: ReserveSummary = ReserveSummary(0, null, null, false),
    val days: List<DayReserve> = emptyList(),
    val now: Long = System.currentTimeMillis()
)

/**
 * UI-only state of the plan list: search, sort, filter and multi-selection.
 * Kept apart from [MainUiState] so changing a filter does not rebuild the flows.
 */
data class PlanQuery(
    val search: String = "",
    val sort: TaskSort = TaskSort.DEADLINE,
    val filter: TaskFilter = TaskFilter.ALL,
    val selecting: Boolean = false,
    val selected: Set<Long> = emptySet()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NetoverdoseApp
    private val repo = app.repository
    private val zone: ZoneId = ZoneId.systemDefault()

    private val query = MutableStateFlow(PlanQuery())

    val planQuery: StateFlow<PlanQuery> = query.asStateFlow()

    fun updateQuery(block: (PlanQuery) -> PlanQuery) {
        query.value = block(query.value)
    }

    fun toggleSelection(id: Long) {
        query.value = query.value.let { current ->
            val next = if (current.selected.contains(id)) current.selected - id
            else current.selected + id
            current.copy(selected = next)
        }
    }

    fun clearSelection() {
        query.value = query.value.copy(selecting = false, selected = emptySet())
    }

    val state: StateFlow<MainUiState> = combine(
        repo.openTasks, repo.postponedTasks, repo.allTasks, nowTicker()
    ) { open, postponed, all, now ->
        val heroes = TaskRules.heroes(open.map { it.task })
        val heroIds = heroes.map { it.id }.toSet()
        val progress = all.map { it.task.toProgress() }
        MainUiState(
            heroes = heroes.mapNotNull { hero -> open.firstOrNull { it.task.id == hero.id } },
            rest = open.filter { it.task.id !in heroIds },
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

    /** Plan rows after search, filter and sort are applied. */
    fun visiblePlan(state: MainUiState, plan: PlanQuery): List<TaskItem> {
        val rows = state.rest.map {
            PlanRow(
                id = it.task.id,
                title = it.task.title,
                difficulty = it.task.difficulty,
                deadline = it.task.deadline,
                windowStart = it.task.windowStart,
                status = it.task.status,
                orderIndex = it.task.orderIndex
            )
        }
        val ordered = TaskRules.planRows(rows, state.now, plan.sort, plan.filter, plan.search)
        val byId = state.rest.associateBy { it.task.id }
        return ordered.mapNotNull { byId[it.id] }
    }

    fun postponeSelected() {
        val ids = query.value.selected.toList()
        if (ids.isEmpty()) return
        clearSelection()
        launchWithMessage({ repo.postponeAll(ids) }, R.string.msg_postponed)
    }

    fun deleteSelected() {
        val ids = query.value.selected.toList()
        if (ids.isEmpty()) return
        clearSelection()
        launchWithMessage({ repo.deleteAll(ids) }, R.string.msg_deleted)
    }

    fun reorder(orderedIds: List<Long>) {
        viewModelScope.launch { repo.reorder(orderedIds) }
    }

    /** Removes a copy made for the intake panel that the user chose not to save. */
    fun discardAttachment(stored: StoredAttachment) {
        viewModelScope.launch { repo.discardAttachment(stored) }
    }

    fun start(id: Long) = launchWithMessage({ repo.start(id) }, R.string.msg_started, app.sounds::tick)
    fun close(id: Long) = launchWithMessage({ repo.close(id) }, R.string.msg_closed, app.sounds::complete)
    fun reopen(id: Long) = launchWithMessage({ repo.reopen(id) }, R.string.msg_reopened, app.sounds::tick)

    private fun launchWithMessage(
        block: suspend () -> Unit,
        messageRes: Int,
        sound: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            block()
            sound?.invoke()
            app.messages.emit(app.getString(messageRes))
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(), onOpenTask: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plan by viewModel.planQuery.collectAsStateWithLifecycle()
    val visible = remember(state, plan) { viewModel.visiblePlan(state, plan) }
    val intakeViewModel: IntakeViewModel = viewModel()
    var showIntake by rememberSaveable { mutableStateOf(false) }
    var heroIndex by rememberSaveable { mutableStateOf(0) }
    val palette = LocalDesign.current
    val zone = ZoneId.systemDefault()
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as NetoverdoseApp
    // A shared file lands here instead of being imported straight into the plan (A2).
    var sharedAttachment by remember { mutableStateOf<StoredAttachment?>(null) }

    LaunchedEffect(app) {
        app.pendingShare.collect { stored ->
            intakeViewModel.reset()
            intakeViewModel.prefill(stored)
            sharedAttachment = stored
            showIntake = true
        }
    }

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

            if (state.heroes.isNotEmpty()) {
                item(key = "hero") {
                    HeroCard(
                        item = state.heroes.getOrElse(heroIndex) { state.heroes.first() },
                        now = state.now,
                        pagerCount = state.heroes.size,
                        pagerIndex = heroIndex,
                        onSelectHero = { heroIndex = it },
                        onStart = { viewModel.start(state.heroes.getOrElse(heroIndex) { state.heroes.first() }.task.id) },
                        onClose = { viewModel.close(state.heroes.getOrElse(heroIndex) { state.heroes.first() }.task.id) },
                        onOpen = { onOpenTask(state.heroes.getOrElse(heroIndex) { state.heroes.first() }.task.id) }
                    )
                }
            }

            item { TempoBlock(state.summary, state.days) }

            item {
                PlanToolbar(
                    query = plan,
                    onSearch = { text -> viewModel.updateQuery { it.copy(search = text) } },
                    onSort = { sort -> viewModel.updateQuery { it.copy(sort = sort) } },
                    onFilter = { filter -> viewModel.updateQuery { it.copy(filter = filter) } },
                    onToggleSelecting = {
                        viewModel.updateQuery { current ->
                            if (current.selecting) current.copy(selecting = false, selected = emptySet())
                            else current.copy(selecting = true)
                        }
                    }
                )
            }

            if (state.heroes.isEmpty() && visible.isEmpty() && plan.search.isBlank() && plan.filter == TaskFilter.ALL) {
                item {
                    Text(
                        stringResource(R.string.empty_tasks),
                        style = TextStyle(fontFamily = SerifFamily, fontSize = 16.sp),
                        color = palette.muted,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                }
            } else if (visible.isEmpty() && (plan.search.isNotBlank() || plan.filter != TaskFilter.ALL)) {
                item {
                    Text(
                        stringResource(R.string.plan_nothing_found),
                        style = TextStyle(fontFamily = SerifFamily, fontSize = 16.sp),
                        color = palette.muted,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                }
            }

            items(visible, key = { it.task.id }) { item ->
                val description = stringResource(R.string.a11y_task_cd, item.task.title)
                val remaining = TimeText.remaining(item.task.deadline, state.now)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (plan.selecting) {
                        Box(
                            Modifier.padding(start = 8.dp).size(20.dp)
                                .background(
                                    if (plan.selected.contains(item.task.id)) palette.accent else palette.bg
                                )
                                .border(1.dp, palette.rule)
                                .clickable { viewModel.toggleSelection(item.task.id) }
                        )
                    }
                    FileRow(
                        title = item.task.title,
                        extension = item.attachment?.let { TimeText.extension(it.displayName) } ?: "",
                        deadline = item.task.deadline,
                        windowStart = item.task.windowStart,
                        remainingText = remainingLabel(remaining),
                        overdue = remaining.overdue,
                        now = state.now,
                        burning = WindowProgress.isBurning(item.task.windowStart, item.task.deadline, state.now),
                        onClick = {
                            if (plan.selecting) viewModel.toggleSelection(item.task.id)
                            else onOpenTask(item.task.id)
                        },
                        modifier = Modifier.weight(1f).semantics { contentDescription = description }
                    )
                }
            }

            if (plan.selecting && plan.selected.isNotEmpty()) {
                item {
                    BulkBar(
                        count = plan.selected.size,
                        onPostpone = viewModel::postponeSelected,
                        onDelete = viewModel::deleteSelected,
                        onClear = viewModel::clearSelection
                    )
                }
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
            onDismiss = {
                showIntake = false
                // The copy never became a task, so it must not stay in filesDir forever.
                val stored = sharedAttachment
                if (stored != null) {
                    sharedAttachment = null
                    viewModel.discardAttachment(stored)
                }
            },
            onSaved = {
                showIntake = false
                sharedAttachment = null
            }
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
    pagerCount: Int,
    pagerIndex: Int,
    onSelectHero: (Int) -> Unit,
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
            FileTile(ext.ifBlank { "·" }, 72.dp, burnt = task.deadline < now)
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

        // Pager dots when more than one task is in progress.
        if (pagerCount > 1) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pagerCount) { index ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).height(6.dp)
                            .width(if (index == pagerIndex) 16.dp else 6.dp)
                            .background(if (index == pagerIndex) palette.accent else palette.off)
                            .clickable { onSelectHero(index) }
                    )
                }
            }
        }
    }
}

/** Search field, sort selector and filter chips above the plan list. */
@Composable
private fun PlanToolbar(
    query: PlanQuery,
    onSearch: (String) -> Unit,
    onSort: (TaskSort) -> Unit,
    onFilter: (TaskFilter) -> Unit,
    onToggleSelecting: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.section_plan), Modifier.semantics { heading() })
            Box(Modifier.weight(1f))
            MiniButton(
                if (query.selecting) stringResource(R.string.plan_done) else stringResource(R.string.plan_select),
                onToggleSelecting
            )
        }
        SharpTextField(
            value = query.search,
            onValueChange = onSearch,
            placeholder = stringResource(R.string.plan_search)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TaskFilter.entries.forEach { filter ->
                FilterChip(
                    label = when (filter) {
                        TaskFilter.ALL -> stringResource(R.string.plan_filter_all)
                        TaskFilter.BURNING -> stringResource(R.string.plan_filter_burning)
                        TaskFilter.WEEK -> stringResource(R.string.plan_filter_week)
                    },
                    active = query.filter == filter,
                    onClick = { onFilter(filter) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TaskSort.entries.forEach { sort ->
                FilterChip(
                    label = when (sort) {
                        TaskSort.DEADLINE -> stringResource(R.string.plan_sort_deadline)
                        TaskSort.DIFFICULTY -> stringResource(R.string.plan_sort_difficulty)
                        TaskSort.TITLE -> stringResource(R.string.plan_sort_title)
                        TaskSort.MANUAL -> stringResource(R.string.plan_sort_manual)
                    },
                    active = query.sort == sort,
                    onClick = { onSort(sort) }
                )
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    val palette = LocalDesign.current
    Box(
        Modifier.height(32.dp).clip(palette.shape)
            .background(if (active) palette.ink else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (active) palette.ink else palette.rule, palette.shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            ru(label),
            style = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp, letterSpacing = 0.5.sp),
            color = if (active) palette.bg else palette.muted,
            maxLines = 1
        )
    }
}

/** Selection bar shown while several plan rows are ticked. */
@Composable
private fun BulkBar(count: Int, onPostpone: () -> Unit, onDelete: () -> Unit, onClear: () -> Unit) {
    val palette = LocalDesign.current
    Row(
        Modifier.fillMaxWidth().clip(palette.shape).background(palette.ink)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.plan_selected, count),
            style = TextStyle(
                fontFamily = MonoFamily, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp
            ),
            color = palette.bg,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(
            ru(stringResource(R.string.action_postpone)),
            style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = palette.bg,
            modifier = Modifier.clickable(onClick = onPostpone)
        )
        Text(
            ru(stringResource(R.string.action_delete)),
            style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = palette.danger,
            modifier = Modifier.clickable(onClick = onDelete)
        )
        Text(
            "✕",
            style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp),
            color = palette.bg,
            modifier = Modifier.clickable(onClick = onClear)
        )
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
