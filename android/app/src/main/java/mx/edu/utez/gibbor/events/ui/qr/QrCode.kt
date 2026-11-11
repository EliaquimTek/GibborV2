package mx.edu.utez.gibbor.events.ui.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * QR dibujado con Canvas (nítido a cualquier tamaño). Siempre negro sobre blanco, también en
 * tema oscuro: muchos lectores no reconocen QR invertidos.
 */
@Composable
fun QrCode(
    content: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(content) { encodeQr(content) }
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(16.dp) // quiet zone
            .semantics { this.contentDescription = contentDescription },
    ) {
        val cell = size.minDimension / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    // +0.5px evita líneas finas entre módulos por redondeo
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f),
                    )
                }
            }
        }
    }
}
