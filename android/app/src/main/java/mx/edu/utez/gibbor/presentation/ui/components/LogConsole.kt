package mx.edu.utez.gibbor.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.edu.utez.gibbor.presentation.ui.theme.Stroke
import mx.edu.utez.gibbor.presentation.ui.theme.TextLow
import mx.edu.utez.gibbor.presentation.ui.theme.TextMid

@Composable
fun LogConsole(
    logText: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val textAlpha = remember { Animatable(1f) }
    LaunchedEffect(logText) {
        if (logText.isEmpty()) {
            textAlpha.snapTo(0.35f)
            textAlpha.animateTo(1f, animationSpec = tween(220))
        } else {
            textAlpha.snapTo(1f)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("CONSOLA", color = TextLow, fontSize = 9.sp, letterSpacing = 1.4.sp)
            Surface(
                onClick = onClear,
                shape = RoundedCornerShape(50),
                color = Color.Transparent,
                border = BorderStroke(1.dp, Stroke),
            ) {
                Text(
                    text = "CLS",
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    color = TextMid,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 86.dp, max = 280.dp)
                .background(Color(0xFF05071A), RoundedCornerShape(12.dp))
                .padding(12.dp)
                .verticalScroll(scrollState),
        ) {
            Text(
                text = logText.ifEmpty { "— registro vacío —" },
                modifier = Modifier.alpha(textAlpha.value),
                color = if (logText.isEmpty()) TextLow else Color(0xFFB8F5C8),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
