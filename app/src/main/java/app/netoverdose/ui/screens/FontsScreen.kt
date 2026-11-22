package app.netoverdose.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.netoverdose.NetoverdoseApp
import app.netoverdose.R
import app.netoverdose.design.DesignController
import app.netoverdose.design.LocalDesign
import app.netoverdose.ui.components.AppBackground
import app.netoverdose.ui.components.ItalicAccent
import app.netoverdose.ui.components.LinkButton
import app.netoverdose.ui.components.RainbowHairline
import app.netoverdose.ui.components.SectionLabel
import app.netoverdose.ui.theme.FamilyOption
import app.netoverdose.ui.theme.MonoFamily
import app.netoverdose.ui.theme.SYSTEM_FAMILY_KEY
import app.netoverdose.ui.theme.SerifFamily
import app.netoverdose.ui.theme.availableFamilies

/**
 * Typeface picker (Ещё → Шрифты). Only families that are actually present are listed, so
 * every row previews the real thing; an empty bundle leaves the system set alone.
 */
@Composable
fun FontsScreen(controller: DesignController, onBack: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val palette = LocalDesign.current
    val context = LocalContext.current
    val app = context.applicationContext as NetoverdoseApp
    val families = remember(context) { availableFamilies(context) }
    val current = state.tokens.fontFamily.ifBlank { families.firstOrNull()?.key.orEmpty() }
    val onlySystem = families.none { it.key != SYSTEM_FAMILY_KEY }
    // Resolved during composition; reading resources off LocalContext inside a callback is
    // what Compose lint flags, and this label never changes at runtime.
    val changedText = stringResource(R.string.msg_font_changed)

    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = palette.pad, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column {
                    LinkButton(stringResource(R.string.action_back), onBack)
                    ItalicAccent(
                        stringResource(R.string.fonts_title),
                        modifier = Modifier.semantics { heading() },
                        size = 24.sp
                    )
                    Text(
                        stringResource(R.string.fonts_note),
                        style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, lineHeight = 16.sp),
                        color = palette.muted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    RainbowHairline(Modifier.fillMaxWidth().padding(top = 10.dp), alpha = palette.rainAlpha)
                }
            }

            if (onlySystem) {
                item {
                    Text(
                        stringResource(R.string.fonts_missing),
                        style = TextStyle(fontFamily = SerifFamily, fontSize = 15.sp),
                        color = palette.muted,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.fonts_loaded)) }

            items(families, key = { it.key }) { option ->
                FamilyRow(
                    option = option,
                    active = option.key == current,
                    onSelect = {
                        controller.update { it.copy(fontFamily = option.key) }
                        app.messages.tryEmit(changedText)
                    }
                )
            }

            item { Box(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun FamilyRow(option: FamilyOption, active: Boolean, onSelect: () -> Unit) {
    val palette = LocalDesign.current
    val label = stringResource(option.labelRes)
    val rowCd = stringResource(R.string.a11y_switch_cd, label)
    // The preview paints with the family it is offering, so the choice is visible without
    // applying it first. Missing faces fall back to the current ones.
    val serif = option.serif ?: SerifFamily
    val mono = option.mono ?: MonoFamily

    Column {
        Row(
            Modifier.fillMaxWidth()
                .clip(palette.shape)
                .clickable(role = Role.RadioButton, onClick = onSelect)
                .sizeIn(minHeight = 44.dp)
                .padding(vertical = palette.rowPad, horizontal = 4.dp)
                .semantics {
                    selected = active
                    contentDescription = rowCd
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    label,
                    style = TextStyle(
                        fontFamily = mono, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, letterSpacing = 0.6.sp
                    ),
                    color = palette.accentText
                )
                // The preview must be big enough to actually compare faces: at 11 sp the
                // differences between Inter, Roboto and Noto Serif are invisible.
                Text(
                    stringResource(R.string.font_preview_line),
                    style = TextStyle(
                        fontFamily = serif, fontWeight = FontWeight.Normal,
                        fontSize = 19.sp, lineHeight = 24.sp
                    ),
                    color = palette.ink,
                    maxLines = 1
                )
            }
            Box(
                Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .then(
                            if (active) {
                                Modifier.background(palette.ink).border(1.dp, palette.ink, palette.shape)
                            } else {
                                Modifier.background(Color.Transparent).border(1.dp, palette.rule, palette.shape)
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        stringResource(if (active) R.string.font_active else R.string.font_choose),
                        style = TextStyle(
                            fontFamily = MonoFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold
                        ),
                        color = if (active) palette.bg else palette.muted
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rowRule))
    }
}
