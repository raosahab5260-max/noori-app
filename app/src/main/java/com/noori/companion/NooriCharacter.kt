package com.noori.companion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class NooriExpression {
    IDLE, HAPPY, SPEAKING, LISTENING, THINKING, LAUGHING, SHY
}

enum class TouchZone {
    HEAD, ARM_SHOULDER, TICKLE_WAIST
}

@Composable
fun NooriLiveStage(
    expression: NooriExpression,
    activeDance: DanceCategory?,
    showBoyPartner: Boolean,
    onTouchZone: (TouchZone) -> Unit,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "noori_photo_rig")

    val breathe by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )

    val danceCycle by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(activeDance?.tempoMs ?: 650, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "dance"
    )

    val mouthAnim by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(150), RepeatMode.Reverse),
        label = "mouth"
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

    val transY = when (activeDance) {
        null -> -breathe * 8f
        DanceCategory.PUNJABI, DanceCategory.CUTE_ANIME -> kotlin.math.abs(danceCycle) * -26f
        DanceCategory.SLOW, DanceCategory.ROMANTIC_COUPLE -> danceCycle * 6f
        else -> kotlin.math.abs(danceCycle) * -16f
    }
    val rotZ = when (activeDance) {
        null -> if (expression == NooriExpression.LAUGHING) danceCycle * 3.5f else 0f
        DanceCategory.HIP_HOP, DanceCategory.K_POP -> danceCycle * 7f
        DanceCategory.GARBA, DanceCategory.CLASSICAL_INDIAN -> danceCycle * 9f
        else -> danceCycle * 4.5f
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        // Exact uploaded Noori photo (736x1318) with live blink, cute smile, lip-sync & touch
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(736f / 1318f)
                .graphicsLayer {
                    translationY = transY
                    rotationZ = rotZ
                    scaleY = 1f + breathe * 0.012f
                }
                .clip(RoundedCornerShape(22.dp))
                .border(1.5.dp, Color(0xFF9333EA), RoundedCornerShape(22.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val relX = offset.x / size.width
                        val relY = offset.y / size.height
                        when {
                            relY < 0.25f -> onTouchZone(TouchZone.HEAD)
                            relX < 0.30f || relX > 0.72f -> onTouchZone(TouchZone.ARM_SHOULDER)
                            else -> onTouchZone(TouchZone.TICKLE_WAIST)
                        }
                    }
                }
        ) {
            Image(
                painter = painterResource(id = R.drawable.noori_ref),
                contentDescription = "Noori",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Natural eye blinking aligned to her purple eyes
                if (isBlinking || expression == NooriExpression.LAUGHING) {
                    val lidBrush = Brush.verticalGradient(listOf(Color(0xFFFFECE6), Color(0xFFE8C4BC)))
                    drawOval(lidBrush, Offset(w * 0.415f, h * 0.128f), Size(w * 0.075f, h * 0.036f))
                    drawLine(Color(0xFF1D1726), Offset(w * 0.418f, h * 0.148f), Offset(w * 0.488f, h * 0.150f), 3f, StrokeCap.Round)

                    drawOval(lidBrush, Offset(w * 0.518f, h * 0.120f), Size(w * 0.075f, h * 0.036f))
                    drawLine(Color(0xFF1D1726), Offset(w * 0.520f, h * 0.142f), Offset(w * 0.590f, h * 0.140f), 3f, StrokeCap.Round)
                }

                // Cute smile & lip-sync mouth overlay
                when (expression) {
                    NooriExpression.SPEAKING -> {
                        val mh = h * 0.008f + mouthAnim * h * 0.012f
                        drawOval(Color(0xFF8F2D46), Offset(w * 0.498f, h * 0.183f - mh / 2f), Size(w * 0.034f, mh))
                    }
                    NooriExpression.HAPPY, NooriExpression.LAUGHING -> {
                        drawArc(
                            color = Color(0xFFA83E58),
                            startAngle = 15f,
                            sweepAngle = 150f,
                            useCenter = false,
                            topLeft = Offset(w * 0.493f, h * 0.178f),
                            size = Size(w * 0.042f, h * 0.012f),
                            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                        )
                    }
                    else -> {}
                }
            }
        }

        // Anime Boy Partner (Appears only in Couple Dance Mode)
        if (showBoyPartner) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.width(125.dp).height(260.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val bounceY = kotlin.math.abs(danceCycle) * -9f

                    rotate(degrees = -danceCycle * 5f, pivot = Offset(cx, h * 0.8f)) {
                        drawRoundRect(Color(0xFF1F2937), Offset(cx - 18f, h * 0.65f + bounceY), Size(13f, h * 0.25f), CornerRadius(5f))
                        drawRoundRect(Color(0xFF1F2937), Offset(cx + 5f, h * 0.65f + bounceY), Size(13f, h * 0.25f), CornerRadius(5f))
                        drawOval(Color(0xFF0D0D14), Offset(cx - 21f, h * 0.89f + bounceY), Size(20f, 11f))
                        drawOval(Color(0xFF0D0D14), Offset(cx + 2f, h * 0.89f + bounceY), Size(20f, 11f))
                        drawRoundRect(Color(0xFF312E81), Offset(cx - 28f, h * 0.36f + bounceY), Size(56f, h * 0.30f), CornerRadius(14f))
                        drawRoundRect(Color(0xFF312E81), Offset(cx - 42f, h * 0.38f + bounceY), Size(14f, h * 0.20f), CornerRadius(7f))
                        drawRoundRect(Color(0xFF312E81), Offset(cx + 28f, h * 0.38f + bounceY), Size(14f, h * 0.20f), CornerRadius(7f))
                        drawRoundRect(Color(0xFFFDE2D2), Offset(cx - 26f, h * 0.12f + bounceY), Size(52f, 58f), CornerRadius(24f))
                        val hair = Path().apply {
                            moveTo(cx - 28f, h * 0.18f + bounceY)
                            quadraticBezierTo(cx, h * 0.05f + bounceY, cx + 28f, h * 0.18f + bounceY)
                            quadraticBezierTo(cx + 26f, h * 0.06f + bounceY, cx, h * 0.06f + bounceY)
                            quadraticBezierTo(cx - 26f, h * 0.06f + bounceY, cx - 28f, h * 0.18f + bounceY)
                            close()
                        }
                        drawPath(hair, Color(0xFF1E1B4B))
                        drawCircle(Color(0xFF1E1B4B), 4.5f, Offset(cx - 12f, h * 0.22f + bounceY))
                        drawCircle(Color(0xFF1E1B4B), 4.5f, Offset(cx + 12f, h * 0.22f + bounceY))
                        drawArc(Color(0xFF9F1239), 15f, 150f, false, Offset(cx - 7f, h * 0.27f + bounceY), Size(14f, 7f), style = Stroke(2.4f, cap = StrokeCap.Round))
                    }
                }
            }
        }
    }
}
