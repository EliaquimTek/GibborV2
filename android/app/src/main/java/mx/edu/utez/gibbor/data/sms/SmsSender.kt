package mx.edu.utez.gibbor.data.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import mx.edu.utez.gibbor.domain.repository.SmsGateway
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Envío directo de SMS con SmsManager (requiere SEND_SMS), con la SIM predeterminada.
 *
 * [send] solo entrega el mensaje a Android. El resultado real llega después por
 * [onStatus]:
 * - "enviado": la red aceptó el SMS (o el error: sin servicio, modo avión…);
 * - "entregado": el teléfono del contacto lo recibió (si el operador manda acuses).
 * El número se reporta enmascarado (solo los últimos 4 dígitos).
 */
class SmsSender(
    context: Context,
    private val onStatus: (String) -> Unit = {},
) : SmsGateway {

    private val appContext = context.applicationContext

    private val smsManager: SmsManager
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    override fun send(number: String, message: String) {
        ensureReceiver(appContext)
        val manager = smsManager
        val parts = manager.divideMessage(message)
        val masked = mask(number)
        val id = requestCodes.incrementAndGet()
        listeners[id] = onStatus
        // El acuse va solo en la última parte: un mensaje largo cuenta como un envío.
        val sent = ArrayList<PendingIntent?>(parts.size)
        val delivered = ArrayList<PendingIntent?>(parts.size)
        for (i in parts.indices) {
            val last = i == parts.lastIndex
            sent += if (last) statusIntent(ACTION_SENT, masked, id) else null
            delivered += if (last) statusIntent(ACTION_DELIVERED, masked, id) else null
        }
        manager.sendMultipartTextMessage(number, null, parts, sent, delivered)
    }

    private fun statusIntent(action: String, masked: String, id: Int): PendingIntent {
        val intent = Intent(action).setPackage(appContext.packageName)
            .putExtra(EXTRA_NUMBER, masked)
            .putExtra(EXTRA_ID, id)
        // requestCode distinto para "enviado" y "entregado" del mismo SMS
        return PendingIntent.getBroadcast(
            appContext,
            id * 2 + if (action == ACTION_SENT) 0 else 1,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val ACTION_SENT = "mx.edu.utez.gibbor.SMS_SENT"
        private const val ACTION_DELIVERED = "mx.edu.utez.gibbor.SMS_DELIVERED"
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_ID = "id"

        private val requestCodes = AtomicInteger()

        /** Quién pidió cada SMS (pánico o prueba), para avisarle a él del resultado. */
        private val listeners = ConcurrentHashMap<Int, (String) -> Unit>()

        @Volatile
        private var registered = false

        fun mask(number: String): String = "***" + number.takeLast(4)

        /** Traduce el resultCode de un SMS_SENT a texto. */
        fun sentResultText(resultCode: Int): String = when (resultCode) {
            Activity.RESULT_OK -> "enviado (la red lo aceptó)"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "NO enviado: sin servicio celular"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "NO enviado: radio apagado (modo avión)"
            SmsManager.RESULT_ERROR_NULL_PDU -> "NO enviado: mensaje inválido"
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "NO enviado: falla general (¿saldo o SIM?)"
            else -> "NO enviado: error $resultCode"
        }

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val number = intent.getStringExtra(EXTRA_NUMBER) ?: "?"
                val id = intent.getIntExtra(EXTRA_ID, -1)
                val listener = listeners[id] ?: return
                val text = when (intent.action) {
                    ACTION_SENT -> "SMS $number: ${sentResultText(resultCode)}"
                    ACTION_DELIVERED ->
                        if (resultCode == Activity.RESULT_OK) "SMS $number: entregado al teléfono del contacto"
                        else "SMS $number: sin confirmación de entrega (código $resultCode)"
                    else -> return
                }
                // Tras un fallo de envío no llegará acuse de entrega
                if (intent.action == ACTION_DELIVERED || resultCode != Activity.RESULT_OK) listeners.remove(id)
                listener(text)
            }
        }

        @Synchronized
        private fun ensureReceiver(appContext: Context) {
            if (registered) return
            val filter = IntentFilter().apply {
                addAction(ACTION_SENT)
                addAction(ACTION_DELIVERED)
            }
            ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
    }
}
