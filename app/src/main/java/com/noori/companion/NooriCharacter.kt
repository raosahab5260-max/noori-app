package com.noori.companion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
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
    val infinite = rememberInfiniteTransition(label = "noori_rig")

    val breathe by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )

    val hairSway by infinite.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hair"
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
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(150), RepeatMode.Reverse),
        label = "mouth"
    )

    var isBlinking by remember { mutableStateOf(false) }
    var lookOffsetX by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3200)
            isBlinking = true
            delay(130)
            isBlinking = false
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(190.dp)
                .height(290.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val relX = offset.x / size.width
                        val relY = offset.y / size.height
                        lookOffsetX = ((relX - 0.5f) * 6f).coerceIn(-3f, 3f)
                        when {
                            relY < 0.36f -> onTouchZone(TouchZone.HEAD)
                            relX < 0.28f || relX > 0.72f -> onTouchZone(TouchZone.ARM_SHOULDER)
                            else -> onTouchZone(TouchZone.TICKLE_WAIST)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f

                val bounceY = when (activeDance) {
                    null -> -breathe * 5f
                    DanceCategory.PUNJABI, DanceCategory.CUTE_ANIME -> kotlin.math.abs(danceCycle) * -16f
                    DanceCategory.SLOW, DanceCategory.ROMANTIC_COUPLE -> danceCycle * 4f
                    else -> kotlin.math.abs(danceCycle) * -10f
                }
                val bodyTilt = when (activeDance) {
                    null -> if (expression == NooriExpression.LAUGHING) danceCycle * 4f else 0f
                    DanceCategory.HIP_HOP, DanceCategory.K_POP -> danceCycle * 8f
                    DanceCategory.GARBA, DanceCategory.CLASSICAL_INDIAN -> danceCycle * 10f
                    else -> danceCycle * 5f
                }
                val armLift = when (activeDance) {
                    null -> breathe * 4f
                    DanceCategory.PUNJABI -> 35f + danceCycle * 22f
                    DanceCategory.GARBA -> 28f + danceCycle * 18f
                    DanceCategory.CLASSICAL_INDIAN -> 25f + danceCycle * 15f
                    DanceCategory.HIP_HOP -> 15f + danceCycle * 25f
                    else -> 12f + danceCycle * 16f
                }

                rotate(degrees = bodyTilt, pivot = Offset(cx, h * 0.8f)) {
                    rotate(degrees = hairSway, pivot = Offset(cx, h * 0.18f)) {
                        val backHair = Path().apply {
                            moveTo(cx - w * 0.24f, h * 0.16f)
                            lineTo(cx - w * 0.31f, h * 0.62f + bounceY)
                            quadraticBezierTo(cx, h * 0.66f + bounceY, cx + w * 0.31f, h * 0.62f + bounceY)
                            lineTo(cx + w * 0.24f, h * 0.16f)
                            close()
                        }
                        drawPath(backHair, color = Color(0xFF12101A))
                    }

                    val legOffset = if (activeDance != null) danceCycle * 5f else 0f
                    drawRoundRect(Color(0xFFFDE2D2), Offset(cx - 22f, h * 0.70f + bounceY), Size(14f, h * 0.14f), CornerRadius(6f))
                    drawRoundRect(Color(0xFFFDE2D2), Offset(cx + 8f, h * 0.70f + bounceY), Size(14f, h * 0.14f), CornerRadius(6f))
                    drawRoundRect(Color(0xFF181822), Offset(cx - 22f, h * 0.80f + bounceY + legOffset), Size(14f, h * 0.10f), CornerRadius(4f))
                    drawRoundRect(Color(0xFF181822), Offset(cx + 8f, h * 0.80f + bounceY - legOffset), Size(14f, h * 0.10f), CornerRadius(4f))
                    drawOval(Color(0xFF0D0D14), Offset(cx - 26f, h * 0.89f + bounceY + legOffset), Size(22f, 12f))
                    drawOval(Color(0xFF0D0D14), Offset(cx + 4f, h * 0.89f + bounceY - legOffset), Size(22f, 12f))

                    val skirt = Path().apply {
                        moveTo(cx - w * 0.18f, h * 0.60f + bounceY)
                        lineTo(cx + w * 0.18f, h * 0.60f + bounceY)
                        lineTo(cx + w * 0.25f, h * 0.73f + bounceY)
                        lineTo(cx - w * 0.25f, h * 0.73f + bounceY)
                        close()
                    }
                    drawPath(skirt, color = Color(0xFF14141F))
                    drawLine(Color(0xFF2D2D44), Offset(cx - 14f, h * 0.61f + bounceY), Offset(cx - 18f, h * 0.72f + bounceY), 2f)
                    drawLine(Color(0xFF2D2D44), Offset(cx, h * 0.61f + bounceY), Offset(cx, h * 0.72f + bounceY), 2f)
                    drawLine(Color(0xFF2D2D44), Offset(cx + 14f, h * 0.61f + bounceY), Offset(cx + 18f, h * 0.72f + bounceY), 2f)

                    rotate(degrees = -armLift, pivot = Offset(cx - w * 0.19f, h * 0.40f + bounceY)) {
                        drawRoundRect(Color(0xFF181824), Offset(cx - w * 0.28f, h * 0.39f + bounceY), Size(20f, h * 0.20f), CornerRadius(10f))
                        drawCircle(Color(0xFFFDE2D2), 7f, Offset(cx - w * 0.23f, h * 0.60f + bounceY))
                    }
                    rotate(degrees = armLift, pivot = Offset(cx + w * 0.19f, h * 0.40f + bounceY)) {
                        drawRoundRect(Color(0xFF181824), Offset(cx + w * 0.28f - 20f, h * 0.39f + bounceY), Size(20f, h * 0.20f), CornerRadius(10f))
                        drawCircle(Color(0xFFFDE2D2), 7f, Offset(cx + w * 0.23f, h * 0.60f + bounceY))
                    }

                    val hoodie = Path().apply {
                        moveTo(cx - w * 0.20f, h * 0.37f + bounceY)
                        quadraticBezierTo(cx, h * 0.33f + bounceY, cx + w * 0.20f, h * 0.37f + bounceY)
                        lineTo(cx + w * 0.24f, h * 0.62f + bounceY)
                        quadraticBezierTo(cx, h * 0.64f + bounceY, cx - w * 0.24f, h * 0.62f + bounceY)
                        close()
                    }
                    drawPath(hoodie, color = Color(0xFF181824))
                    drawRoundRect(Color(0xFF101018), Offset(cx - 22f, h * 0.50f + bounceY), Size(44f, 20f), CornerRadius(8f))
                    drawLine(Color(0xFFA855F7), Offset(cx - 8f, h * 0.38f + bounceY), Offset(cx - 8f, h * 0.45f + bounceY), 2.5f, StrokeCap.Round)
                    drawLine(Color(0xFFA855F7), Offset(cx + 8f, h * 0.38f + bounceY), Offset(cx + 8f, h * 0.45f + bounceY), 2.5f, StrokeCap.Round)

                    drawRoundRect(Color(0xFFFAD7C4), Offset(cx - 8f, h * 0.32f + bounceY), Size(16f, 16f), CornerRadius(6f))
                    drawRoundRect(Color(0xFFFDE2D2), Offset(cx - 32f, h * 0.12f + bounceY), Size(64f, 66f), CornerRadius(30f))

                    drawOval(Color(0xFFF472B6).copy(alpha = 0.35f), Offset(cx - 24f, h * 0.27f + bounceY), Size(12f, 6f))
                    drawOval(Color(0xFFF472B6).copy(alpha = 0.35f), Offset(cx + 12f, h * 0.27f + bounceY), Size(12f, 6f))

                    val eyeY = h * 0.23f + bounceY
                    if (isBlinking || expression == NooriExpression.LAUGHING) {
                        drawArc(Color(0xFF12101A), 190f, 160f, false, Offset(cx - 22f, eyeY - 4f), Size(14f, 8f), style = Stroke(3f, cap = StrokeCap.Round))
                        drawArc(Color(0xFF12101A), 190f, 160f, false, Offset(cx + 8f, eyeY - 4f), Size(14f, 8f), style = Stroke(3f, cap = StrokeCap.Round))
                    } else {
                        listOf(cx - 15f, cx + 15f).forEach { ex ->
                            drawOval(Color(0xFF3B0764), Offset(ex - 7f, eyeY - 9f), Size(14f, 18f))
                            drawOval(Color(0xFF9333EA), Offset(ex - 4.5f + lookOffsetX, eyeY - 5f), Size(9f, 11f))
                            drawCircle(Color.White, 2.4f, Offset(ex - 2f + lookOffsetX, eyeY - 4f))
                        }
                    }

                    val bangs = Path().apply {
                        moveTo(cx - 35f, h * 0.19f + bounceY)
                        quadraticBezierTo(cx - 14f, h * 0.08f + bounceY, cx, h * 0.16f + bounceY)
                        quadraticBezierTo(cx + 14f, h * 0.08f + bounceY, cx + 35f, h * 0.19f + bounceY)
                        quadraticBezierTo(cx + 34f, h * 0.06f + bounceY, cx, h * 0.06f + bounceY)
                        quadraticBezierTo(cx - 34f, h * 0.06f + bounceY, cx - 35f, h * 0.19f + bounceY)
                        close()
                    }
                    drawPath(bangs, color = Color(0xFF12101A))

                    val mouthY = h * 0.30f + bounceY
                    when (expression) {
                        NooriExpression.SPEAKING -> {
                            val mh = 3f + mouthAnim * 8f
                            drawOval(Color(0xFFBE185D), Offset(cx - 6f, mouthY - mh / 2f), Size(12f, mh))
                        }
                        NooriExpression.LAUGHING -> {
                            drawOval(Color(0xFFBE185D), Offset(cx - 7f, mouthY - 4f), Size(14f, 10f))
                        }
                        else -> {
                            drawArc(Color(0xFFBE185D), 15f, 150f, false, Offset(cx - 8f, mouthY - 4f), Size(16f, 8f), style = Stroke(2.6f, cap = StrokeCap.Round))
                        }
                    }
                }
            }
        }

        if (showBoyPartner) {
            Box(modifier = Modifier.width(145.dp).height(280.dp)) {
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
