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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import app.netoverdose.data.IdeaEntity
import app.netoverdose.design.LocalDesign
import app.netoverdose.domain.TimeText
import app.netoverdose.ui.components.AccentButton
import app.netoverdose.ui.components.AppBackground
import app.netoverdose.ui.components.ItalicAccent
import app.netoverdose.ui.components.LinkButton
import app.netoverdose.ui.components.MiniButton
import app.netoverdose.ui.components.RainbowHairline
import app.netoverdose.ui.components.SharpTextField
import app.netoverdose.ui.theme.MonoFamily
import app.netoverdose.ui.theme.SerifFamily
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

class IdeasViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NetoverdoseApp

    val ideas: StateFlow<List<IdeaEntity>> = app.repository.ideas
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(text: String, situation: String, onDone: () -> Unit) {
        if (text.isBlank()) return
        viewModelScope.launch {
            app.repository.addIdea(text, situation)
            app.messages.emit(app.getString(R.string.msg_idea_added))
            onDone()
        }
    }

    fun bump(id: Long) {
        viewModelScope.launch {
            app.repository.bumpIdea(id)
            app.messages.emit(app.getString(R.string.msg_idea_bumped))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            app.repository.deleteIdea(id)
            app.messages.emit(app.getString(R.string.ideas_deleted))
        }
    }
}

@Composable
fun IdeasScreen(onBack: () -> Unit, viewModel: IdeasViewModel = viewModel()) {
    val ideas by viewModel.ideas.collectAsStateWithLifecycle()
    val palette = LocalDesign.current
    var draft by rememberSaveable { mutableStateOf("") }
    var situation by rememberSaveable { mutableStateOf("") }
    val zone = ZoneId.systemDefault()

    AppBackground {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = palette.pad)
                    .padding(top = 10.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LinkButton(stringResource(R.string.action_back), onBack)
                ItalicAccent(
                    stringResource(R.string.ideas_title),
                    modifier = Modifier.semantics { heading() },
                    size = 24.sp
                )
                Text(
                    stringResource(R.string.ideas_note),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, lineHeight = 15.sp),
                    color = palette.muted
                )
                Text(
                    stringResource(R.string.ideas_sorted_note),
                    style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
                    color = palette.text
                )
                RainbowHairline(Modifier.fillMaxWidth().padding(top = 6.dp), alpha = palette.rainAlpha)
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = palette.pad, vertical = 4.dp)
            ) {
                if (ideas.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.ideas_empty),
                            style = TextStyle(fontFamily = SerifFamily, fontSize = 16.sp),
                            color = palette.muted,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    }
                }
                items(ideas, key = { it.id }) { idea ->
                    IdeaRow(
                        idea = idea,
                        dateLabel = TimeText.dayLabel(idea.createdAt, zone),
                        onBump = { viewModel.bump(idea.id) },
                        onDelete = { viewModel.delete(idea.id) }
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth().background(palette.backdrop)
                    .padding(horizontal = palette.pad, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SharpTextField(
                    value = draft,
                    onValueChange = { draft = it.take(140) },
                    placeholder = stringResource(R.string.ideas_hint)
                )
                SharpTextField(
                    value = situation,
                    onValueChange = { situation = it.take(400) },
                    placeholder = stringResource(R.string.ideas_situation_hint),
                    singleLine = false
                )
                AccentButton(
                    text = stringResource(R.string.ideas_add),
                    onClick = {
                        viewModel.add(draft, situation) {
                            draft = ""
                            situation = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp
                )
            }
        }
    }
}

@Composable
private fun IdeaRow(idea: IdeaEntity, dateLabel: String, onBump: () -> Unit, onDelete: () -> Unit) {
    val palette = LocalDesign.current
    val rowCd = stringResource(R.string.ideas_row_cd, idea.text, idea.hits)
    val bumpCd = stringResource(R.string.ideas_bump_cd)
    val deleteCd = stringResource(R.string.a11y_delete_idea_cd)
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp)
                .semantics(mergeDescendants = true) { contentDescription = rowCd },
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.width(38.dp).height(28.dp).clip(palette.shape)
                    .background(palette.tile).border(1.dp, palette.rule, palette.shape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.ideas_hits_badge, idea.hits),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = palette.accentText
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    idea.text,
                    style = TextStyle(
                        fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp, lineHeight = 20.sp
                    ),
                    color = palette.ink
                )
                if (idea.situation.isNotBlank()) {
                    Text(
                        idea.situation,
                        style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
                        color = palette.text
                    )
                }
                Text(
                    dateLabel,
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp),
                    color = palette.muted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                MiniButton(
                    stringResource(R.string.ideas_bump),
                    onBump,
                    Modifier.semantics { contentDescription = bumpCd }
                )
                Box(
                    Modifier.height(44.dp).padding(horizontal = 6.dp)
                        .clickable(onClick = onDelete)
                        .semantics { contentDescription = deleteCd },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✕",
                        style = TextStyle(fontFamily = MonoFamily, fontSize = 14.sp),
                        color = palette.muted
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rowRule))
    }
}
