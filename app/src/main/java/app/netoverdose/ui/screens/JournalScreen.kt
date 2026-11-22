package app.netoverdose.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
import app.netoverdose.design.DesignEngine
import app.netoverdose.design.LocalDesign
import app.netoverdose.domain.EventType
import app.netoverdose.ui.components.AppBackground
import app.netoverdose.ui.components.ItalicAccent
import app.netoverdose.ui.components.LinkButton
import app.netoverdose.ui.components.RainbowHairline
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

/** Journal row with the task title resolved for readability. */
data class JournalRow(
    val id: Long,
    val type: EventType,
    val at: Long,
    val title: String,
    val payload: String?
)

class JournalViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NetoverdoseApp

    val rows: StateFlow<List<JournalRow>> =
        combine(app.repository.allEvents, app.repository.allTasks) { events, tasks ->
            val titles = tasks.associate { it.task.id to it.task.title }
            events.map { event ->
                JournalRow(
                    id = event.id,
                    type = event.type,
                    at = event.at,
                    title = titles[event.taskId] ?: "—",
                    payload = event.payload
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun JournalScreen(onBack: () -> Unit, viewModel: JournalViewModel = viewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val palette = LocalDesign.current
    val zone = ZoneId.systemDefault()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val filters = listOf(null) + EventType.entries.map { it.name }
    val visible = rows.filter { filter == null || it.type.name == filter }

    AppBackground {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = palette.pad).padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LinkButton(stringResource(R.string.action_back), onBack)
                ItalicAccent(
                    stringResource(R.string.journal_title),
                    modifier = Modifier.semantics { heading() },
                    size = 24.sp
                )
                Text(
                    ru(stringResource(R.string.journal_count, rows.size)),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, letterSpacing = 0.8.sp),
                    color = palette.muted
                )
                RainbowHairline(Modifier.fillMaxWidth().padding(top = 6.dp), alpha = palette.rainAlpha)
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = palette.pad, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filters.forEach { key ->
                    val active = filter == key
                    val label = key ?: stringResource(R.string.journal_all)
                    Box(
                        Modifier.height(32.dp).clip(palette.shape)
                            .background(if (active) palette.ink else androidx.compose.ui.graphics.Color.Transparent)
                            .border(1.dp, if (active) palette.ink else palette.rule, palette.shape)
                            .clickable { filter = key }
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            ru(label),
                            style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, letterSpacing = 0.6.sp),
                            color = if (active) palette.bg else palette.muted,
                            maxLines = 1
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = palette.pad, vertical = 4.dp)
            ) {
                if (visible.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.journal_empty),
                            style = TextStyle(fontFamily = SerifFamily, fontSize = 16.sp),
                            color = palette.muted,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    }
                }
                items(visible, key = { it.id }) { row ->
                    val time = Instant.ofEpochMilli(row.at).atZone(zone)
                    val hhmm = "%02d:%02d".format(time.hour, time.minute)
                    val day = "%02d.%02d".format(time.dayOfMonth, time.monthValue)
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                .semantics { contentDescription = "${row.type.name} · ${row.title}" },
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
        "$day\n$hhmm",
                                style = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp, lineHeight = 13.sp),
                                color = palette.muted,
                                modifier = Modifier.width(42.dp)
                            )
                            Box(
                                Modifier.clip(palette.shape)
                                    .background(palette.tile)
                                    .border(1.dp, palette.rule, palette.shape)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    row.type.name,
                                    style = TextStyle(
                                        fontFamily = MonoFamily, fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp
                                    ),
                                    color = DesignEngine.hsl(typeHue(row.type), 70f, if (palette.dark) 66f else 42f),
                                    maxLines = 1
                                )
                            }
                            Text(
                                row.title,
                                style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
                                color = palette.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rowRule))
                    }
                }
            }
        }
    }
}

private fun typeHue(type: EventType): Float = when (type) {
    EventType.CREATED -> 214f
    EventType.STARTED -> 248f
    EventType.CLOSED -> 150f
    EventType.STEP_DONE -> 282f
    EventType.POSTPONED -> 32f
    EventType.REOPENED -> 188f
    EventType.NOTE -> 318f
}
