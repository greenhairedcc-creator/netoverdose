package app.netoverdose.design

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.netoverdose.domain.WindowProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

data class DesignTokens(
    val cold: Int = 72,
    val accentHue: Int = 228,
    val rainbow: Int = 78,
    val sharp: Int = 74,
    val grain: Int = 42,
    val grid: Boolean = true,
    val dark: Boolean = false,
    val italics: Boolean = true,
    val slant: Int = 8,
    val radius: Int = 0,
    val tileRadius: Int = 10,
    val density: Int = 1,
    val wave: Int = 55,
    val glow: Int = 45,
    val hatch: Boolean = true,
    val peaks: Boolean = true,
    val motion: Int = 100,
    /** Typeface set key; empty means the bundled fonts, when present. */
    val fontFamily: String = ""
)

data class DesignState(val tokens: DesignTokens = DesignTokens(), val preset: String = "ice")

object DesignPresets {
    val ice = DesignTokens()
    val steel = DesignTokens(cold = 30, accentHue = 215, rainbow = 12, sharp = 92, grain = 56, slant = 4, wave = 25, glow = 14)
    val prism = DesignTokens(cold = 85, accentHue = 262, rainbow = 100, sharp = 86, grain = 38, slant = 12, wave = 80, glow = 72)
    val night = DesignTokens(cold = 90, accentHue = 200, rainbow = 85, sharp = 80, grain = 50, dark = true, wave = 55, glow = 68)

    fun byKey(key: String): DesignTokens = when (key) {
        "steel" -> steel
        "prism" -> prism
        "night" -> night
        else -> ice
    }

    fun random(): DesignTokens {
        fun r(from: Int, to: Int) = Random.nextInt(from, to + 1)
        return DesignTokens(
            cold = r(40, 100), accentHue = r(0, 360), rainbow = r(30, 100), sharp = r(50, 100),
            grain = r(20, 70), grid = Random.nextInt(10) > 1, dark = Random.nextInt(4) == 0,
            italics = true, slant = r(0, 14), radius = if (Random.nextInt(10) < 6) 0 else r(2, 10),
            density = r(0, 2), wave = r(20, 90), glow = r(10, 80),
            tileRadius = r(4, 18), hatch = true, peaks = true, motion = r(50, 100)
        )
    }
}

data class DesignPalette(
    val bg: Color,
    val tile: Color,
    val backdrop: Color,
    val rule: Color,
    val rowRule: Color,
    val off: Color,
    val ink: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val danger: Color,
    val success: Color,
    val warn: Color,
    val spectrum: List<Color>,
    val waveColors: List<Color>,
    val rainbowBrush: Brush,
    val rainbowHard: Brush,
    val rainAlpha: Float,
    val glowAlpha: Float,
    val grainAlpha: Float,
    val gridAlpha: Float,
    val waveDurationMs: Int,
    /** Slide period for gradient text; 0 when motion is disabled. */
    val gradientDurationMs: Int,
    val slantDeg: Float,
    val shape: Shape,
    val tileShape: Shape,
    val pad: Dp,
    val rowPad: Dp,
    val gap: Dp,
    val italics: Boolean,
    val dark: Boolean,
    val sharp: Int,
    val hatch: Boolean,
    val peaks: Boolean,
    val motion: Float
)

val LocalDesign = staticCompositionLocalOf<DesignPalette> {
    error("DesignPalette is not provided")
}

object DesignEngine {

    private val spectrumHues = listOf(188f, 214f, 248f, 282f, 318f, 352f, 28f, 88f)

