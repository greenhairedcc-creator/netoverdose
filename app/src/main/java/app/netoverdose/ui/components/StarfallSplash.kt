package app.netoverdose.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.netoverdose.design.LocalDesign
import app.netoverdose.ui.theme.MonoFamily
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

/**
 * ASCII meteor shower shown while the app boots: columns of falling glyphs over the cold
 * background with the brand fading in. Decorative only, no data access.
 *
 * @param onFinished called once the animation has played out so the caller can reveal the app
 */
@Composable
fun StarfallSplash(onFinished: () -> Unit, durationMs: Long = 2600L) {
    val palette = LocalDesign.current
    // Animated spectrum, same loop as the rest of the UI; kept out of the draw calls.
    val splashBrush = animatedSpectrumBrush()
    val glyphs = remember { "·*+✳✶✷·+*··".toList() }
    val trails = remember {
        val random = Random(7)
        List(28) { index ->
            Trail(
                speed = 0.35f + random.nextFloat() * 0.8f,
                offset = random.nextFloat(),
                length = 4 + random.nextInt(5),
                phase = random.nextFloat() * 6.28f
            ).let { it to index }
        }
    }
    var frame by remember { mutableStateOf(0L) }
    var running by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (running) {
            frame = System.currentTimeMillis() - start
            if (frame > durationMs) {
                running = false
                onFinished()
            }
            delay(33)
        }
    }

    val brandAlpha by animateFloatAsState(
        targetValue = if (frame > durationMs * 0.4f) 1f else 0f,
        animationSpec = tween(500),
        label = "brandAlpha"
    )

    Box(Modifier.fillMaxSize().background(palette.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val columnWidth = size.width / 28f
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = columnWidth * 0.78f
                typeface = android.graphics.Typeface.MONOSPACE
            }
            trails.forEach { (trail, column) ->
                repeat(trail.length) { step ->
                    // Wraps so meteors keep falling for the whole splash.
                    val progress = ((frame / 1000f) * trail.speed + trail.offset + step * 0.05f) % 1f
                    val y = progress * size.height
                    val sway = sin(progress * 6.28f + trail.phase) * columnWidth * 0.4f
                    val fade = (1f - step.toFloat() / trail.length).coerceIn(0.12f, 1f)
                    val glyph = glyphs[(column + step) % glyphs.size]
                    val color = when (step % 3) {
                        0 -> palette.spectrum[column % palette.spectrum.size]
                        1 -> palette.accentText
                        else -> palette.muted
                    }
                    paint.color = color.copy(alpha = fade * 0.85f).toArgb()
                    drawContext.canvas.nativeCanvas.drawText(
                        glyph.toString(),
                        column * columnWidth + sway,
                        y,
                        paint
                    )
                }
            }
        }

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "net",
                style = TextStyle(
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    letterSpacing = 8.sp
                ),
                color = palette.ink.copy(alpha = brandAlpha)
            )
            Text(
                "overdose",
                style = TextStyle(
                    brush = splashBrush,
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    letterSpacing = 8.sp
                )
            )
            Spacer(Modifier.height(22.dp))
            // Progress fills exactly by the end of the splash.
            val progress = (frame.toFloat() / durationMs).coerceIn(0f, 1f)
            Box(
                Modifier.width(180.dp).height(2.dp)
                    .background(palette.off.copy(alpha = 0.35f))
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(progress)
                        .background(splashBrush, alpha = brandAlpha.coerceAtLeast(0.35f))
                )
            }
        }
    }
}

private data class Trail(
    val speed: Float,
    val offset: Float,
    val length: Int,
    val phase: Float
)
