package app.netoverdose.ui.screens

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import app.netoverdose.domain.DeadlineOptions
import app.netoverdose.ui.components.AccentButton
import app.netoverdose.ui.components.ItalicAccent
import app.netoverdose.ui.components.RainbowHairline
import app.netoverdose.ui.components.SectionLabel
import app.netoverdose.ui.components.SegmentedControl
import app.netoverdose.ui.components.SharpSwitch
import app.netoverdose.ui.components.SharpTextField
import app.netoverdose.ui.theme.MonoFamily
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

    fun reset() {
        title = ""
        difficulty = 2
        remind = true
        deadlinePick = 1
        customDeadline = null
        attachment = null
    }

    fun attach(uri: Uri) {
        viewModelScope.launch {
            runCatching { app.repository.attach(uri) }
                .onSuccess { attachment = it }
                .onFailure { app.messages.emit(app.getString(R.string.msg_attach_failed)) }
        }
    }

    fun save(onSaved: () -> Unit) {
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
                    onValueChange = { viewModel.title = it },
                    placeholder = stringResource(R.string.hint_title)
                )
            }

            SectionLabel(stringResource(R.string.label_deadline))
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.deadline_today),
                    stringResource(R.string.deadline_tomorrow),
                    "Пн",
                    stringResource(R.string.deadline_custom)
                ),
                selected = viewModel.deadlinePick,
                onSelect = { viewModel.deadlinePick = it }
            )

            SectionLabel(stringResource(R.string.label_difficulty))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { level ->
                    val active = level - 1 == viewModel.difficulty
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(palette.shape)
                            .background(if (active) palette.ink else Color.Transparent)
                            .border(1.dp, if (active) palette.ink else palette.rule, palette.shape)
                            .clickable { viewModel.difficulty = level - 1 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            level.toString(),
                            style = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            color = if (active) palette.bg else palette.muted
                        )
                    }
                }
            }
            Text(
                stringResource(difficultyLabel(viewModel.difficulty)),
                style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, lineHeight = 15.sp),
                color = palette.muted
            )

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
                SharpSwitch(checked = viewModel.remind, onChange = { viewModel.remind = it })
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
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // keeps the scope referenced so lint does not flag it while state is hoisted
    @Suppress("UNUSED_EXPRESSION")
    scope
}

private fun difficultyLabel(index: Int): Int = when (index) {
    0 -> R.string.diff_1
    1 -> R.string.diff_2
    2 -> R.string.diff_3
    3 -> R.string.diff_4
    else -> R.string.diff_5
}
