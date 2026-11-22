package app.netoverdose.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.ExperimentalComposeUiApi
import android.view.HapticFeedbackConstants
import app.netoverdose.design.LocalDesign

/**
 * Subtle press feedback used by every tappable surface: the element shrinks a little and
 * fires a light haptic tap. Scaling is disabled when the design token `motion` is 0, so the
 * whole interface can be made static from the Lab.
 *
 * @param enabled set false for non-interactive callers sharing the modifier
 */
fun Modifier.pressScale(enabled: Boolean = true): Modifier = composed {
    val motion = LocalDesign.current.motion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && motion > 0f) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Fires a light haptic tick on press. Compose has no built-in haptics API, so this delegates
 * to the platform view, which is the standard approach on API 26+.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberHaptics(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            runCatching {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }
}
