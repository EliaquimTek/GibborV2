package mx.edu.utez.gibbor.data.sms

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import mx.edu.utez.gibbor.domain.repository.SmsGateway

/**
 * Envío directo de SMS con SmsManager (requiere SEND_SMS).
 * Usa la SIM predeterminada para SMS. No espera confirmación de entrega.
 */
class SmsSender(private val context: Context) : SmsGateway {

    private val smsManager: SmsManager
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    override fun send(number: String, message: String) {
        val manager = smsManager
        manager.sendMultipartTextMessage(number, null, manager.divideMessage(message), null, null)
    }
}