    fun derive(t: DesignTokens): DesignPalette {
        val hue = 222f - t.cold * 0.30f
        val sat = 10f + t.cold * 0.20f
        val dark = t.dark

        val bg: Color
        val tile: Color
        val backdrop: Color
        val rule: Color
        val rowRule: Color
        val off: Color
        val ink: Color
        val text: Color
        val muted: Color

        if (!dark) {
            bg = hsl(hue, sat * 1.1f, 97.5f)
            tile = hsl(hue, sat, 94f)
            backdrop = hsl(hue, sat, 90f)
            rule = hsl(hue, sat * 0.8f, 84f)
            rowRule = hsl(hue, sat * 0.8f, 89f)
            off = hsl(hue, sat * 0.7f, 80f)
            ink = hsl(hue, min(60f, sat * 2.2f), 9f)
            text = hsl(hue, sat * 0.9f, 24f)
            muted = hsl(hue, sat * 0.7f, 42f)
        } else {
            bg = hsl(hue, sat * 1.2f, 7f)
            tile = hsl(hue, sat, 11.5f)
            backdrop = hsl(hue, sat, 5f)
            rule = hsl(hue, sat * 0.8f, 21f)
            rowRule = hsl(hue, sat * 0.8f, 15.5f)
            off = hsl(hue, sat * 0.7f, 25f)
            ink = hsl(hue, sat * 0.9f, 93f)
            text = hsl(hue, sat * 0.6f, 80f)
            muted = hsl(hue, sat * 0.5f, 58f)
        }

        var textLight = if (dark) 62f else 46f
        var accentText = hsl(t.accentHue.toFloat(), 88f, textLight)
        var guard = 0
        while (contrast(accentText, bg) < 4.5f && guard < 32) {
            textLight = (textLight + if (dark) 2f else -2f).coerceIn(0f, 100f)
            accentText = hsl(t.accentHue.toFloat(), 88f, textLight)
            guard++
        }

        // Filled accent (buttons). Only the label contrast matters: `onAccent` is picked to suit
        // the fill. Search *away* from mid-tone in small steps so the fill stays recognisably
        // the accent colour instead of washing out to near-white on dark themes.
        var fillLight = if (dark) 56f else 46f
        var accent = hsl(t.accentHue.toFloat(), 88f, fillLight)
        var onAccent = if (contrast(Color.White, accent) >= contrast(ink, accent)) Color.White else ink
        guard = 0
        while (contrast(onAccent, accent) < 4.5f && guard < 40) {
            fillLight = (fillLight + if (dark) -2f else -2f).coerceIn(6f, 72f)
            accent = hsl(t.accentHue.toFloat(), 88f, fillLight)
            onAccent = if (contrast(Color.White, accent) >= contrast(ink, accent)) Color.White else ink
            guard++
        }

        val spectrumSat = t.rainbow * 0.92f
        val spectrumLight = if (dark) 60f else 52f
        val spectrum = spectrumHues.map { hsl(it, spectrumSat, spectrumLight) }
        val waveColors = List(WindowProgress.COLUMNS) { index ->
            hsl(188f + index * (200f / 31f), spectrumSat, spectrumLight)
        }

        val pads = listOf(12, 16, 20)[t.density.coerceIn(0, 2)]
        val rowPads = listOf(8, 12, 16)[t.density.coerceIn(0, 2)]
        val gaps = listOf(10, 14, 18)[t.density.coerceIn(0, 2)]

        return DesignPalette(
            bg = bg, tile = tile, backdrop = backdrop, rule = rule, rowRule = rowRule, off = off,
            ink = ink, text = text, muted = muted,
            accent = accent, onAccent = onAccent, accentText = accentText,
            danger = if (dark) hsl(343f, 82f, 66f) else hsl(343f, 68f, 47f),
            success = if (dark) hsl(160f, 62f, 55f) else hsl(160f, 84f, 34f),
            warn = if (dark) hsl(43f, 92f, 60f) else hsl(32f, 88f, 37f),
            spectrum = spectrum,
            waveColors = waveColors,
            rainbowBrush = Brush.linearGradient(spectrum),
            rainbowHard = Brush.linearGradient(*hardStops(spectrum)),
            rainAlpha = 0.2f + t.rainbow / 100f * 0.8f,
            glowAlpha = t.glow / 100f * (if (dark) 0.5f else 0.3f),
            grainAlpha = (t.grain / 100f) * (0.5f + t.sharp / 100f * 0.9f) * 0.16f,
            gridAlpha = if (t.grid) 0.05f + t.sharp / 100f * 0.09f else 0f,
            waveDurationMs = if (t.wave == 0) 0 else 14000 - t.wave * 110,
            gradientDurationMs = if (t.motion == 0) 0 else 16000 - t.motion * 90,
            slantDeg = t.slant.toFloat(),
            shape = RoundedCornerShape(t.radius.dp),
            tileShape = RoundedCornerShape(t.tileRadius.dp),
            hatch = t.hatch,
            peaks = t.peaks,
            motion = t.motion / 100f,
            pad = pads.dp, rowPad = rowPads.dp, gap = gaps.dp,
            italics = t.italics, dark = dark, sharp = t.sharp
        )
    }

