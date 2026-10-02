package mx.edu.utez.gibbor.presentation.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.edu.utez.gibbor.presentation.ui.theme.Cyan
import mx.edu.utez.gibbor.presentation.ui.theme.Panic
import mx.edu.utez.gibbor.presentation.ui.theme.PanicDeep
import mx.edu.utez.gibbor.presentation.ui.theme.TextHi
import mx.edu.utez.gibbor.presentation.ui.theme.TextMid

@Composable
fun PanicButton(
    authenticated: Boolean,
    busy: Boolean,
    isRecording: Boolean,
    elapsedSeconds: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "panic pulse")
    val firstRing by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "first pulse",
    )
    val secondRing by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, delayMillis = 1200, easing = LinearEasing)),
        label = "second pulse",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "processing arc",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(pressed) {
        if (pressed && authenticated && !busy) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val enabled = authenticated && !busy
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "panic button press",
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .drawBehind {
                    val radius = size.minDimension * 0.34f
                    listOf(firstRing, secondRing).forEach { progress ->
                        drawCircle(
                            color = Panic.copy(alpha = 0.35f * (1f - progress)),
                            radius = radius * (0.79f + 0.48f * progress),
                            center = Offset(size.width / 2, size.height / 2),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.7.dp.toPx()),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                androidx.compose.foundation.Canvas(Modifier.size(224.dp)) {
                    drawArc(
                        color = Cyan,
                        startAngle = rotation - 90f,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .alpha(if (authenticated) 1f else 0.4f)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Panic, PanicDeep),
                        ),
                        shape = CircleShape,
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = onClick,
                    )
                    .semantics {
                        role = Role.Button
                        contentDescription = "Botón de pánico"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(25.dp),
                            color = TextHi,
                            strokeWidth = 2.dp,
                        )
                        Text("Procesando…", color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    } else if (isRecording) {
                        Text("REC", color = TextHi, fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                        Text(
                            formatElapsed(elapsedSeconds),
                            color = TextHi,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Text("GIBBOR", color = TextHi, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (authenticated) "PULSAR" else "Inicia sesión",
                            color = TextHi,
                            fontSize = if (authenticated) 24.sp else 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        Text(
            text = "Pulsa aquí o el botón físico GIBBOR",
            color = TextMid,
            fontSize = 12.sp,
        )
    }
}

@Composable
fun RecordingElapsedText(
    elapsedSeconds: Long,
    modifier: Modifier = Modifier,
    color: Color = TextHi,
) {
    Text(
        text = formatElapsed(elapsedSeconds),
        modifier = modifier,
        color = color,
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
    )
}

private fun formatElapsed(seconds: Long): String =
    "%02d:%02d".format(seconds / 60, seconds % 60)
