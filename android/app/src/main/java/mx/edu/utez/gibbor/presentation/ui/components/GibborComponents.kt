package mx.edu.utez.gibbor.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.edu.utez.gibbor.presentation.ui.theme.Cyan
import mx.edu.utez.gibbor.presentation.ui.theme.Panic
import mx.edu.utez.gibbor.presentation.ui.theme.Stroke
import mx.edu.utez.gibbor.presentation.ui.theme.Surface
import mx.edu.utez.gibbor.presentation.ui.theme.TextHi
import mx.edu.utez.gibbor.presentation.ui.theme.TextMid

@Composable
fun GibborCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Stroke),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) { content() }
    }
}

@Composable
fun GibborSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Medium,
        color = TextMid,
    )
}

@Composable
fun GibborPrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Cyan,
            contentColor = Color(0xFF070A24),
            disabledContainerColor = Stroke,
            disabledContentColor = TextMid,
        ),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
    }
}

@Composable
fun GibborDangerButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Panic, contentColor = TextHi),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
    }
}

@Composable
fun GibborOutlinedButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Stroke),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = TextHi,
            disabledContentColor = TextMid,
        ),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
    }
}

@Composable
fun GibborCollapsible(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "collapsible chevron",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title.uppercase(),
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium,
                color = TextHi,
            )
            Text(
                text = "⌄",
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
                fontSize = 20.sp,
                color = TextMid,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Stroke)
        )
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)) {
                content()
            }
        }
    }
}
