@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package app.netoverdose.ui.screens

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.netoverdose.NetoverdoseApp
import app.netoverdose.R
import app.netoverdose.data.StepEntity
import app.netoverdose.data.TaskItem
import app.netoverdose.data.nowTicker
import app.netoverdose.data.toProgress
import app.netoverdose.design.LocalDesign
import app.netoverdose.domain.Progress
import app.netoverdose.domain.ProgressTask
import app.netoverdose.domain.TaskStatus
import app.netoverdose.domain.TimeText
import app.netoverdose.domain.WindowProgress
import app.netoverdose.ui.components.AccentButton
import app.netoverdose.ui.components.AppBackground
import app.netoverdose.ui.components.DifficultySquares
import app.netoverdose.ui.components.FileTile
import app.netoverdose.ui.components.HotChip
import app.netoverdose.ui.components.LinkButton
import app.netoverdose.ui.components.MiniButton
import app.netoverdose.ui.components.OutlineButton
import app.netoverdose.ui.components.PrimaryButton
import app.netoverdose.ui.components.RainbowHairline
import app.netoverdose.ui.components.SectionLabel
import app.netoverdose.ui.components.SharpTextField
import app.netoverdose.ui.components.StepCheckbox
import app.netoverdose.ui.components.WaveLabels
import app.netoverdose.ui.components.WindowWave
import app.netoverdose.ui.components.signedReserve
import app.netoverdose.ui.theme.MonoFamily
import app.netoverdose.ui.theme.SerifFamily
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

data class CardUiState(
    val item: TaskItem? = null,
    val steps: List<StepEntity> = emptyList(),
    val now: Long = System.currentTimeMillis()
)

