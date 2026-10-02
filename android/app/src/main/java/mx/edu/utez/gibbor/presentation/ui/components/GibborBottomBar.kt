package mx.edu.utez.gibbor.presentation.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.edu.utez.gibbor.presentation.ui.theme.Cyan
import mx.edu.utez.gibbor.presentation.ui.theme.Night
import mx.edu.utez.gibbor.presentation.ui.theme.Stroke as BorderColor
import mx.edu.utez.gibbor.presentation.ui.theme.TextHi
import mx.edu.utez.gibbor.presentation.ui.theme.TextLow

enum class GibborTab { OPERATIONAL, DEMO }

@Composable
fun GibborBottomBar(
    selectedTab: GibborTab,
    onSelectTab: (GibborTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Night)
            .navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderColor))
        BoxWithConstraints(Modifier.fillMaxWidth().height(70.dp)) {
            val itemWidth = maxWidth / 2
            val indicatorOffset by animateDpAsState(
                targetValue = if (selectedTab == GibborTab.OPERATIONAL) 0.dp else itemWidth,
                label = "tab indicator",
            )
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset + (itemWidth - 116.dp) / 2, y = 7.dp)
                    .size(width = 116.dp, height = 40.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Cyan.copy(alpha = 0.15f))
            )
            Row(Modifier.fillMaxWidth().height(70.dp)) {
                TabItem(
                    title = "GIBBOR",
                    selected = selectedTab == GibborTab.OPERATIONAL,
                    description = "Pestaña operativa de GIBBOR",
                    onClick = { onSelectTab(GibborTab.OPERATIONAL) },
                    modifier = Modifier.weight(1f),
                ) { color -> ShieldIcon(color) }
                TabItem(
                    title = "DEMO",
                    selected = selectedTab == GibborTab.DEMO,
                    description = "Pestaña de demostración",
                    onClick = { onSelectTab(GibborTab.DEMO) },
                    modifier = Modifier.weight(1f),
                ) { color -> DemoIcon(color) }
            }
        }
    }
}

@Composable
private fun TabItem(
    title: String,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit,
) {
    Column(
        modifier = modifier
            .height(70.dp)
            .clickable(onClick = onClick)
            .padding(top = 10.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        icon(if (selected) Cyan else TextLow)
        Text(
            text = title,
            modifier = Modifier.semantics { contentDescription = description },
            color = if (selected) TextHi else TextLow,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun ShieldIcon(color: Color) {
    Canvas(
        modifier = Modifier
            .size(23.dp)
            .semantics { contentDescription = "Escudo" }
    ) {
        val shield = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.06f)
            lineTo(size.width * 0.88f, size.height * 0.2f)
            lineTo(size.width * 0.84f, size.height * 0.57f)
            quadraticTo(size.width * 0.75f, size.height * 0.83f, size.width * 0.5f, size.height * 0.96f)
            quadraticTo(size.width * 0.25f, size.height * 0.83f, size.width * 0.16f, size.height * 0.57f)
            lineTo(size.width * 0.12f, size.height * 0.2f)
            close()
        }
        drawPath(shield, color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
        drawLine(
            color = color,
            start = Offset(size.width * 0.34f, size.height * 0.5f),
            end = Offset(size.width * 0.46f, size.height * 0.62f),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.46f, size.height * 0.62f),
            end = Offset(size.width * 0.68f, size.height * 0.38f),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun DemoIcon(color: Color) {
    Canvas(
        modifier = Modifier
            .size(23.dp)
            .semantics { contentDescription = "Botón de demostración" }
    ) {
        drawCircle(color, radius = size.minDimension * 0.45f, style = Stroke(width = 1.8.dp.toPx()))
        drawCircle(color, radius = size.minDimension * 0.23f)
    }
}
