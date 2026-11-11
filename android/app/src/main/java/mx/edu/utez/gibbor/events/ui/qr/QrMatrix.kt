package mx.edu.utez.gibbor.events.ui.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Codifica [content] como QR y devuelve la matriz de módulos (true = módulo oscuro), sin margen:
 * el margen blanco (quiet zone) lo dibuja la UI. Corrección de errores M (~15 %), suficiente
 * para leer desde una pantalla a 1–2 m.
 */
fun encodeQr(content: String): BitMatrix = QRCodeWriter().encode(
    content,
    BarcodeFormat.QR_CODE,
    0,
    0,
    mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 0,
    ),
)