class TaskCardViewModel(application: Application, savedState: SavedStateHandle) :
    AndroidViewModel(application) {

    private val app = application as NetoverdoseApp
    private val taskId: Long = savedState.get<Long>("id") ?: 0L

    val state: StateFlow<CardUiState> = combine(
        app.repository.observeTask(taskId),
        app.repository.steps(taskId),
        nowTicker()
    ) { item, steps, now -> CardUiState(item, steps, now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardUiState())

    fun start() = launchWithMessage({ app.repository.start(taskId) }, R.string.msg_started)
    fun close() = launchWithMessage({ app.repository.close(taskId) }, R.string.msg_closed)
    fun postpone() = launchWithMessage({ app.repository.postpone(taskId) }, R.string.msg_postponed)
    fun reopen() = launchWithMessage({ app.repository.reopen(taskId) }, R.string.msg_reopened)

    fun addStep(text: String, onDone: () -> Unit) {
        if (text.isBlank()) return
        viewModelScope.launch {
            app.repository.addStep(taskId, text)
            app.messages.emit(app.getString(R.string.msg_step_added))
            onDone()
        }
    }

    fun toggleStep(stepId: Long) {
        viewModelScope.launch { app.repository.toggleStep(stepId) }
    }

    fun deleteStep(stepId: Long) {
        viewModelScope.launch { app.repository.deleteStep(stepId) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            app.repository.delete(taskId)
            app.messages.emit(app.getString(R.string.msg_deleted))
            onDone()
        }
    }

    fun openFile(): Intent? {
        val attachment = state.value.item?.attachment
        if (attachment == null) {
            app.messages.tryEmit(app.getString(R.string.msg_no_file))
            return null
        }
        val file = app.repository.fileOf(attachment)
        if (!file.exists()) {
            app.messages.tryEmit(app.getString(R.string.msg_no_file))
            return null
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, attachment.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun launchWithMessage(block: suspend () -> Unit, messageRes: Int) {
        viewModelScope.launch {
            block()
            app.messages.emit(app.getString(messageRes))
        }
    }
}

@Composable
fun TaskCardScreen(viewModel: TaskCardViewModel = viewModel(), onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalDesign.current
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var stepDraft by rememberSaveable { mutableStateOf("") }
    val backCd = stringResource(R.string.a11y_back_cd)
    val noAppText = stringResource(R.string.msg_no_app)
    val item = state.item

    AppBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = palette.pad, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinkButton(
                    stringResource(R.string.action_back),
                    onBack,
                    Modifier.semantics { contentDescription = backCd }
                )
                if (item != null &&
                    WindowProgress.isBurning(item.task.windowStart, item.task.deadline, state.now)
                ) HotChip()
            }
            RainbowHairline(Modifier.fillMaxWidth(), alpha = palette.rainAlpha)

            if (item == null) {
                Text(
                    stringResource(R.string.empty_tasks),
                    style = TextStyle(fontFamily = SerifFamily, fontSize = 16.sp),
                    color = palette.muted
                )
                return@Column
            }

            val task = item.task
            val attachment = item.attachment
            val ext = attachment?.let { TimeText.extension(it.displayName) } ?: ""

            Text(
                task.title,
                style = TextStyle(
                    fontFamily = SerifFamily, fontWeight = FontWeight.Bold,
                    fontSize = 22.sp, lineHeight = 28.sp
                ),
                color = palette.ink,
                modifier = Modifier.semantics { heading() }
            )

            Row(
                Modifier.fillMaxWidth().clip(palette.shape).background(palette.tile)
                    .border(1.dp, palette.rule, palette.shape).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FileTile(ext.ifBlank { "·" }, 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val size = attachment?.let {
                        if (TimeText.isMegabytes(it.sizeBytes)) {
                            stringResource(R.string.size_mb, TimeText.sizeValue(it.sizeBytes))
                        } else {
                            stringResource(R.string.size_kb, TimeText.sizeValue(it.sizeBytes))
                        }
                    }
                    Text(
                        if (attachment == null) stringResource(R.string.file_missing)
                        else stringResource(R.string.meta_file, attachment.displayName, size ?: ""),
                        style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, lineHeight = 15.sp),
                        color = palette.muted
                    )
                    DifficultySquares(task.difficulty)
                }
                val openCd = stringResource(R.string.a11y_open_file_cd)
                MiniButton(
                    stringResource(R.string.action_file),
                    onClick = {
                        val intent = viewModel.openFile() ?: return@MiniButton
                        try {
                            context.startActivity(Intent.createChooser(intent, null))
                        } catch (error: ActivityNotFoundException) {
                            (context.applicationContext as NetoverdoseApp).messages
                                .tryEmit(noAppText)
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = openCd }
                )
            }

            Column {
                WindowWave(task.windowStart, task.deadline, state.now)
                WaveLabels(task.windowStart, task.deadline, state.now, Modifier.padding(top = 6.dp))
            }

            ForecastText(task.toProgress(), state.now)

            val showSteps = task.status != TaskStatus.DONE || state.steps.isNotEmpty()
            if (showSteps) {
                StepsBlock(
                    steps = state.steps,
                    editable = task.status != TaskStatus.DONE,
                    draft = stepDraft,
                    onDraft = { stepDraft = it.take(160) },
                    onAdd = { viewModel.addStep(stepDraft) { stepDraft = "" } },
                    onToggle = viewModel::toggleStep,
                    onDelete = viewModel::deleteStep
                )
            }

            when (task.status) {
                TaskStatus.IN_PROGRESS -> PrimaryButton(
                    stringResource(R.string.action_close), viewModel::close, Modifier.fillMaxWidth()
                )
                TaskStatus.DONE, TaskStatus.POSTPONED -> OutlineButton(
                    stringResource(R.string.action_reopen), viewModel::reopen, Modifier.fillMaxWidth()
                )
                else -> AccentButton(
                    stringResource(R.string.action_start), viewModel::start, Modifier.fillMaxWidth()
                )
            }

            if (task.status == TaskStatus.PLANNED || task.status == TaskStatus.IN_PROGRESS) {
                OutlineButton(
                    stringResource(R.string.action_postpone), viewModel::postpone,
                    Modifier.fillMaxWidth(), height = 44.dp
                )
            }

            OutlineButton(
                text = stringResource(R.string.action_delete),
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                height = 44.dp,
                danger = true
            )
            Box(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = palette.shape,
            containerColor = palette.bg,
            titleContentColor = palette.ink,
            textContentColor = palette.text,
            title = {
                Text(
                    stringResource(R.string.delete_title),
                    style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                )
            },
            text = { Text(stringResource(R.string.delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; viewModel.delete(onBack) },
                    shape = palette.shape
                ) { Text(stringResource(R.string.action_delete), color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, shape = palette.shape) {
                    Text(stringResource(R.string.action_cancel), color = palette.muted)
                }
            }
        )
    }
}

@Composable
private fun ForecastText(task: ProgressTask, now: Long) {
    val palette = LocalDesign.current
    val zone = ZoneId.systemDefault()
    val accent = SpanStyle(
        brush = palette.rainbowBrush,
        fontStyle = if (palette.italics) FontStyle.Italic else FontStyle.Normal,
        fontWeight = FontWeight.SemiBold
    )
    val remaining = remainingText(task.deadline, now)
    val lead: String
    val tail: String
    when (task.status) {
        TaskStatus.DONE -> {
            lead = ""
            tail = stringResource(
                R.string.forecast_done,
                TimeText.dayLabel(task.closedAt ?: now, zone),
                signedReserve(Progress.reserveDays(task, now))
            )
        }
        TaskStatus.POSTPONED -> {
            lead = ""
            tail = stringResource(R.string.forecast_postponed)
        }
        else -> {
            val left = task.deadline - now
            if (left <= 0) {
                lead = stringResource(R.string.forecast_open_overdue_lead, remaining)
                tail = stringResource(R.string.forecast_open_overdue)
            } else {
                lead = stringResource(R.string.forecast_open_lead, remaining)
                val fraction = left.toFloat() /
                    (task.deadline - task.windowStart).coerceAtLeast(1L).toFloat()
                tail = if (fraction < WindowProgress.BURN_PART) {
                    stringResource(R.string.forecast_open_tight)
                } else {
                    stringResource(R.string.forecast_open_calm)
                }
            }
        }
    }
    val text = buildAnnotatedString {
        append(lead)
        withStyle(accent) { append(tail) }
    }
    Text(
        text,
        style = TextStyle(
            fontFamily = SerifFamily, fontSize = 16.sp, lineHeight = 22.sp, color = palette.text
        ),
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp)
    )
}

@Composable
private fun remainingText(deadline: Long, now: Long): String {
    val remaining = TimeText.remaining(deadline, now)
    return when {
        remaining.showDays -> stringResource(R.string.remaining_dh, remaining.days, remaining.hours)
        remaining.showHours -> stringResource(R.string.remaining_hm, remaining.hours, remaining.minutes)
        else -> stringResource(R.string.remaining_m, remaining.minutes.coerceAtLeast(1))
    }
}

@Composable
private fun StepsBlock(
    steps: List<StepEntity>,
    editable: Boolean,
    draft: String,
    onDraft: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    val palette = LocalDesign.current
    val done = steps.count { it.done }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel(
            stringResource(R.string.steps_label, done, steps.size),
            Modifier.semantics { heading() }
        )
        steps.forEach { step ->
            val rowCd = stringResource(R.string.step_cd, step.text)
            Column {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp)
                        .clickable(enabled = editable) { onToggle(step.id) }
                        .padding(vertical = 8.dp)
                        .semantics { contentDescription = rowCd },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StepCheckbox(step.done)
                    Text(
                        step.text,
                        style = TextStyle(
                            fontSize = 13.sp, lineHeight = 18.sp,
                            textDecoration = if (step.done) TextDecoration.LineThrough else null
                        ),
                        color = if (step.done) palette.muted else palette.text,
                        modifier = Modifier.weight(1f)
                    )
                    if (editable) {
                        val deleteCd = stringResource(R.string.step_delete_cd)
                        Box(
                            Modifier.height(44.dp).padding(horizontal = 6.dp)
                                .clickable { onDelete(step.id) }
                                .semantics { contentDescription = deleteCd },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✕",
                                style = TextStyle(fontFamily = MonoFamily, fontSize = 13.sp),
                                color = palette.muted
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rowRule))
            }
        }
        if (editable) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    SharpTextField(
                        value = draft,
                        onValueChange = onDraft,
                        placeholder = stringResource(R.string.steps_hint)
                    )
                }
                MiniButton(stringResource(R.string.steps_add), onAdd)
            }
        }
    }
}
