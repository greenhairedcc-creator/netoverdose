package app.netoverdose.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.netoverdose.NetoverdoseApp
import app.netoverdose.R
import app.netoverdose.design.DesignController
import app.netoverdose.design.DesignEngine
import app.netoverdose.design.LocalDesign
import app.netoverdose.ui.components.AppBackground
import app.netoverdose.ui.components.ItalicAccent
import app.netoverdose.ui.components.LinkButton
import app.netoverdose.ui.components.MiniButton
import app.netoverdose.ui.components.RainbowHairline
import app.netoverdose.ui.components.SectionLabel
import app.netoverdose.ui.components.SegmentedControl
import app.netoverdose.ui.components.SharpSlider
import app.netoverdose.ui.components.SharpSwitch
import app.netoverdose.ui.components.SwatchDot
import app.netoverdose.ui.theme.MonoFamily
import app.netoverdose.ui.theme.availableFamilies
import java.util.Locale

@Composable
fun LabScreen(controller: DesignController, onBack: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val tokens = state.tokens
    val palette = LocalDesign.current
    val context = LocalContext.current
    val app = context.applicationContext as NetoverdoseApp
    // Only families whose TTFs are actually bundled are offered; empty means none are.
    val families = remember(context) { availableFamilies(context) }
    // Resolved during composition: reading resources off LocalContext inside a callback is
    // what Compose lint flags, and these labels never change at runtime.
    val shareTitle = stringResource(R.string.share_tokens)
    val noAppText = stringResource(R.string.msg_no_app)
    val presetText = stringResource(R.string.msg_preset)
    val resetText = stringResource(R.string.msg_reset)
    val randomText = stringResource(R.string.msg_random)

    fun share() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, shareTitle)
            .putExtra(Intent.EXTRA_TEXT, controller.exportText())
        try {
            context.startActivity(Intent.createChooser(intent, shareTitle))
        } catch (error: ActivityNotFoundException) {
            app.messages.tryEmit(noAppText)
        }
    }

    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = palette.pad, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column {
                    LinkButton(stringResource(R.string.action_back), onBack)
                    ItalicAccent(stringResource(R.string.lab_title), size = 24.sp)
                    Text(
                        stringResource(R.string.lab_sub).uppercase(Locale.forLanguageTag("ru")),
                        style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, letterSpacing = 0.7.sp),
                        color = palette.muted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    RainbowHairline(Modifier.fillMaxWidth().padding(top = 10.dp), alpha = palette.rainAlpha)
                }
            }

            item {
                Group(stringResource(R.string.group_presets)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "ice" to R.string.preset_ice,
                            "steel" to R.string.preset_steel,
                            "prism" to R.string.preset_prism,
                            "night" to R.string.preset_night
                        ).forEach { (key, title) ->
                            val active = state.preset == key
                            Box(
                                Modifier.weight(1f).height(44.dp).clip(palette.shape)
                                    .background(if (active) palette.ink else Color.Transparent)
                                    .border(1.dp, if (active) palette.ink else palette.rule, palette.shape)
                                    .clickable {
                                        controller.applyPreset(key)
                                        app.messages.tryEmit(presetText)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(title).uppercase(Locale.forLanguageTag("ru")),
                                    style = TextStyle(
                                        fontFamily = MonoFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.6.sp
                                    ),
                                    color = if (active) palette.bg else palette.muted,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }

            item {
                Group(stringResource(R.string.group_palette)) {
                    SliderRow(
                        title = stringResource(R.string.ctl_cold),
                        value = tokens.cold.toFloat(), min = 0f, max = 100f,
                        valueText = stringResource(R.string.unit_percent, tokens.cold),
                        onValue = { v -> controller.update { it.copy(cold = v.toInt()) } }
                    )
                    SliderRow(
                        title = stringResource(R.string.ctl_hue),
                        value = tokens.accentHue.toFloat(), min = 0f, max = 360f,
                        valueText = stringResource(R.string.unit_degree, tokens.accentHue),
                        rainbowTrack = true,
                        swatch = palette.accent,
                        onValue = { v -> controller.update { it.copy(accentHue = v.toInt()) } }
                    )
                    SliderRow(
                        title = stringResource(R.string.ctl_rainbow),
                        value = tokens.rainbow.toFloat(), min = 0f, max = 100f,
                        valueText = stringResource(R.string.unit_percent, tokens.rainbow),
                        onValue = { v -> controller.update { it.copy(rainbow = v.toInt()) } }
                    )
                    SwitchRow(stringResource(R.string.ctl_dark), tokens.dark) { v ->
                        controller.update { it.copy(dark = v) }
                    }
                }
            }

            item {
                Group(stringResource(R.string.group_background)) {
                    SliderRow(
                        title = stringResource(R.string.ctl_sharp),
                        value = tokens.sharp.toFloat(), min = 0f, max = 100f,
                        valueText = stringResource(R.string.unit_percent, tokens.sharp),
                        onValue = { v -> controller.update { it.copy(sharp = v.toInt()) } }
                    )
                    SliderRow(
                        title = stringResource(R.string.ctl_grain),
                        value = tokens.grain.toFloat(), min = 0f, max = 100f,
                        valueText = stringResource(R.string.unit_percent, tokens.grain),
                        onValue = { v -> controller.update { it.copy(grain = v.toInt()) } }
                    )
                    SwitchRow(stringResource(R.string.ctl_grid), tokens.grid) { v ->
                        controller.update { it.copy(grid = v) }
                    }
                }
            }

            item {
                Group(stringResource(R.string.group_form)) {
                    SliderRow(
                        title = stringResource(R.string.ctl_radius),
                        value = tokens.radius.toFloat(), min = 0f, max = 14f,
                        valueText = stringResource(R.string.unit_dp, tokens.radius),
                        onValue = { v -> controller.update { it.copy(radius = v.toInt()) } }
                    )
                    SliderRow(
                        title = stringResource(R.string.ctl_slant),
                        value = tokens.slant.toFloat(), min = 0f, max = 14f,
                        valueText = stringResource(R.string.unit_degree, tokens.slant),
                        onValue = { v -> controller.update { it.copy(slant = v.toInt()) } }
                    )
                    SwitchRow(stringResource(R.string.ctl_italics), tokens.italics) { v ->
                        controller.update { it.copy(italics = v) }
                    }
                    SectionLabel(stringResource(R.string.ctl_density), Modifier.padding(top = 8.dp, bottom = 6.dp))
                    SegmentedControl(
                        options = listOf(
                            stringResource(R.string.density_compact),
                            stringResource(R.string.density_normal),
                            stringResource(R.string.density_airy)
                        ),
                        selected = tokens.density,
                        onSelect = { index -> controller.update { it.copy(density = index) } }
                    )
                }
            }

            if (families.size > 1) {
                item {
                    Group(stringResource(R.string.group_fonts)) {
                        SegmentedControl(
                            options = families.map { it.key },
                            selected = families.indexOfFirst { it.key == tokens.fontFamily },
                            onSelect = { index ->
                                controller.update { it.copy(fontFamily = families[index].key) }
                            }
                        )
                        Text(
                            stringResource(R.string.group_fonts_hint),
                            style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, lineHeight = 15.sp),
                            color = palette.muted
                        )
                    }
                }
            }

            item {
                Group(stringResource(R.string.group_motion)) {
                    SliderRow(
                        title = stringResource(R.string.ctl_wave),
                        value = tokens.wave.toFloat(), min = 0f, max = 100f,
                        valueText = if (tokens.wave == 0) stringResource(R.string.unit_static)
                        else stringResource(R.string.unit_percent, tokens.wave),
                        onValue = { v -> controller.update { it.copy(wave = v.toInt()) } }
                    )
                    SliderRow(
                        title = stringResource(R.string.ctl_glow),
                        value = tokens.glow.toFloat(), min = 0f, max = 100f,
                        valueText = stringResource(R.string.unit_percent, tokens.glow),
                        onValue = { v -> controller.update { it.copy(glow = v.toInt()) } }
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            MiniButton(stringResource(R.string.action_reset), onClick = {
                                controller.applyPreset("ice")
                                app.messages.tryEmit(resetText)
                            }, modifier = Modifier.fillMaxWidth())
                        }
                        Box(Modifier.weight(1f)) {
                            MiniButton(stringResource(R.string.action_random), onClick = {
                                controller.randomize()
                                app.messages.tryEmit(randomText)
                            }, modifier = Modifier.fillMaxWidth())
                        }
                        Box(Modifier.weight(1f)) {
                            MiniButton(stringResource(R.string.action_export), onClick = { share() }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            item {
                Column(
                    Modifier.fillMaxWidth().clip(palette.shape).border(1.dp, palette.rule, palette.shape).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SwatchDot(palette.ink)
                        SwatchDot(palette.accent)
                        palette.spectrum.forEach { SwatchDot(it) }
                    }
                    Text(
                        "INK ${DesignEngine.hex(palette.ink)} · ACCENT ${DesignEngine.hex(palette.accent)} · BG ${DesignEngine.hex(palette.bg)}",
                        style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, lineHeight = 15.sp),
                        color = palette.muted
                    )
                    Text(
                        stringResource(R.string.lab_hint),
                        style = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, lineHeight = 15.sp),
                        color = palette.muted
                    )
                }
            }

            item { Box(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val palette = LocalDesign.current
    Column(
        Modifier.fillMaxWidth().clip(palette.shape).border(1.dp, palette.rule, palette.shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.height(8.dp).fillMaxWidth(0.03f).background(palette.rainbowBrush))
            SectionLabel(title)
        }
        content()
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    min: Float,
    max: Float,
    valueText: String,
    onValue: (Float) -> Unit,
    rainbowTrack: Boolean = false,
    swatch: Color? = null
) {
    val palette = LocalDesign.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp), color = palette.text)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (swatch != null) SwatchDot(swatch)
                Text(
                    valueText,
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = palette.accentText
                )
            }
        }
        SharpSlider(value = value, min = min, max = max, onValue = onValue, rainbowTrack = rainbowTrack)
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalDesign.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp), color = palette.text)
        SharpSwitch(checked = checked, onChange = onChange)
    }
}