    fun solid(color: Color): Brush = SolidColor(color)

    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L

    /**
     * Colour of a remaining-time label, matching the reference's urgency ramp:
     * overdue or under 3h = danger, under 24h or window past 75% = warn,
     * under 72h = amber, otherwise success.
     */
    fun urgency(deadline: Long, windowStart: Long, now: Long, p: DesignPalette): Color {
        val left = deadline - now
        if (left <= 0L) return p.danger
        if (left < 3 * HOUR_MS) return p.danger
        val length = (deadline - windowStart).coerceAtLeast(1L)
        if (left < DAY_MS || left.toFloat() / length.toFloat() < 0.25f) return p.warn
        if (left < 3 * DAY_MS) return if (p.dark) hsl(45f, 88f, 58f) else hsl(45f, 88f, 42f)
        return p.success
    }

    private fun hardStops(colors: List<Color>): Array<Pair<Float, Color>> {
        val stops = ArrayList<Pair<Float, Color>>(colors.size * 2)
        val step = 1f / colors.size
        colors.forEachIndexed { index, color ->
            stops.add(index * step to color)
            stops.add((index + 1) * step to color)
        }
        return stops.toTypedArray()
    }

    fun hsl(hue: Float, saturation: Float, lightness: Float): Color {
        val h = (((hue % 360f) + 360f) % 360f) / 360f
        val s = (saturation / 100f).coerceIn(0f, 1f)
        val l = (lightness / 100f).coerceIn(0f, 1f)
        if (s == 0f) return Color(l, l, l)
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        fun hueToChannel(t: Float): Float {
            var value = t
            if (value < 0f) value += 1f
            if (value > 1f) value -= 1f
            return when {
                value < 1f / 6f -> p + (q - p) * 6f * value
                value < 1f / 2f -> q
                value < 2f / 3f -> p + (q - p) * (2f / 3f - value) * 6f
                else -> p
            }
        }
        return Color(hueToChannel(h + 1f / 3f), hueToChannel(h), hueToChannel(h - 1f / 3f))
    }

    fun luminance(color: Color): Float {
        fun channel(value: Float): Float =
            if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
    }

    fun contrast(a: Color, b: Color): Float {
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05f) / (min(x, y) + 0.05f)
    }

    fun hex(color: Color): String = "#%02X%02X%02X".format(
        (color.red * 255).roundToInt(), (color.green * 255).roundToInt(), (color.blue * 255).roundToInt()
    )
}

class DesignController(context: Context) {

