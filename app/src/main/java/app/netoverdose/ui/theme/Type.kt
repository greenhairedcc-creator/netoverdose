package app.netoverdose.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Fonts are loaded from res/font when the files are present, otherwise the platform
 * families are used. Lookup is by name so the project compiles before the TTFs are added.
 *
 * Expected files in app/src/main/res/font/:
 *   noto_serif_regular.ttf, noto_serif_bold.ttf, noto_serif_italic.ttf, noto_serif_bold_italic.ttf
 *   roboto_regular.ttf, roboto_medium.ttf, roboto_bold.ttf
 *   roboto_mono_regular.ttf, roboto_mono_medium.ttf, roboto_mono_bold.ttf
 */
var SerifFamily: FontFamily = FontFamily.Serif
    private set
var MonoFamily: FontFamily = FontFamily.Monospace
    private set
var SansFamily: FontFamily = FontFamily.Default
    private set

@SuppressLint("DiscouragedApi")
private fun fontId(context: Context, name: String): Int =
    context.resources.getIdentifier(name, "font", context.packageName)

private fun family(vararg entries: Triple<Int, FontWeight, FontStyle>): FontFamily? {
    val fonts = entries.filter { it.first != 0 }.map { Font(it.first, it.second, it.third) }
    return if (fonts.isEmpty()) null else FontFamily(fonts)
}

/** Called once from Application.onCreate, before any composition happens. */
fun installFonts(context: Context) {
    SerifFamily = serifFamily(context) ?: FontFamily.Serif
    SansFamily = sansFamily(context) ?: FontFamily.Default
    MonoFamily = monoFamily(context) ?: FontFamily.Monospace
}

private fun serifFamily(context: Context): FontFamily? = family(
    Triple(fontId(context, "noto_serif_regular"), FontWeight.Normal, FontStyle.Normal),
    Triple(fontId(context, "noto_serif_bold"), FontWeight.Bold, FontStyle.Normal),
    Triple(fontId(context, "noto_serif_italic"), FontWeight.Normal, FontStyle.Italic),
    Triple(fontId(context, "noto_serif_bold_italic"), FontWeight.Bold, FontStyle.Italic)
)

private fun sansFamily(context: Context): FontFamily? = family(
    Triple(fontId(context, "roboto_regular"), FontWeight.Normal, FontStyle.Normal),
    Triple(fontId(context, "roboto_medium"), FontWeight.Medium, FontStyle.Normal),
    Triple(fontId(context, "roboto_bold"), FontWeight.Bold, FontStyle.Normal)
)

private fun monoFamily(context: Context): FontFamily? = family(
    Triple(fontId(context, "roboto_mono_regular"), FontWeight.Normal, FontStyle.Normal),
    Triple(fontId(context, "roboto_mono_medium"), FontWeight.Medium, FontStyle.Normal),
    Triple(fontId(context, "roboto_mono_bold"), FontWeight.Bold, FontStyle.Normal)
)

// Inter ships only as a variable font, so the single file is registered once per weight.
private fun interFamily(context: Context): FontFamily? = family(
    Triple(fontId(context, "inter_variable"), FontWeight.Normal, FontStyle.Normal),
    Triple(fontId(context, "inter_variable"), FontWeight.Medium, FontStyle.Normal),
    Triple(fontId(context, "inter_variable"), FontWeight.Bold, FontStyle.Normal)
)

/** Alt sans: Roboto condensed-free stack is not bundled, so Roboto itself stands in. */
private fun altSansFamily(context: Context): FontFamily? = sansFamily(context)

/**
 * One selectable typeface set. `key` is persisted in the design tokens; a set overrides
 * only the faces it replaces, the rest fall back to the bundle.
 */
data class FamilyOption(
    val key: String,
    val labelRes: Int,
    val serif: FontFamily?,
    val sans: FontFamily?,
    val mono: FontFamily?
)

const val BUNDLE_FAMILY_KEY = "bundle"
const val INTER_FAMILY_KEY = "inter"
const val NOTO_FAMILY_KEY = "noto"
const val ROBOTO_FAMILY_KEY = "roboto"

/** Platform families; always offered so the user can fall back if the bundle is not liked. */
const val SYSTEM_FAMILY_KEY = "system"

/** Kept for callers written before the system option existed. */
const val DEFAULT_FAMILY_KEY = BUNDLE_FAMILY_KEY

/**
 * Every set the user can pick, ordered from most neutral to most decorative. A set is
 * listed only when its files were actually found, otherwise two rows would look identical.
 */
fun availableFamilies(context: Context): List<FamilyOption> {
    val bundle = FamilyOption(
        key = BUNDLE_FAMILY_KEY,
        labelRes = app.netoverdose.R.string.font_bundle,
        serif = serifFamily(context),
        sans = sansFamily(context),
        mono = monoFamily(context)
    )
    val inter = FamilyOption(
        key = INTER_FAMILY_KEY,
        labelRes = app.netoverdose.R.string.font_inter,
        serif = interFamily(context),
        sans = interFamily(context),
        mono = null
    )
    val noto = FamilyOption(
        key = NOTO_FAMILY_KEY,
        labelRes = app.netoverdose.R.string.font_noto,
        serif = serifFamily(context),
        sans = null,
        mono = null
    )
    val roboto = FamilyOption(
        key = ROBOTO_FAMILY_KEY,
        labelRes = app.netoverdose.R.string.font_roboto,
        serif = null,
        sans = altSansFamily(context),
        mono = null
    )
    val system = FamilyOption(
        key = SYSTEM_FAMILY_KEY,
        labelRes = app.netoverdose.R.string.font_system,
        serif = FontFamily.Serif,
        sans = FontFamily.SansSerif,
        mono = FontFamily.Monospace
    )
    return listOfNotNull(
        bundle.takeIf { it.serif != null || it.sans != null || it.mono != null },
        inter.takeIf { it.serif != null },
        noto.takeIf { it.serif != null },
        roboto.takeIf { it.sans != null },
        system
    )
}

/**
 * Applies the persisted choice. A set that overrides only one face keeps the bundle for
 * the others, so switching to Inter does not leave the mono labels on a system font.
 */
fun applyFamily(context: Context, key: String) {
    val all = availableFamilies(context)
    val bundle = all.firstOrNull { it.key == BUNDLE_FAMILY_KEY }
        ?: all.firstOrNull()
    val option = all.firstOrNull { it.key == key } ?: bundle
    SerifFamily = option?.serif ?: bundle?.serif ?: FontFamily.Serif
    SansFamily = option?.sans ?: bundle?.sans ?: FontFamily.SansSerif
    MonoFamily = option?.mono ?: bundle?.mono ?: FontFamily.Monospace
}

fun netoverdoseTypography(): Typography = Typography(
    headlineMedium = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 28.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.04.sp
    ),
    labelMedium = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.88.sp
    )
)
