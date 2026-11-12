package mx.edu.utez.gibbor.domain.usecase

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Texto del SMS de emergencia. Sin acentos ni emojis para que quepa en codificación
 * GSM-7 (160 caracteres por parte en lugar de 70).
 */
class BuildEmergencySmsUseCase {

    operator fun invoke(
        sender: String,
        latitude: Double,
        longitude: Double,
        timeMillis: Long = System.currentTimeMillis(),
    ): String {
        val lat = String.format(Locale.US, "%.6f", latitude)
        val lon = String.format(Locale.US, "%.6f", longitude)
        val time = SimpleDateFormat("HH:mm", Locale.US).format(Date(timeMillis))
        return "ALERTA GIBBOR: $sender pidio ayuda a las $time. " +
            "Ubicacion: https://maps.google.com/?q=$lat,$lon"
    }

    companion object {
        /** SMS de prueba desde Ajustes: deja claro que NO es una emergencia. */
        fun testMessage(sender: String): String =
            "PRUEBA GIBBOR: $sender te agrego como contacto de emergencia. " +
                "Esto es solo una prueba, no es una emergencia."
    }
}
