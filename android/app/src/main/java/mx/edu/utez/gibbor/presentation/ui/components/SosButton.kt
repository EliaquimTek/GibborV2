package mx.edu.utez.gibbor.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.edu.utez.gibbor.presentation.ui.theme.Motion
import mx.edu.utez.gibbor.presentation.ui.theme.rememberReduceMotion
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Colores tomados del botón del logo (alebrije): domo magenta sobre base índigo.
private val CapTopLight = Color(0xFFFF6A98)
private val CapTop = Color(0xFFFF2D6F)
private val CapSide = Color(0xFFE0185A)
private val CapDeep = Color(0xFF9E0B3E)
private val BaseTop = Color(0xFF2B2F8A)
private val BaseSide = Color(0xFF1A1D5E)
private val BaseRim = Color(0xFF4A52D1)
private val BaseWell = Color(0xFF13164A)
private val Burst = Color(0xFFFF7A1A)
private val MotionArc = Color(0xFF2EC4E6)

/**
 * Botón SOS 3D inspirado en el botón del logo de GIBBOR. Mantener presionado 1.2 s: el domo se hunde,
 * un anillo naranja recorre la base y aparecen los destellos; al completarse llama a [onConfirmed].
 * En `busy` el domo queda hundido y un arco gira sobre la base.
 */
@Composable
fun SosButton(
    busy: Boolean,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 260.dp,
    holdMillis: Int = 1200,
) {
    val reduceMotion = rememberReduceMotion()
    val progress = remember { Animatable(0f) }
    val currentOnConfirmed by rememberUpdatedState(onConfirmed)
    val busyDepth by animateFloatAsState(
        targetValue = if (busy) 1f else 0f,
        animationSpec = tween(Motion.COMPONENT, easing = Motion.Emphasized),
        label = "sos busy depth",
    )

    val transition = rememberInfiniteTransition(label = "sos ambient")
    val ambient = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "sos ambient progress",
    )

    Box(
        modifier = modifier
            .width(width)
            .height(width * 0.72f)
            .holdToConfirm(
                enabled = !busy,
                durationMillis = holdMillis,
                progress = progress,
                onConfirmed = onConfirmed,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Botón de emergencia SOS. Mantén presionado para pedir ayuda"
                stateDescription = if (busy) "Enviando alerta" else "Listo"
                if (!busy) {
                    onClick(label = "Pedir ayuda") {
                        currentOnConfirmed()
                        true
                    }
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val p = progress.value
            // El domo baja rápido al tocar (sqrt) y termina hundido al completar o mientras se envía.
            val depth = maxOf(sqrt(p), busyDepth)
            val t = if (reduceMotion) 0f else ambient.value
            drawPanicButton(progress = p, depth = depth, busy = busy, ambient = t, animate = !reduceMotion)
        }
    }
}