    private val prefs = context.getSharedPreferences("netoverdose_design", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<DesignState> = _state.asStateFlow()

    val tokens: DesignTokens get() = _state.value.tokens

    fun update(block: (DesignTokens) -> DesignTokens) {
        val next = block(_state.value.tokens)
        _state.value = DesignState(next, preset = "")
        save(next, "")
    }

    fun applyPreset(key: String) {
        val next = DesignPresets.byKey(key)
        _state.value = DesignState(next, key)
        save(next, key)
    }

    fun randomize() {
        val next = DesignPresets.random()
        _state.value = DesignState(next, preset = "")
        save(next, "")
    }

    fun exportText(): String {
        val t = _state.value.tokens
        val p = DesignEngine.derive(t)
        return buildString {
            appendLine("/* netoverdose design tokens */")
            appendLine("/* preset: ${_state.value.preset.ifBlank { "custom" }} */")
            appendLine(":root {")
            appendLine("  --ink: ${DesignEngine.hex(p.ink)};")
            appendLine("  --text: ${DesignEngine.hex(p.text)};")
            appendLine("  --muted: ${DesignEngine.hex(p.muted)};")
            appendLine("  --rule: ${DesignEngine.hex(p.rule)};")
            appendLine("  --row-rule: ${DesignEngine.hex(p.rowRule)};")
            appendLine("  --tile: ${DesignEngine.hex(p.tile)};")
            appendLine("  --bg: ${DesignEngine.hex(p.bg)};")
            appendLine("  --backdrop: ${DesignEngine.hex(p.backdrop)};")
            appendLine("  --off: ${DesignEngine.hex(p.off)};")
            appendLine("  --accent: ${DesignEngine.hex(p.accent)};")
            appendLine("  --on-accent: ${DesignEngine.hex(p.onAccent)};")
            appendLine("  --accent-text: ${DesignEngine.hex(p.accentText)};")
            appendLine("  --danger: ${DesignEngine.hex(p.danger)};")
            appendLine("  --success: ${DesignEngine.hex(p.success)};")
            appendLine("  --warn: ${DesignEngine.hex(p.warn)};")
            p.spectrum.forEachIndexed { index, color ->
                appendLine("  --spectrum-$index: ${DesignEngine.hex(color)};")
            }
            appendLine("  --rain-alpha: ${p.rainAlpha};")
            appendLine("  --glow-alpha: ${p.glowAlpha};")
            appendLine("  --grain-alpha: ${p.grainAlpha};")
            appendLine("  --grid-alpha: ${p.gridAlpha};")
            appendLine("  --wave-duration-ms: ${p.waveDurationMs};")
            appendLine("  --slant-deg: ${p.slantDeg};")
            appendLine("  --radius-dp: ${t.radius};")
            appendLine("  --tile-radius-dp: ${t.tileRadius};")
            appendLine("  --pad-dp: ${p.pad.value};")
            appendLine("}")
            appendLine(
                "/* raw: cold=${t.cold} hue=${t.accentHue} rainbow=${t.rainbow} sharp=${t.sharp} " +
                    "grain=${t.grain} grid=${t.grid} dark=${t.dark} italics=${t.italics} slant=${t.slant} " +
                    "radius=${t.radius} tile=${t.tileRadius} density=${t.density} wave=${t.wave} " +
                    "glow=${t.glow} hatch=${t.hatch} peaks=${t.peaks} motion=${t.motion} " +
                    "font=${t.fontFamily.ifBlank { "bundle" }} */"
            )
        }
    }

    private fun load(): DesignState {
        val d = DesignTokens()
        val tokens = DesignTokens(
            cold = prefs.getInt("cold", d.cold),
            accentHue = prefs.getInt("accentHue", d.accentHue),
            rainbow = prefs.getInt("rainbow", d.rainbow),
            sharp = prefs.getInt("sharp", d.sharp),
            grain = prefs.getInt("grain", d.grain),
            grid = prefs.getBoolean("grid", d.grid),
            dark = prefs.getBoolean("dark", d.dark),
            italics = prefs.getBoolean("italics", d.italics),
            slant = prefs.getInt("slant", d.slant),
            radius = prefs.getInt("radius", d.radius),
            density = prefs.getInt("density", d.density),
            wave = prefs.getInt("wave", d.wave),
            glow = prefs.getInt("glow", d.glow),
            tileRadius = prefs.getInt("tileRadius", d.tileRadius),
            hatch = prefs.getBoolean("hatch", d.hatch),
            peaks = prefs.getBoolean("peaks", d.peaks),
            motion = prefs.getInt("motion", d.motion),
            fontFamily = prefs.getString("fontFamily", d.fontFamily) ?: d.fontFamily
        )
        return DesignState(tokens, prefs.getString("preset", "ice") ?: "ice")
    }

    private fun save(t: DesignTokens, preset: String) {
        prefs.edit()
            .putInt("cold", t.cold).putInt("accentHue", t.accentHue).putInt("rainbow", t.rainbow)
            .putInt("sharp", t.sharp).putInt("grain", t.grain).putBoolean("grid", t.grid)
            .putBoolean("dark", t.dark).putBoolean("italics", t.italics).putInt("slant", t.slant)
            .putInt("radius", t.radius).putInt("density", t.density).putInt("wave", t.wave)
            .putInt("glow", t.glow).putString("preset", preset)
            .putInt("tileRadius", t.tileRadius).putBoolean("hatch", t.hatch)
            .putBoolean("peaks", t.peaks).putInt("motion", t.motion)
            .putString("fontFamily", t.fontFamily)
            .apply()
    }
}
