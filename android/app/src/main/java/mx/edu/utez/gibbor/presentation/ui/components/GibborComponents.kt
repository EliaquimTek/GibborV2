package mx.edu.utez.gibbor.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.edu.utez.gibbor.presentation.ui.theme.GibborBorder
import mx.edu.utez.gibbor.presentation.ui.theme.GibborLight
import mx.edu.utez.gibbor.presentation.ui.theme.GibborMid
import mx.edu.utez.gibbor.presentation.ui.theme.GibborNavy
import mx.edu.utez.gibbor.presentation.ui.theme.GibborRed
import mx.edu.utez.gibbor.presentation.ui.theme.GibborSurface

// ─── Design System Components ─────────────────────────────────────────────

@Composable
fun GibborCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GibborSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) { content() }
    }
}

@Composable
fun GibborSectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Medium,
        color = GibborMid
    )
}

@Composable
fun GibborPrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = GibborNavy,
            disabledContainerColor = GibborBorder
        )
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
    }
}

@Composable
fun GibborDangerButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = GibborRed)
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
    }
}

@Composable
fun GibborOutlinedButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, GibborBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = GibborNavy)
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.5.sp)
    }
}

@Composable
fun GibborCollapsible(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title.uppercase(),
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium,
                color = GibborLight
            )
            Text(if (expanded) "—" else "+", fontSize = 16.sp, color = GibborLight)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(GibborBorder)
        )
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
fun OTPDialog(
    onOTPSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var otpCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter OTP Code") },
        text = {
            OutlinedTextField(
                value = otpCode,
                onValueChange = { otpCode = it },
                label = { Text("OTP") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onOTPSubmit(otpCode) }) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
