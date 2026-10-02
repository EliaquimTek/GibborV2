package mx.edu.utez.gibbor.presentation.ui.screen.demo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.edu.utez.gibbor.presentation.MainScreenController
import mx.edu.utez.gibbor.presentation.ui.components.GibborLogo
import mx.edu.utez.gibbor.presentation.ui.components.holdToConfirm
import mx.edu.utez.gibbor.presentation.ui.theme.GibborTheme
import mx.edu.utez.gibbor.presentation.ui.theme.MonoStyle
import mx.edu.utez.gibbor.presentation.ui.theme.Motion
import mx.edu.utez.gibbor.presentation.ui.theme.rememberReduceMotion

private enum class StepState { Pending, Active, Done, Failed }

private data class DemoStep(val label: String, val state: StepState, val error: String = "")

/**
 * Pestaña DEMO: misma lógica real que el botón GIBBOR (REMOTE/DEMO, cámara frontal).
 * Los pasos se derivan del estado real (incidentPreview, onChainStatus, isRecording y el registro).
 */
@Composable
fun DemoScreen(
    controller: MainScreenController,
    busy: Boolean,
    onChainStatus: String,
    incidentPreview: String,
    demoExecutionId: Int,
    demoStartIncidentPreview: String,
    demoStartChainStatus: String,
    demoStartLogLength: Int,
    dismissedDemoExecutionId: Int,
    onResetDemo: () -> Unit,
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var recordingStartedExecutionId by remember { mutableIntStateOf(0) }
    var lastVisualExecutionId by remember { mutableIntStateOf(demoExecutionId) }
    var flashing by remember { mutableStateOf(false) }
    val wave = remember { Animatable(1f) }
    val haptic = LocalHapticFeedback.current
    val sos = GibborTheme.colors.sos
    val flashAlpha by animateFloatAsState(
        targetValue = if (flashing) 0.12f else 0f,
        animationSpec = tween(Motion.MICRO),
        label = "demo alert flash",
    )

    LaunchedEffect(demoExecutionId) {
        if (demoExecutionId != lastVisualExecutionId) {
            lastVisualExecutionId = demoExecutionId
            if (demoExecutionId <= 0) return@LaunchedEffect
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            flashing = true
            wave.snapTo(0f)
            launch {
                wave.animateTo(1f, animationSpec = tween(900, easing = LinearEasing))
            }
            delay(150)
            flashing = false
        }
    }

    LaunchedEffect(demoExecutionId, controller.isRecording) {
        if (demoExecutionId > 0 && controller.isRecording) {
            recordingStartedExecutionId = demoExecutionId
        }
    }

    // ─── Pasos derivados del estado real (igual que BRIEF 2) ─────────────────
    val hasRun = demoExecutionId > 0 && demoExecutionId > dismissedDemoExecutionId
    val hasIncident = hasRun &&
        incidentPreview != "Ninguno" &&
        incidentPreview != demoStartIncidentPreview
    val chainChanged = hasRun && onChainStatus != demoStartChainStatus
    val chainSucceeded = chainChanged && onChainStatus.startsWith("✅")
    val chainFailed = chainChanged &&
        (onChainStatus.contains("Error", ignoreCase = true) || onChainStatus.contains("❌"))
    val recordingStartedThisRun = recordingStartedExecutionId == demoExecutionId && demoExecutionId > 0
    val recordingComplete = recordingStartedThisRun && !controller.isRecording
    val newLogText = when {
        demoStartLogLength <= 0 -> controller.logText
        controller.logText.length >= demoStartLogLength -> controller.logText.substring(demoStartLogLength)
        else -> controller.logText
    }
    val evidenceAnchored = hasRun && newLogText.contains("AUDIO: Hash anchored on blockchain")
    val shortChainError = if (chainFailed) {
        onChainStatus.lineSequence().firstOrNull().orEmpty()
            .replace("❌", "").trim().take(100)
    } else {
        ""
    }
    val txHash = if (chainSucceeded) {
        onChainStatus.lineSequence()
            .firstOrNull { it.trimStart().startsWith("TX:") }
            ?.substringAfter("TX:")
            ?.trim()
            .orEmpty()
    } else {
        ""
    }

    fun state(done: Boolean, active: Boolean = false, failed: Boolean = false) = when {
        done -> StepState.Done
        failed -> StepState.Failed
        active -> StepState.Active
        else -> StepState.Pending
    }
    val steps = listOf(
        DemoStep("Ubicación GPS capturada", state(hasIncident, active = hasRun && busy && !hasIncident)),
        DemoStep("Huella SHA-256 generada", state(hasIncident)),
        DemoStep(
            "Incidente anclado en Stellar",
            state(
                done = chainSucceeded,
                active = hasRun && busy && hasIncident && !chainSucceeded && !chainFailed,
                failed = chainFailed,
            ),
            error = shortChainError,
        ),
        DemoStep(
            "Grabando audio y video (selfie)",
            state(recordingComplete, active = hasRun && controller.isRecording),
        ),
        DemoStep(
            "Evidencia anclada",
            state(evidenceAnchored, active = recordingComplete && !evidenceAnchored),
        ),
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { if (flashAlpha > 0f) drawRect(sos.copy(alpha = flashAlpha)) },
    ) {
        val buttonSize = minOf(maxWidth * 0.7f, 300.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "DEMOSTRACIÓN EN VIVO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Pide ayuda sin tocar tu teléfono",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))
            RemoteChip(lastKey = controller.lastDemoKey)
            Spacer(Modifier.height(16.dp))

            DemoButton(
                buttonSize = buttonSize,
                waveProgress = { wave.value },
                busy = busy,
                authenticated = controller.isAuthenticated,
                onTrigger = onTrigger,
            )
            Text(
                when {
                    busy -> "Procesando incidente…"
                    else -> "Mantén presionado o usa el control"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            Timeline(steps = steps, modifier = Modifier.widthIn(max = 480.dp))

            AnimatedVisibility(
                visible = chainSucceeded && hasRun,
                enter = fadeIn(tween(Motion.COMPONENT)) + expandVertically(tween(Motion.COMPONENT, easing = Motion.Emphasized)),
                exit = fadeOut(tween(Motion.MICRO)) + shrinkVertically(tween(Motion.MICRO)),
            ) {
                DemoSuccessCard(
                    txHash = txHash,
                    onReset = onResetDemo,
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .padding(top = 16.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Modo en vivo · Stellar testnet",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RemoteChip(lastKey: String) {
    val waiting = lastKey.isEmpty()
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Rounded.SettingsRemote,
                contentDescription = null,
                tint = if (waiting) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                if (waiting) "Esperando el control…" else "Control: $lastKey",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DemoButton(
    buttonSize: Dp,
    waveProgress: () -> Float,
    busy: Boolean,
    authenticated: Boolean,
    onTrigger: () -> Unit,
) {
    val sos = GibborTheme.colors.sos
    val reduceMotion = rememberReduceMotion()
    val progress = remember { Animatable(0f) }
    val currentOnTrigger by rememberUpdatedState(onTrigger)
    val pulse = if (!reduceMotion && !busy) {
        val transition = rememberInfiniteTransition(label = "demo pulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
            label = "demo pulse progress",
        )
    } else {
        null
    }

    Box(
        modifier = Modifier
            .size(buttonSize + 48.dp)
            .drawBehind {
                val base = buttonSize.toPx() / 2f
                pulse?.value?.let { t ->
                    drawCircle(
                        color = sos.copy(alpha = 0.18f * (1f - t)),
                        radius = base * (1f + 0.08f * t),
                    )
                }
                val w = waveProgress()
                if (w < 1f) {
                    drawCircle(
                        color = sos.copy(alpha = 0.6f * (1f - w)),
                        radius = base * (1f + 0.2f * w),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(4.dp, sos, CircleShape)
                .holdToConfirm(
                    enabled = !busy,
                    durationMillis = 1200,
                    progress = progress,
                    onConfirmed = onTrigger,
                )
                .drawWithContent {
                    drawContent()
                    val p = progress.value
                    if (p > 0f) {
                        val stroke = 6.dp.toPx()
                        val inset = stroke / 2
                        drawArc(
                            color = sos,
                            startAngle = -90f,
                            sweepAngle = 360f * p,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = if (authenticated) {
                        "Botón de demostración en vivo de GIBBOR. Mantén presionado para pedir ayuda"
                    } else {
                        "Inicia sesión en GIBBOR"
                    }
                    stateDescription = if (busy) "Procesando incidente" else "Listo"
                    if (!busy) {
                        onClick(label = "Pedir ayuda") {
                            currentOnTrigger()
                            true
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            GibborLogo(size = buttonSize * 0.72f, decorative = true)
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(buttonSize - 8.dp),
                    color = sos,
                    strokeWidth = 4.dp,
                    trackColor = Color.Transparent,
                )
            }
        }
    }
}

/** Línea de tiempo vertical estilo iOS: el conector se llena de `success` al completar cada paso. */
@Composable
private fun Timeline(steps: List<DemoStep>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            steps.forEachIndexed { index, step ->
                TimelineRow(step = step, isLast = index == steps.lastIndex)
            }
        }
    }
}

@Composable
private fun TimelineRow(step: DemoStep, isLast: Boolean) {
    val success = GibborTheme.colors.success
    val sos = GibborTheme.colors.sos
    val outline = MaterialTheme.colorScheme.outline
    val fill by animateFloatAsState(
        targetValue = if (step.state == StepState.Done) 1f else 0f,
        animationSpec = tween(Motion.SCREEN, easing = Motion.Emphasized),
        label = "timeline connector",
    )
    val stateText = when (step.state) {
        StepState.Pending -> "Pendiente"
        StepState.Active -> "En curso"
        StepState.Done -> "Completado"
        StepState.Failed -> "Error"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) { stateDescription = stateText },
    ) {
        Column(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                when (step.state) {
                    StepState.Done -> Box(
                        Modifier.size(22.dp).clip(CircleShape).background(success),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    StepState.Failed -> Box(
                        Modifier.size(22.dp).clip(CircleShape).background(sos),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    StepState.Active -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                    StepState.Pending -> Box(
                        Modifier
                            .size(12.dp)
                            .border(2.dp, outline, CircleShape)
                    )
                }
            }
            if (!isLast) {
                Box(
                    Modifier
                        .weight(1f)
                        .width(2.dp)
                        .background(outline)
                        .drawWithContent {
                            drawRect(success, size = Size(size.width, size.height * fill))
                        }
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 16.dp),
        ) {
            Text(
                step.label,
                style = MaterialTheme.typography.bodyLarge,
                color = when (step.state) {
                    StepState.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
                    StepState.Failed -> sos
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.heightIn(min = 24.dp),
            )
            if (step.state == StepState.Failed && step.error.isNotEmpty()) {
                Text(
                    step.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = sos,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DemoSuccessCard(txHash: String, onReset: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.CheckCircle, null, tint = GibborTheme.colors.success)
                Text(
                    "Alerta registrada",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "TX",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                txHash.ifBlank { "no disponible" },
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onReset,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Nueva demo", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
