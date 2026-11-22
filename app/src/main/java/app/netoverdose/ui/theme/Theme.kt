package app.netoverdose.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.netoverdose.design.DesignController
import app.netoverdose.design.DesignEngine
import app.netoverdose.design.DesignPalette
import app.netoverdose.design.LocalDesign

private val SquareShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

@Composable
fun NetoverdoseTheme(controller: DesignController, content: @Composable () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val palette: DesignPalette = remember(state.tokens) { DesignEngine.derive(state.tokens) }
    val context = LocalContext.current
    // The families are process-wide mutable globals, so they must be applied before the
    // typography is built and the styles rebuilt whenever the choice changes.
    val typography = remember(state.tokens.fontFamily) {
        applyFamily(context, state.tokens.fontFamily)
        netoverdoseTypography()
    }

    CompositionLocalProvider(LocalDesign provides palette) {
        val scheme = if (palette.dark) {
            darkColorScheme(
                primary = palette.accent, onPrimary = palette.onAccent,
                background = palette.bg, onBackground = palette.text,
                surface = palette.bg, onSurface = palette.text,
                surfaceVariant = palette.tile, onSurfaceVariant = palette.muted,
                outline = palette.rule, outlineVariant = palette.rowRule,
                error = palette.danger, onError = palette.onAccent,
                scrim = Color.Black
            )
        } else {
            lightColorScheme(
                primary = palette.accent, onPrimary = palette.onAccent,
                background = palette.bg, onBackground = palette.text,
                surface = palette.bg, onSurface = palette.text,
                surfaceVariant = palette.tile, onSurfaceVariant = palette.muted,
                outline = palette.rule, outlineVariant = palette.rowRule,
                error = palette.danger, onError = palette.onAccent,
                scrim = Color.Black
            )
        }
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = SquareShapes) {
            content()
        }
    }
}
