package mx.edu.utez.gibbor.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import mx.edu.utez.gibbor.presentation.ui.theme.Cyan
import mx.edu.utez.gibbor.presentation.ui.theme.Ink
import mx.edu.utez.gibbor.presentation.ui.theme.Night
import mx.edu.utez.gibbor.presentation.ui.theme.Violet

@Composable
fun GibborBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ink, Night)))
            .drawBehind {
                drawCircle(
                    color = Cyan.copy(alpha = 0.075f),
                    radius = size.minDimension * 0.58f,
                    center = Offset(size.width * 0.94f, size.height * 0.12f),
                )
                drawCircle(
                    color = Violet.copy(alpha = 0.07f),
                    radius = size.minDimension * 0.68f,
                    center = Offset(size.width * 0.02f, size.height * 0.56f),
                )
                drawCircle(
                    color = Cyan.copy(alpha = 0.035f),
                    radius = size.minDimension * 0.5f,
                    center = Offset(size.width * 0.9f, size.height * 0.94f),
                )
            },
        content = content,
    )
}
