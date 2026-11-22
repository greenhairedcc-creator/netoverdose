package app.netoverdose.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
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
import app.netoverdose.data.NoteEntity
import app.netoverdose.data.TaskItem
import app.netoverdose.data.toProgress
import app.netoverdose.design.LocalDesign
import app.netoverdose.domain.TaskStatus
import app.netoverdose.domain.TimeText
import app.netoverdose.ui.components.AccentButton
import app.netoverdose.ui.components.FileRow
import app.netoverdose.ui.components.FileTile
import app.netoverdose.ui.components.ItalicAccent
import app.netoverdose.ui.components.RainbowHairline
import app.netoverdose.ui.components.SectionLabel
import app.netoverdose.ui.components.SharpTextField
import app.netoverdose.ui.components.signedReserve
import app.netoverdose.ui.theme.MonoFamily
import app.netoverdose.ui.theme.SerifFamily
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DayUiState(
    val due: List<TaskItem> = emptyList(),
    val burned: List<TaskItem> = emptyList(),
    val closed: List<TaskItem> = emptyList(),
    val notes: List<NoteEntity> = emptyList()
)

class DayViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NetoverdoseApp
    private val zone: ZoneId = ZoneId.systemDefault()

    fun state(dayStart: Long, now: Long): StateFlow<DayUiState> {
        val dayEnd = dayStart + 86_400_000L
        return combine(app.repository.allTasks, app.repository.notes) { all, notes ->
            DayUiState(
                due = all.filter {
                    (it.task.status == TaskStatus.PLANNED || it.task.status == TaskStatus.IN_PROGRESS) &&
                        it.task.deadline in dayStart until dayEnd
                }.sortedBy { it.task.deadline },
                burned = all.filter { item ->
                    val closed = item.task.closedAt
                    item.task.deadline in dayStart until dayEnd &&
                        item.task.deadline < now &&
                        (closed == null || closed > item.task.deadline)
                },
                closed = all.filter {
                    it.task.status == TaskStatus.DONE &&
                        (it.task.closedAt ?: 0L) in dayStart until dayEnd
                }.sortedByDescending { it.task.closedAt ?: 0L },
                notes = notes.filter { it.at in dayStart until dayEnd }.sortedBy { it.at }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState())
    }

    fun addNote(dayStart: Long, now: Long, text: String, onDone: () -> Unit) {
        if (text.isBlank()) return
        viewModelScope.launch {
            // Notes added to today keep the real time, otherwise they pin to midday.
            val todayStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                .atStartOfDay(zone).toInstant().toEpochMilli()
            val at = if (dayStart == todayStart) now else dayStart + 43_200_000L
            app.repository.addNote(at, text)
            app.messages.emit(app.getString(R.string.msg_note_added))
            onDone()
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            app.repository.deleteNote(id)
            app.messages.emit(app.getString(R.string.msg_note_deleted))
        }
    }
}

/** Bottom sheet for one calendar day: due, burnt, closed tasks and free-form notes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(
    dayStart: Long,
    now: Long,
    onDismiss: () -> Unit,
    onOpenTask: (Long) -> Unit,
    viewModel: DayViewModel = viewModel()
) {
    val palette = LocalDesign.current
    val zone = ZoneId.systemDefault()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dayState by viewModel.state(dayStart, now).collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }

    val date = Instant.ofEpochMilli(dayStart).atZone(zone)
    val title = date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru")))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = palette.shape,
        containerColor = palette.bg,
        contentColor = palette.text,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
        dragHandle = {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(3.dp)
                        .background(palette.rule)
                )
                RainbowHairline(Modifier.fillMaxWidth().padding(top = 8.dp), alpha = palette.rainAlpha)
            }
        }
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 640.dp).imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = palette.pad).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ItalicAccent(title, size = 22.sp, gradient = true)

            if (dayState.due.isNotEmpty()) {
                SectionLabel(stringResource(R.string.day_due))
                dayState.due.forEach { item ->
                    FileRow(
                        title = item.task.title,
                        extension = item.attachment?.let { TimeText.extension(it.displayName) } ?: "",
                        deadline = item.task.deadline,
                        windowStart = item.task.windowStart,
                        remainingText = TimeText.dayLabel(item.task.deadline, zone),
                        overdue = item.task.deadline < now,
                        now = now,
                        burning = app.netoverdose.domain.WindowProgress.isBurning(
                            item.task.windowStart, item.task.deadline, now
                        ),
                        onClick = { onOpenTask(item.task.id) }
                    )
                }
            }

            if (dayState.burned.isNotEmpty()) {
                SectionLabel(stringResource(R.string.day_burned))
                dayState.burned.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenTask(item.task.id) }
                            .padding(vertical = palette.rowPad),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FileTile(
                            item.attachment?.let { TimeText.extension(it.displayName) } ?: "·",
                            burnt = true
                        )
                        Text(
                            item.task.title,
                            style = TextStyle(
                                fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            ),
                            color = palette.danger,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (dayState.closed.isNotEmpty()) {
                SectionLabel(stringResource(R.string.day_closed))
                dayState.closed.forEach { item ->
                    val reserve = app.netoverdose.domain.Progress.reserveDays(
                        item.task.toProgress(), now
                    )
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenTask(item.task.id) }
                            .padding(vertical = palette.rowPad),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FileTile(item.attachment?.let { TimeText.extension(it.displayName) } ?: "·")
                        Text(
                            item.task.title,
                            style = TextStyle(
                                fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            ),
                            color = palette.ink,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            signedReserve(reserve) + " д",
                            style = TextStyle(
                                fontFamily = MonoFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold
                            ),
                            color = if (reserve >= 0) palette.accentText else palette.danger
                        )
                    }
                }
            }

            SectionLabel(stringResource(R.string.day_notes))
            if (dayState.notes.isEmpty()) {
                Text(
                    stringResource(R.string.day_notes_empty),
                    style = TextStyle(fontSize = 12.sp),
                    color = palette.muted
                )
            }
            dayState.notes.forEach { note ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        note.text,
                        style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                        color = palette.text,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier.height(36.dp).clip(palette.shape)
                            .clickable { viewModel.deleteNote(note.id) }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", style = TextStyle(fontFamily = MonoFamily, fontSize = 13.sp), color = palette.muted)
                    }
                }
            }

            SharpTextField(
                value = draft,
                onValueChange = { draft = it.take(400) },
                placeholder = stringResource(R.string.day_note_hint),
                singleLine = false
            )
            AccentButton(
                text = stringResource(R.string.day_note_add),
                onClick = { viewModel.addNote(dayStart, now, draft) { draft = "" } },
                modifier = Modifier.fillMaxWidth(),
                height = 44.dp
            )
        }
    }
}
