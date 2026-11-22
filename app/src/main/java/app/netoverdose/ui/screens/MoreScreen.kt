package app.netoverdose.ui.screens

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.netoverdose.NetoverdoseApp
import app.netoverdose.R
import app.netoverdose.data.EventEntity
import app.netoverdose.design.LocalDesign
import app.netoverdose.ui.components.AccentButton
import app.netoverdose.ui.components.AppBackground
import app.netoverdose.ui.components.BrandMark
import app.netoverdose.ui.components.ItalicAccent
import app.netoverdose.ui.components.RainbowHairline
import app.netoverdose.ui.components.SectionLabel
import app.netoverdose.ui.components.SharpSwitch
import app.netoverdose.ui.theme.MonoFamily
import app.netoverdose.ui.theme.SerifFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

private fun ru(value: String): String = value.uppercase(Locale.forLanguageTag("ru"))

data class MoreUiState(
    val ideaCount: Int = 0,
    val notifications: Boolean = false,
    val busy: Boolean = false
)

class MoreViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NetoverdoseApp
    private val repo = app.repository
    private val notifications = MutableStateFlow(app.reminders.enabled)
    private val busy = MutableStateFlow(false)

    val state: StateFlow<MoreUiState> =
        combine(repo.ideasCount, notifications, busy) { count, enabled, isBusy ->
            MoreUiState(ideaCount = count, notifications = enabled, busy = isBusy)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUiState())

    fun setNotifications(enabled: Boolean, permissionDenied: Boolean = false) {
        app.reminders.enabled = enabled
        notifications.value = enabled
        viewModelScope.launch {
            if (enabled) {
                app.reminders.reschedule(repo.openTasksOnce())
                app.sounds.tick()
                app.messages.emit(app.getString(R.string.msg_notifications_on))
            } else {
                app.reminders.cancelAll()
                app.messages.emit(
                    app.getString(
                        if (permissionDenied) R.string.msg_permission_denied
                        else R.string.msg_notifications_off
                    )
                )
            }
        }
    }

    fun export(uri: Uri) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            val result = runCatching { repo.exportTo(uri) }
            busy.value = false
            result.fold(
                onSuccess = { app.messages.emit(app.getString(R.string.msg_export_ok, it.tasks, it.files)) },
                onFailure = { app.messages.emit(app.getString(R.string.msg_export_failed)) }
            )
        }
    }

    fun restore(uri: Uri) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            val result = runCatching { repo.restoreFrom(uri) }
            busy.value = false
            result.fold(
                onSuccess = { app.messages.emit(app.getString(R.string.msg_import_done, it.tasks, it.files)) },
                onFailure = { app.messages.emit(app.getString(R.string.msg_import_failed)) }
            )
        }
    }

    /** Persists the sound preference and plays a sample when enabling. */
    fun setSound(enabled: Boolean) {
        app.sounds.enabled = enabled
        if (enabled) app.sounds.confirm()
    }

    fun soundEnabled(): Boolean = app.sounds.enabled

    /** Wipes tasks, attachments, events, ideas and notes without reseeding. */
    fun wipeAll() {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            runCatching { repo.wipeAll() }
            busy.value = false
            app.messages.emit(app.getString(R.string.msg_wiped))
        }
    }

    /** Event log for the journal screen, newest first. */
    val journal: StateFlow<List<EventEntity>> = app.repository.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenLab: () -> Unit,
    onOpenIdeas: () -> Unit,
    onOpenJournal: () -> Unit,
    viewModel: MoreViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val journal by viewModel.journal.collectAsStateWithLifecycle()
    val journalCount = journal.size
    val palette = LocalDesign.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showBattery by rememberSaveable { mutableStateOf(false) }
    var showWipeConfirm by rememberSaveable { mutableStateOf(false) }
    var soundOn by rememberSaveable { mutableStateOf(viewModel.soundEnabled()) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) viewModel.export(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingImport = uri }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setNotifications(granted, permissionDenied = !granted) }

    fun requestNotifications(enabled: Boolean) {
        if (!enabled) {
            viewModel.setNotifications(false)
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setNotifications(true)
        }
    }

    AppBackground {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = palette.pad, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Column(Modifier.padding(bottom = 8.dp)) {
                BrandMark(Modifier.semantics { heading() })
                Text(
                    ru(stringResource(R.string.nav_more)),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, letterSpacing = 0.8.sp),
                    color = palette.muted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
            RainbowHairline(Modifier.fillMaxWidth(0.18f), alpha = palette.rainAlpha)
            Box(Modifier.height(6.dp))

            MoreRow("lab", stringResource(R.string.more_lab), stringResource(R.string.more_lab_cap), onOpenLab)

            MoreRow(
                "export",
                stringResource(R.string.more_export),
                if (state.busy) stringResource(R.string.more_export_busy)
                else stringResource(R.string.more_export_cap),
                onClick = {
                    exportLauncher.launch(
                        context.getString(R.string.export_file_name, LocalDate.now().toString())
                    )
                }
            )

            MoreRow(
                "import",
                stringResource(R.string.more_import),
                if (state.busy) stringResource(R.string.more_import_busy)
                else stringResource(R.string.more_import_cap),
                onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
            )

            MoreRow(
                "ideas",
                stringResource(R.string.more_ideas),
                stringResource(R.string.more_ideas_cap, state.ideaCount),
                onOpenIdeas
            )

            val switchCd = stringResource(R.string.a11y_switch_cd, stringResource(R.string.more_notifications))
            MoreRow(
                "bell",
                stringResource(R.string.more_notifications),
                stringResource(
                    if (state.notifications) R.string.more_notifications_on
                    else R.string.more_notifications_off
                ),
                onClick = { requestNotifications(!state.notifications) },
                trailing = {
                    Box(
                        Modifier.height(44.dp).semantics { contentDescription = switchCd },
                        contentAlignment = Alignment.Center
                    ) {
                        SharpSwitch(
                            checked = state.notifications,
                            onChange = { requestNotifications(it) }
                        )
                    }
                }
            )

            MoreRow(
                "battery",
                stringResource(R.string.more_battery),
                stringResource(R.string.more_battery_cap),
                onClick = { showBattery = true }
            )

            val soundCd = stringResource(R.string.a11y_switch_cd, stringResource(R.string.more_sound))
            MoreRow(
                "sound",
                stringResource(R.string.more_sound),
                stringResource(
                    if (soundOn) R.string.more_sound_on else R.string.more_sound_off
                ),
                onClick = {
                    viewModel.setSound(!soundOn)
                    soundOn = !soundOn
                },
                trailing = {
                    Box(
                        Modifier.height(44.dp).semantics { contentDescription = soundCd },
                        contentAlignment = Alignment.Center
                    ) {
                        SharpSwitch(
                            checked = soundOn,
                            onChange = { value ->
                                viewModel.setSound(value)
                                soundOn = value
                            }
                        )
                    }
                }
            )

            MoreRow(
                "journal",
                stringResource(R.string.more_journal),
                stringResource(R.string.journal_count, journalCount),
                onClick = onOpenJournal
            )

            MoreRow(
                "wipe",
                stringResource(R.string.more_wipe),
                stringResource(R.string.more_wipe_cap),
                onClick = { showWipeConfirm = true }
            )

            MoreRow(
                "about",
                stringResource(R.string.more_about),
                stringResource(R.string.more_about_cap),
                onClick = { showAbout = true }
            )

            Box(Modifier.height(24.dp))
        }
    }

    if (showWipeConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.wipe_title),
            text = stringResource(R.string.wipe_text),
            onConfirm = { showWipeConfirm = false; viewModel.wipeAll() },
            onDismiss = { showWipeConfirm = false }
        )
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.import_title),
            text = stringResource(R.string.import_text),
            onConfirm = { pendingImport = null; viewModel.restore(uri) },
            onDismiss = { pendingImport = null }
        )
    }

    if (showBattery) {
        val noApp = stringResource(R.string.msg_no_app)
        InfoSheet(onDismiss = { showBattery = false }) {
            ItalicAccent(stringResource(R.string.battery_title), size = 20.sp)
            Text(
                stringResource(R.string.battery_text),
                style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
                color = palette.text
            )
            Text(
                stringResource(R.string.battery_note),
                style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, lineHeight = 16.sp),
                color = palette.muted
            )
            AccentButton(
                text = stringResource(R.string.battery_action),
                onClick = {
                    // Opens the app's system detail page; battery optimisation is user-controlled there.
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                    } catch (error: ActivityNotFoundException) {
                        (context.applicationContext as NetoverdoseApp).messages.tryEmit(noApp)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showAbout) {
        InfoSheet(onDismiss = { showAbout = false }) {
            ItalicAccent(stringResource(R.string.about_quote))
            Text(
                stringResource(R.string.about_text),
                style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
                color = palette.text
            )
            SectionLabel(stringResource(R.string.about_facts))
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val palette = LocalDesign.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = palette.shape,
        containerColor = palette.bg,
        titleContentColor = palette.ink,
        textContentColor = palette.text,
        title = {
            Text(
                title,
                style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            )
        },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm, shape = palette.shape) {
                Text(stringResource(R.string.action_confirm), color = palette.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = palette.shape) {
                Text(stringResource(R.string.action_cancel), color = palette.muted)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val palette = LocalDesign.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = palette.shape,
        containerColor = palette.bg,
        contentColor = palette.text,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp, bottom = 12.dp).height(3.dp)
                    .fillMaxWidth(0.1f).background(palette.rule)
            )
        }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = palette.pad).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RainbowHairline(Modifier.fillMaxWidth(), alpha = palette.rainAlpha)
            content()
        }
    }
}

