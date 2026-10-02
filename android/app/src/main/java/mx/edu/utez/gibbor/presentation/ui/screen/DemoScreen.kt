package mx.edu.utez.gibbor.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.edu.utez.gibbor.R
import mx.edu.utez.gibbor.data.recording.CameraPreviewHolder
import mx.edu.utez.gibbor.presentation.MainScreenController
import mx.edu.utez.gibbor.presentation.ui.components.GibborDangerButton
import mx.edu.utez.gibbor.presentation.ui.components.StatusChip
import mx.edu.utez.gibbor.presentation.ui.theme.Cyan
import mx.edu.utez.gibbor.presentation.ui.theme.Lime
import mx.edu.utez.gibbor.presentation.ui.theme.Panic
import mx.edu.utez.gibbor.presentation.ui.theme.PanicDeep
import mx.edu.utez.gibbor.presentation.ui.theme.Stroke as BorderColor
import mx.edu.utez.gibbor.presentation.ui.theme.Surface
import mx.edu.utez.gibbor.presentation.ui.theme.SurfaceHigh
import mx.edu.utez.gibbor.presentation.ui.theme.TextHi
import mx.edu.utez.gibbor.presentation.ui.theme.TextLow
import mx.edu.utez.gibbor.presentation.ui.theme.TextMid

private val demoSteps = listOf(
    "Ubicación GPS capturada",
    "Huella SHA-256 generada",
    "Incidente anclado en Stellar",
    "Grabando audio y video (selfie)",
    "Evidencia anclada",
)