private fun DrawScope.drawPanicButton(
    progress: Float,
    depth: Float,
    busy: Boolean,
    ambient: Float,
    animate: Boolean,
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val k = 0.38f // aplanado de perspectiva

    // ─── Geometría ────────────────────────────────────────────────────────
    val rxB = w * 0.46f
    val ryB = rxB * k
    val tb = w * 0.045f
    val baseTopY = h - tb - ryB - w * 0.03f
    val rxC = w * 0.33f
    val ryC = rxC * k
    val capBottomY = baseTopY - ryB * 0.05f
    val maxH = w * 0.2f
    val minH = w * 0.05f
    val hc = maxH - (maxH - minH) * depth
    val capTopY = capBottomY - hc

    fun oval(cy: Float, rx: Float, ry: Float) = Rect(cx - rx, cy - ry, cx + rx, cy + ry)

    // ─── Sombra ───────────────────────────────────────────────────────────
    val shadow = oval(baseTopY + tb + ryB * 0.25f, rxB * 1.08f, ryB * 1.15f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent),
            center = shadow.center,
            radius = shadow.width / 2f,
        ),
        topLeft = shadow.topLeft,
        size = shadow.size,
    )

    // ─── Arcos de movimiento (como en el logo) ───────────────────────────
    val arcsAlpha = if (animate) 0.35f + 0.45f * (1f - ambient) else 0.6f
    val arcOval = oval(baseTopY + tb * 0.5f, rxB * 1.14f, ryB * 1.7f)
    val arcStroke = Stroke(width = w * 0.012f, cap = StrokeCap.Round)
    drawArc(MotionArc.copy(alpha = arcsAlpha), 150f, 50f, false, arcOval.topLeft, arcOval.size, style = arcStroke)
    drawArc(MotionArc.copy(alpha = arcsAlpha), -20f, 50f, false, arcOval.topLeft, arcOval.size, style = arcStroke)

    // ─── Base índigo ──────────────────────────────────────────────────────
    val baseBottom = oval(baseTopY + tb, rxB, ryB)
    drawOval(BaseSide, baseBottom.topLeft, baseBottom.size)
    drawRect(BaseSide, Offset(cx - rxB, baseTopY), Size(rxB * 2f, tb))
    val baseTop = oval(baseTopY, rxB, ryB)
    drawOval(
        brush = Brush.verticalGradient(listOf(BaseRim, BaseTop), startY = baseTop.top, endY = baseTop.bottom),
        topLeft = baseTop.topLeft,
        size = baseTop.size,
    )
    val well = oval(capBottomY, rxC * 1.1f, ryC * 1.1f)
    drawOval(BaseWell, well.topLeft, well.size)

    // Anillo de progreso (hold) o arco giratorio (enviando) sobre el borde de la base.
    val ring = oval(baseTopY, rxB * 0.9f, ryB * 0.86f)
    val ringStroke = Stroke(width = w * 0.016f, cap = StrokeCap.Round)
    if (busy) {
        val start = if (animate) 90f + 360f * ambient * 2f else 90f
        drawArc(Burst, start, 90f, false, ring.topLeft, ring.size, style = ringStroke)
    } else if (progress > 0f) {
        drawArc(Burst, 90f, 360f * progress, false, ring.topLeft, ring.size, style = ringStroke)
    }

    // ─── Domo magenta (lateral) ───────────────────────────────────────────
    val side = Path().apply {
        moveTo(cx - rxC, capBottomY)
        lineTo(cx - rxC, capTopY)
        arcTo(oval(capTopY, rxC, ryC), 180f, 180f, false)
        lineTo(cx + rxC, capBottomY)
        arcTo(oval(capBottomY, rxC, ryC), 0f, 180f, false)
        close()
    }
    drawPath(
        path = side,
        brush = Brush.horizontalGradient(
            0f to CapDeep,
            0.22f to CapSide,
            0.42f to CapTop,
            0.75f to CapSide,
            1f to CapDeep,
            startX = cx - rxC,
            endX = cx + rxC,
        ),
    )

    // ─── Domo (cara superior) con brillo ──────────────────────────────────
    val top = oval(capTopY, rxC, ryC)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(CapTopLight, CapTop, CapSide),
            center = Offset(cx - rxC * 0.25f, capTopY - ryC * 0.3f),
            radius = rxC * 1.2f,
        ),
        topLeft = top.topLeft,
        size = top.size,
    )
    val gloss = Rect(
        offset = Offset(cx - rxC * 0.62f, capTopY - ryC * 0.62f),
        size = Size(rxC * 0.5f, ryC * 0.42f),
    )
    drawOval(Color.White.copy(alpha = 0.55f), gloss.topLeft, gloss.size)
    drawArc(
        color = Color.White.copy(alpha = 0.35f),
        startAngle = 200f,
        sweepAngle = 70f,
        useCenter = false,
        topLeft = Offset(top.left + rxC * 0.06f, top.top + ryC * 0.12f),
        size = Size(top.width - rxC * 0.12f, top.height - ryC * 0.24f),
        style = Stroke(width = w * 0.01f, cap = StrokeCap.Round),
    )

    // ─── "!" blanco en el frente del domo ─────────────────────────────────
    val frontTop = capTopY - ryC * 0.15f
    val frontBottom = capBottomY + ryC * 0.8f
    val span = frontBottom - frontTop
    val markH = span * 0.7f
    val markW = w * 0.042f
    val markTop = frontTop + (span - markH) / 2f
    val dotR = markW * 0.62f
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(cx - markW / 2f, markTop),
        size = Size(markW, markH - dotR * 3.2f),
        cornerRadius = CornerRadius(markW / 2f),
    )
    drawCircle(Color.White, radius = dotR, center = Offset(cx, markTop + markH - dotR))

    // ─── Destellos naranjas (crecen con el hold; fijos al enviar) ─────────
    val burst = if (busy) 1f else progress
    if (burst > 0f) {
        val origin = Offset(cx, capTopY + ryC * 0.2f)
        val inner = rxC * 1.12f
        val stroke = w * 0.024f
        for (angle in listOf(-10f, -32f, -55f, 190f, 212f, 235f)) {
            val rad = Math.toRadians(angle.toDouble())
            val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat() * 0.6f)
            val len = w * 0.09f * burst
            drawLine(
                color = Burst.copy(alpha = 0.4f + 0.6f * burst),
                start = origin + dir * inner,
                end = origin + dir * (inner + len),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
