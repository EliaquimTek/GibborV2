package mx.edu.utez.gibbor.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.edu.utez.gibbor.R
import mx.edu.utez.gibbor.presentation.ui.theme.Cyan
import mx.edu.utez.gibbor.presentation.ui.theme.Ink
import mx.edu.utez.gibbor.presentation.ui.theme.TextHi
import mx.edu.utez.gibbor.presentation.ui.theme.TextMid

@Composable
fun SplashOverlay(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(true) }
    var textVisible by remember { mutableStateOf(false) }
    val logoScale = remember { androidx.compose.animation.core.Animatable(0.7f) }
    val logoAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    val titleAlpha by animateFloatAsState(
        targetValue = if (textVisible && visible) 1f else 0f,
        animationSpec = tween(400),
        label = "splash title alpha",
    )
    val ring = rememberInfiniteTransition(label = "splash ring")
    val ringProgress by ring.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1250, easing = LinearEasing)),
        label = "splash ring progress",
    )

    LaunchedEffect(Unit) {
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.6f),
            )
        }
        launch {
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(700),
            )
        }
        delay(400)
        textVisible = true
        delay(900)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(300)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Ink),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(230.dp)
                        .drawBehind {
                            val radius = size.minDimension * (0.31f + ringProgress * 0.25f)
                            drawCircle(
                                color = Cyan.copy(alpha = 0.42f * (1f - ringProgress)),
                                radius = radius,
                                center = Offset(size.width / 2, size.height / 2),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx()),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.gibbor_logo),
                        contentDescription = "Logo de GIBBOR",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(166.dp)
                            .graphicsLayer {
                                scaleX = logoScale.value
                                scaleY = logoScale.value
                                alpha = logoAlpha.value
                            },
                    )
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Text(
                    text = "GIBBOR",
                    modifier = Modifier.graphicsLayer { alpha = titleAlpha },
                    color = TextHi,
                    fontSize = 23.sp,
                    letterSpacing = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Text(
                    text = "Tu evidencia, inmutable",
                    modifier = Modifier.graphicsLayer { alpha = titleAlpha },
                    color = TextMid,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}
