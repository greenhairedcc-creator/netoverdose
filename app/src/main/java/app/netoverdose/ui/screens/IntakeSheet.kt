package app.netoverdose.ui.screens

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.netoverdose.NetoverdoseApp
import app.netoverdose.R
import app.netoverdose.data.StoredAttachment
import app.netoverdose.design.LocalDesign
import app.netoverdose.domain.AutoDifficulty
import app.netoverdose.domain.DeadlineOptions
import app.netoverdose.domain.HistoryTask
import app.netoverdose.notify.rememberNotificationGate
import app.netoverdose.ui.components.AccentButton
import app.netoverdose.ui.components.ItalicAccent
import app.netoverdose.ui.components.RainbowHairline
import app.netoverdose.ui.components.SectionLabel
import app.netoverdose.ui.components.SegmentedControl
import app.netoverdose.ui.components.SharpSwitch
import app.netoverdose.ui.components.SharpTextField
import app.netoverdose.ui.theme.MonoFamily
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Holds the intake form so an in-flight attach survives sheet recomposition. */
class IntakeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NetoverdoseApp
    private val zone: ZoneId = ZoneId.systemDefault()

    var title by mutableStateOf("")
    var difficulty by mutableStateOf(2)
    var remind by mutableStateOf(true)
    var deadlinePick by mutableStateOf(1)
    var customDeadline by mutableStateOf<Long?>(null)
    var attachment by mutableStateOf<StoredAttachment?>(null)

    /** True once the user picks a level by hand; the auto estimate then stops overwriting it. */
    var difficultyLocked by mutableStateOf(false)
        private set

    /** Reasons behind the current suggestion, shown as chips under the difficulty row. */
    var reasons by mutableStateOf<List<String>>(emptyList())
        private set

    /** Past tasks used as history for the estimate. */
    private var history: List<HistoryTask> = emptyList()

    init {
        viewModelScope.launch {
            history = app.repository.allTasks.first().map {
                HistoryTask(it.task.title, it.task.difficulty)
            }
        }
    }

    /**
     * Seeds the form from a shared file (A2). Difficulty stays at the default and the
     * estimate keeps running, so the user is still the one who picks both numbers;
     * this only saves retyping the title.
     */
    fun prefill(stored: StoredAttachment) {
        attachment = stored
        if (title.isBlank()) title = stored.displayName
        refreshEstimate()
    }

    /** Picks a level by hand and freezes the estimate. */
    fun pickDifficulty(level: Int) {
        difficulty = level
        difficultyLocked = true
        reasons = emptyList()
    }

    /** Re-runs the heuristic whenever the title, file or deadline changes. */
    fun refreshEstimate() {
        if (difficultyLocked) return
        val now = System.currentTimeMillis()
        val deadline = resolveDeadline(now)
        val guess = AutoDifficulty.estimate(
            title = title.ifBlank { attachment?.displayName ?: "" },
            fileSizeBytes = attachment?.sizeBytes,
            deadline = deadline,
            now = now,
            endOfToday = DeadlineOptions.today(now, zone),
            history = history
        )
        difficulty = guess.value - 1
        reasons = guess.reasons
    }

    fun reset() {
        title = ""
        difficulty = 2
        remind = true
        deadlinePick = 1
        customDeadline = null
        attachment = null
        difficultyLocked = false
        reasons = emptyList()
        refreshEstimate()
    }

    fun attach(uri: Uri) {
        viewModelScope.launch {
            runCatching { app.repository.attach(uri) }
                .onSuccess {
                    attachment = it
                    refreshEstimate()
                }
                .onFailure { app.messages.emit(app.getString(R.string.msg_attach_failed)) }
        }
    }

    fun save(onSaved: () -> Unit) {
        if (!canSave) return
        val now = System.currentTimeMillis()
        val name = title.trim().ifEmpty {
            attachment?.displayName ?: app.getString(R.string.hint_title)
        }
        viewModelScope.launch {
            app.repository.createTask(
                title = name,
                difficulty = difficulty + 1,
                deadline = resolveDeadline(now),
                remind = remind,
                stored = attachment
            )
            app.sounds.confirm()
            app.messages.emit(app.getString(R.string.msg_saved))
            onSaved()
        }
    }

    private fun resolveDeadline(now: Long): Long = when (deadlinePick) {
        0 -> DeadlineOptions.today(now, zone)
        1 -> DeadlineOptions.tomorrow(now, zone)
        2 -> DeadlineOptions.nextMonday(now, zone)
        else -> customDeadline ?: DeadlineOptions.tomorrow(now, zone)
    }

    /** A custom date is mandatory when the "Своя" segment is chosen, so Save stays disabled until then. */
    val canSave: Boolean
        get() = deadlinePick != CUSTOM_PICK || customDeadline != null

    companion object {
        const val CUSTOM_PICK = 3
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntakeSheet(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: IntakeViewModel = viewModel()
) {
    val palette = LocalDesign.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as NetoverdoseApp
    val deniedText = stringResource(R.string.msg_permission_denied)

    // Switching the reminder on for the first time must also ask for POST_NOTIFICATIONS (A4).
    val gate = rememberNotificationGate { granted ->
        if (granted) {
            viewModel.remind = true
            scope.launch { app.reminders.setEnabled(true, app.repository.openTasksOnce()) }
        } else {
            app.messages.tryEmit(deniedText)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.attach(uri)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.bg,
        scrimColor = Color.Black.copy(alpha = 0.4f)
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = palette.pad).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ItalicAccent(stringResource(R.string.action_add), size = 22.sp)
            Text(
                stringResource(R.string.intake_note),
                style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, lineHeight = 15.sp),
                color = palette.muted
            )
            RainbowHairline(Modifier.fillMaxWidth(), alpha = palette.rainAlpha)

            SectionLabel(stringResource(R.string.label_title))
            Box(Modifier.fillMaxWidth()) {
                SharpTextField(
                    value = viewModel.title,
                    onValueChange = { text ->
                        viewModel.title = text
                        viewModel.refreshEstimate()
                    },
                    placeholder = stringResource(R.string.hint_title)
                )
            }

            SectionLabel(stringResource(R.string.label_deadline))
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.deadline_today),
                    stringResource(R.string.deadline_tomorrow),
                    stringResource(R.string.deadline_monday),
                    stringResource(R.string.deadline_custom)
                ),
                selected = viewModel.deadlinePick,
                onSelect = { viewModel.deadlinePick = it }
            )

            // The picker lives inside the sheet: a nested DatePickerDialog would be drawn
            // behind the bottom sheet and swallow the selection (B01).
            if (viewModel.deadlinePick == IntakeViewModel.CUSTOM_PICK) {
                CustomDateBlock(
                    selected = viewModel.customDeadline,
                    zone = ZoneId.systemDefault(),
                    onSelect = { viewModel.customDeadline = it }
                )
            }

            SectionLabel(stringResource(R.string.label_difficulty))
            DifficultyPicker(
                selected = viewModel.difficulty,
                onSelect = { level -> viewModel.pickDifficulty(level) }
            )
            Text(
                stringResource(difficultyLabel(viewModel.difficulty)),
                style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, lineHeight = 15.sp),
                color = palette.muted
            )
            if (viewModel.reasons.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    viewModel.reasons.forEach { reason ->
                        Box(
                            Modifier.clip(palette.shape).background(palette.tile)
                                .border(1.dp, palette.rule, palette.shape)
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text(
                                reason,
                                style = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp, letterSpacing = 0.4.sp),
                                color = palette.accentText,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.label_remind),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp),
                    color = palette.text
                )
                SharpSwitch(
                    checked = viewModel.remind,
                    onChange = { value ->
                        if (value) gate(true) else viewModel.remind = false
                    }
                )
            }

            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(palette.shape)
                    .border(1.dp, palette.rule, palette.shape)
                    .clickable { picker.launch(arrayOf("*/*")) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    viewModel.attachment?.displayName ?: stringResource(R.string.intake_attach),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp),
                    color = palette.muted,
                    maxLines = 1
                )
            }

            AccentButton(
                text = stringResource(R.string.action_save),
                onClick = { viewModel.save(onSaved) },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.canSave
            )
        }
    }

    // keeps the scope referenced so lint does not flag it while state is hoisted
    @Suppress("UNUSED_EXPRESSION")
    scope
}