@Composable
private fun MoreRow(
    glyph: String,
    title: String,
    caption: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    val palette = LocalDesign.current
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(vertical = palette.rowPad, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MoreGlyph(glyph)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    color = palette.ink
                )
                Text(
                    caption,
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, lineHeight = 15.sp),
                    color = palette.muted
                )
            }
            if (trailing != null) {
                trailing()
            } else {
                Text("→", style = TextStyle(fontFamily = MonoFamily, fontSize = 16.sp), color = palette.accentText)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rowRule))
    }
}

@Composable
private fun MoreGlyph(kind: String) {
    val palette = LocalDesign.current
    Box(
        Modifier.size(40.dp).clip(palette.shape).background(palette.tile)
            .border(1.dp, palette.rule, palette.shape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = 1.6.dp.toPx()
            val w = size.width
            val h = size.height
            val color = palette.ink
            val knob = 4.dp.toPx()
            when (kind) {
                "lab" -> {
                    val rows = listOf(h * 0.25f, h * 0.5f, h * 0.75f)
                    val knobs = listOf(w * 0.3f, w * 0.66f, w * 0.45f)
                    rows.forEachIndexed { index, y ->
                        drawLine(color, Offset(0f, y), Offset(w, y), strokeWidth = stroke)
                        drawRect(
                            color,
                            topLeft = Offset(knobs[index] - knob / 2f, y - knob / 2f),
                            size = Size(knob, knob)
                        )
                    }
                }
                "export" -> {
                    drawLine(color, Offset(w / 2f, h * 0.08f), Offset(w / 2f, h * 0.66f), strokeWidth = stroke)
                    drawLine(color, Offset(w * 0.26f, h * 0.44f), Offset(w / 2f, h * 0.68f), strokeWidth = stroke)
                    drawLine(color, Offset(w * 0.74f, h * 0.44f), Offset(w / 2f, h * 0.68f), strokeWidth = stroke)
                    drawLine(color, Offset(w * 0.08f, h * 0.92f), Offset(w * 0.92f, h * 0.92f), strokeWidth = stroke)
                }
                "import" -> {
                    drawLine(color, Offset(w / 2f, h * 0.66f), Offset(w / 2f, h * 0.08f), strokeWidth = stroke)
                    drawLine(color, Offset(w * 0.26f, h * 0.32f), Offset(w / 2f, h * 0.08f), strokeWidth = stroke)
                    drawLine(color, Offset(w * 0.74f, h * 0.32f), Offset(w / 2f, h * 0.08f), strokeWidth = stroke)
                    drawLine(color, Offset(w * 0.08f, h * 0.92f), Offset(w * 0.92f, h * 0.92f), strokeWidth = stroke)
                }
                "ideas" -> {
                    drawCircle(
                        color, radius = w * 0.28f, center = Offset(w / 2f, h * 0.36f),
                        style = Stroke(width = stroke)
                    )
                    drawLine(color, Offset(w * 0.34f, h * 0.78f), Offset(w * 0.66f, h * 0.78f), strokeWidth = stroke)
                    drawLine(color, Offset(w * 0.42f, h * 0.93f), Offset(w * 0.58f, h * 0.93f), strokeWidth = stroke)
                }
                "bell" -> {
                    drawArc(
                        color = color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                        topLeft = Offset(w * 0.18f, h * 0.16f), size = Size(w * 0.64f, h * 0.62f),
                        style = Stroke(width = stroke)
                    )
                    drawLine(color, Offset(w * 0.18f, h * 0.47f), Offset(w * 0.13f, h * 0.74f), strokeWidth = stroke)
                    drawLine(color, Offset(w * 0.82f, h * 0.47f), Offset(w * 0.87f, h * 0.74f), strokeWidth = stroke)
                    drawLine(color, Offset(w * 0.08f, h * 0.74f), Offset(w * 0.92f, h * 0.74f), strokeWidth = stroke)
                    drawCircle(color, radius = 1.6.dp.toPx(), center = Offset(w / 2f, h * 0.9f))
                }
                "demo" -> {
                    drawArc(
                        color = color, startAngle = 45f, sweepAngle = 275f, useCenter = false,
                        topLeft = Offset(w * 0.14f, h * 0.14f), size = Size(w * 0.72f, h * 0.72f),
                        style = Stroke(width = stroke)
                    )
                    drawLine(color, Offset(w * 0.70f, h * 0.10f), Offset(w * 0.80f, h * 0.32f), strokeWidth = stroke)
                    drawLine(color, Offset(w * 0.80f, h * 0.32f), Offset(w * 0.57f, h * 0.34f), strokeWidth = stroke)
                }
                "battery" -> {
                    drawRect(
                        color, topLeft = Offset(w * 0.1f, h * 0.28f), size = Size(w * 0.68f, h * 0.44f),
                        style = Stroke(width = stroke)
                    )
                    drawRect(color, topLeft = Offset(w * 0.82f, h * 0.42f), size = Size(w * 0.1f, h * 0.16f))
                    drawRect(color, topLeft = Offset(w * 0.18f, h * 0.36f), size = Size(w * 0.24f, h * 0.28f))
                }
                else -> {
                    drawCircle(
                        color, radius = w * 0.42f, center = Offset(w / 2f, h / 2f),
                        style = Stroke(width = stroke)
                    )
                    drawCircle(color, radius = 1.3.dp.toPx(), center = Offset(w / 2f, h * 0.29f))
                    drawLine(color, Offset(w / 2f, h * 0.45f), Offset(w / 2f, h * 0.73f), strokeWidth = stroke)
                }
            }
        }
    }
}
