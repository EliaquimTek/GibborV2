package mx.edu.utez.gibbor.domain.usecase

import mx.edu.utez.gibbor.domain.model.EmergencyContacts
import mx.edu.utez.gibbor.domain.model.EmergencySmsResult
import mx.edu.utez.gibbor.domain.repository.SmsGateway

/**
 * Envía el SMS de emergencia con la ubicación a cada contacto válido.
 * Un número que falla no impide enviar a los demás.
 */
class SendEmergencySmsUseCase(
    private val smsGateway: SmsGateway,
    private val buildMessage: BuildEmergencySmsUseCase = BuildEmergencySmsUseCase(),
) {

    operator fun invoke(
        contactsRaw: String,
        sender: String,
        latitude: Double,
        longitude: Double,
    ): EmergencySmsResult {
        val numbers = EmergencyContacts.parse(contactsRaw)
        if (numbers.isEmpty()) return EmergencySmsResult.NoContacts

        val message = buildMessage(sender, latitude, longitude)
        val errors = numbers.mapNotNull { number ->
            try {
                smsGateway.send(number, message)
                null
            } catch (e: Exception) {
                "$number: ${e.message}"
            }
        }
        return EmergencySmsResult.Sent(sent = numbers.size - errors.size, total = numbers.size, errors = errors)
    }
}