@Composable
fun DemoScreen(
    controller: MainScreenController,
    busy: Boolean,
    onChainStatus: String,
    incidentPreview: String,
    recordingElapsedSeconds: Long,
    demoExecutionId: Int,
    demoStartIncidentPreview: String,
    demoStartChainStatus: String,
    demoStartLogLength: Int,
    dismissedDemoExecutionId: Int,
    onResetDemo: () -> Unit,
    onTrigger: () -> Unit,
    onStopRecording: () -> Unit,
) {
    var recordingStartedExecutionId by remember { mutableIntStateOf(0) }
    var lastVisualExecutionId by remember { mutableIntStateOf(demoExecutionId) }
    var flashing by remember { mutableStateOf(false) }
    val wave = remember { Animatable(1f) }
    val haptic = LocalHapticFeedback.current
    val flashAlpha by animateFloatAsState(
        targetValue = if (flashing) 0.15f else 0f,
        animationSpec = tween(150),
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
        onChainStatus.lineSequence().firstOrNull().orEmpty().take(100)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                if (flashAlpha > 0f) drawRect(Panic.copy(alpha = flashAlpha))
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "DEMO",
                    color = TextHi,
                    fontSize = 25.sp,
                    letterSpacing = 6.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text("Así funcionará GIBBOR", color = TextMid, fontSize = 12.sp)
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusChip(
                        label = "Sesión",
                        value = if (controller.isAuthenticated) "Sesión activa" else "Inicia sesión",
                        color = if (controller.isAuthenticated) Lime else Panic,
                        modifier = Modifier.weight(1f),
                    )
                    StatusChip(
                        label = "Control remoto",
                        value = controller.lastDemoKey.ifEmpty { "Esperando pulsación" },
                        color = if (controller.lastDemoKey.isEmpty()) TextLow else Cyan,
                        modifier = Modifier.weight(1.35f),
                    )
                }
                Spacer(Modifier.height(8.dp))

                BoxWithConstraints(contentAlignment = Alignment.Center) {
                    val buttonSize = minOf(maxWidth * 0.70f, 280.dp)
                    DemoPanicButton(
                        buttonSize = buttonSize,
                        waveProgress = wave.value,
                        isRecording = controller.isRecording,
                        recordingElapsedSeconds = recordingElapsedSeconds,
                        authenticated = controller.isAuthenticated,
                        busy = busy,
                        onClick = onTrigger,
                    )
                }

                AnimatedVisibility(
                    visible = busy && hasRun,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Cyan,
                            strokeWidth = 1.6.dp,
                        )
                        Text("Procesando incidente…", color = Cyan, fontSize = 11.sp)
                    }
                }

                AnimatedVisibility(visible = hasRun, enter = fadeIn(), exit = fadeOut()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        DemoStepRow("Ubicación GPS capturada", completed = hasIncident)
                        DemoStepRow("Huella SHA-256 generada", completed = hasIncident)
                        DemoStepRow(
                            label = "Incidente anclado en Stellar",
                            completed = chainSucceeded,
                            active = busy && !chainSucceeded && !chainFailed,
                            failed = chainFailed,
                        )
                        if (shortChainError.isNotEmpty()) {
                            Text(
                                shortChainError,
                                modifier = Modifier.padding(start = 27.dp, bottom = 2.dp),
                                color = Panic,
                                fontSize = 9.sp,
                                maxLines = 1,
                            )
                        }
                        DemoStepRow(
                            label = "Grabando audio y video (selfie)",
                            completed = recordingComplete,
                            active = controller.isRecording,
                        )
                        DemoStepRow("Evidencia anclada", completed = evidenceAnchored)
                    }
                }

                AnimatedVisibility(
                    visible = controller.isRecording,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        GibborDangerButton(
                            text = "Detener grabación",
                            onClick = onStopRecording,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = chainSucceeded && hasRun,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    DemoSuccessCard(
                        txHash = txHash,
                        onReset = onResetDemo,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Modo en vivo · registra un incidente real en Stellar testnet",
                    color = TextMid,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Empareja el control en Ajustes › Bluetooth. Cualquier botón del control lo activa.",
                    color = TextLow,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DemoPanicButton(
    buttonSize: Dp,
    waveProgress: Float,
    isRecording: Boolean,
    recordingElapsedSeconds: Long,
    authenticated: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "demo panic pulse")
    val pulseProgress by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "demo pulse",
    )
    val previewAlpha by animateFloatAsState(
        targetValue = if (isRecording) 1f else 0f,
        animationSpec = tween(300),
        label = "selfie preview visibility",
    )

    Box(
        modifier = Modifier.size(buttonSize + 68.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val baseRadius = buttonSize.toPx() * 0.5f
            val center = Offset(size.width / 2, size.height / 2)
            for (index in 0..1) {
                val progress = (pulseProgress + index * 0.5f) % 1f
                drawCircle(
                    color = Panic.copy(alpha = 0.3f * (1f - progress)),
                    radius = baseRadius * (0.9f + progress * 0.38f),
                    center = center,
                    style = Stroke(width = 1.8.dp.toPx()),
                )
            }
            if (waveProgress < 1f) {
                for (index in 0..2) {
                    val ringProgress = (waveProgress - index * 0.22f).coerceIn(0f, 1f)
                    if (waveProgress >= index * 0.22f) {
                        drawCircle(
                            color = Panic.copy(alpha = 0.8f * (1f - ringProgress)),
                            radius = baseRadius * (0.58f + ringProgress * 0.68f),
                            center = center,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Panic, PanicDeep)))
                .clickable(enabled = !busy, onClick = onClick)
                .semantics {
                    role = Role.Button
                    contentDescription = if (authenticated) {
                        "Botón de demostración en vivo de GIBBOR"
                    } else {
                        "Inicia sesión en GIBBOR"
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        CameraPreviewHolder.surfaceProvider = surfaceProvider
                    }
                },
                modifier = Modifier
                    .size(buttonSize * 0.72f)
                    .clip(CircleShape)
                    .alpha(previewAlpha),
                onRelease = { view ->
                    if (CameraPreviewHolder.surfaceProvider === view.surfaceProvider) {
                        CameraPreviewHolder.surfaceProvider = null
                    }
                },
            )
            Image(
                painter = painterResource(R.drawable.gibbor_logo),
                contentDescription = "Logo de GIBBOR",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(buttonSize * 0.72f)
                    .clip(CircleShape)
                    .border(2.dp, TextHi.copy(alpha = 0.85f), CircleShape)
                    .alpha(1f - previewAlpha),
            )
            if (!authenticated) {
                Surface(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Color(0xD9070A24),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        "Inicia sesión en GIBBOR",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = TextHi,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (busy) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                    color = SurfaceHigh.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        "Procesando…",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Cyan,
                        fontSize = 10.sp,
                    )
                }
            }
            AnimatedVisibility(
                visible = isRecording,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = buttonSize * 0.1f),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Surface(
                    color = Panic.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, TextHi.copy(alpha = 0.2f)),
                ) {
                    Text(
                        text = "● REC  " + formatDuration(recordingElapsedSeconds),
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        color = TextHi,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoStepRow(
    label: String,
    completed: Boolean,
    active: Boolean = false,
    failed: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(19.dp), contentAlignment = Alignment.Center) {
            when {
                completed -> Text("✓", color = Lime, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                failed -> Text("✕", color = Panic, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                active -> CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    color = if (label.startsWith("Grabando")) Panic else Cyan,
                    strokeWidth = 1.5.dp,
                )
                else -> Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(TextLow)
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Text(
            label,
            color = when {
                completed -> Lime
                failed -> Panic
                active && label.startsWith("Grabando") -> Panic
                active -> TextHi
                else -> TextMid
            },
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun DemoSuccessCard(txHash: String, onReset: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Lime.copy(alpha = 0.7f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Alerta registrada", color = Lime, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Button(
                    onClick = onReset,
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Cyan.copy(alpha = 0.15f),
                        contentColor = Cyan,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                ) {
                    Text("Nueva demo", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                text = if (txHash.isBlank()) "TX: no disponible" else "TX: " + txHash,
                color = TextMid,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                maxLines = 2,
            )
        }
    }
}

private fun formatDuration(seconds: Long): String =
    "%02d:%02d".format(seconds / 60, seconds % 60)
