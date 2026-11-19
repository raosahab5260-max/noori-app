package com.noori.companion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay

enum class NooriState {
    IDLE, LISTENING, THINKING, SPEAKING
}

@Composable
fun NooriHeroScene(
    state: NooriState,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "noori_hero")

    val mouthAnim by infinite.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mouth_sync"
    )

    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3100)
            isBlinking = true
            delay(130)
            isBlinking = false
        }
    }

    Box(modifier = modifier) {
        Image(
            painter = painterResource(id = R.drawable.noori_hero),
            contentDescription = "Noori Character",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Subtle eye-blink & speaking lip-sync overlay aligned to her face
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            if (isBlinking) {
                val eyelidBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFD39C97), Color(0xFFB87B78))
                )
                // Left eye
                drawOval(
                    brush = eyelidBrush,
                    topLeft = Offset(w * 0.448f, h * 0.325f),
                    size = Size(w * 0.044f, h * 0.055f)
                )
                drawLine(
                    color = Color(0xFF2C141D),
                    start = Offset(w * 0.450f, h * 0.358f),
                    end = Offset(w * 0.490f, h * 0.362f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                // Right eye
                drawOval(
                    brush = eyelidBrush,
                    topLeft = Offset(w * 0.518f, h * 0.332f),
                    size = Size(w * 0.044f, h * 0.055f)
                )
                drawLine(
                    color = Color(0xFF2C141D),
                    start = Offset(w * 0.520f, h * 0.365f),
                    end = Offset(w * 0.560f, h * 0.368f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }

            if (state == NooriState.SPEAKING) {
                val mh = (h * 0.012f) + (mouthAnim * h * 0.018f)
                drawOval(
                    color = Color(0xFF5A1828),
                    topLeft = Offset(w * 0.491f, h * 0.448f - mh / 2f),
                    size = Size(w * 0.024f, mh)
                )
            }
        }
    }
}
