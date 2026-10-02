package mx.edu.utez.gibbor.presentation.ui.screen.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mx.edu.utez.gibbor.presentation.ui.components.GibborLogo
import mx.edu.utez.gibbor.presentation.ui.theme.Motion

/**
 * Acceso: héroe + beneficios + correo (paso 1) y código de acceso (paso 2).
 * El código se muestra en 6 cajas de solo lectura, como hasta ahora.
 */
@Composable
fun OnboardingScreen(
    email: String,
    onEmailChange: (String) -> Unit,
    otpCode: String,
    otpRequested: Boolean,
    onRequestOtp: () -> Unit,
    onSignIn: () -> Unit,
    onChangeEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Box(
            Modifier.shadow(elevation = 16.dp, shape = CircleShape, clip = false),
        ) {
            GibborLogo(size = 120.dp)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "GIBBOR",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Ayuda inmediata. Evidencia que nadie puede borrar.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Benefit(Icons.Rounded.TouchApp, "Un botón, sin desbloquear")
            Benefit(Icons.Rounded.LocationOn, "Ubicación y video protegidos")
            Benefit(Icons.Rounded.Lock, "Registro inmutable en blockchain")
        }
        Spacer(Modifier.height(32.dp))

        AnimatedContent(
            targetState = otpRequested,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            transitionSpec = {
                val dir = if (targetState) 1 else -1
                (slideInHorizontally(tween(Motion.COMPONENT, easing = Motion.Emphasized)) { it / 4 * dir } +
                    fadeIn(tween(Motion.COMPONENT))) togetherWith
                    (slideOutHorizontally(tween(Motion.MICRO, easing = Motion.EmphasizedAccelerate)) { -it / 4 * dir } +
                        fadeOut(tween(Motion.MICRO)))
            },
            label = "onboarding step",
        ) { codeStep ->
            if (!codeStep) {
                EmailStep(email = email, onEmailChange = onEmailChange, onContinue = onRequestOtp)
            } else {
                CodeStep(
                    email = email,
                    otpCode = otpCode,
                    onSignIn = onSignIn,
                    onChangeEmail = onChangeEmail,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Al continuar se crea tu wallet segura en Stellar.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Benefit(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun EmailStep(
    email: String,
    onEmailChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val canContinue = email.isNotBlank()
    Column {
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Correo electrónico") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (canContinue) onContinue() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.EmailAddress },
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onContinue,
            enabled = canContinue,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text("Continuar", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CodeStep(
    email: String,
    otpCode: String,
    onSignIn: () -> Unit,
    onChangeEmail: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Tu código de acceso",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            email.trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Código de acceso: " + otpCode.toCharArray().joinToString(" ")
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            repeat(6) { index ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 52.dp)
                        .heightIn(min = 56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            otpCode.getOrNull(index)?.toString().orEmpty(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSignIn,
            enabled = email.isNotBlank(),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text("Iniciar sesión", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onChangeEmail, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Cambiar correo", style = MaterialTheme.typography.labelLarge)
        }
    }
}
