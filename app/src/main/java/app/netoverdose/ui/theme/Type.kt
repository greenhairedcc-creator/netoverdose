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
    SerifFamily = family(
        Triple(fontId(context, "noto_serif_regular"), FontWeight.Normal, FontStyle.Normal),
        Triple(fontId(context, "noto_serif_bold"), FontWeight.Bold, FontStyle.Normal),
        Triple(fontId(context, "noto_serif_italic"), FontWeight.Normal, FontStyle.Italic),
        Triple(fontId(context, "noto_serif_bold_italic"), FontWeight.Bold, FontStyle.Italic)
    ) ?: FontFamily.Serif

    SansFamily = family(
        Triple(fontId(context, "roboto_regular"), FontWeight.Normal, FontStyle.Normal),
        Triple(fontId(context, "roboto_medium"), FontWeight.Medium, FontStyle.Normal),
        Triple(fontId(context, "roboto_bold"), FontWeight.Bold, FontStyle.Normal)
    ) ?: FontFamily.Default

    MonoFamily = family(
        Triple(fontId(context, "roboto_mono_regular"), FontWeight.Normal, FontStyle.Normal),
        Triple(fontId(context, "roboto_mono_medium"), FontWeight.Medium, FontStyle.Normal),
        Triple(fontId(context, "roboto_mono_bold"), FontWeight.Bold, FontStyle.Normal)
    ) ?: FontFamily.Monospace
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
