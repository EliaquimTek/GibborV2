package mx.edu.utez.gibbor.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mx.edu.utez.gibbor.data.bluetooth.Esp32BleClient
import mx.edu.utez.gibbor.data.session.ProtectionPrefs
import mx.edu.utez.gibbor.presentation.MainActivity

/**
 * Servicio en primer plano que mantiene la protección con la app cerrada y la
 * pantalla apagada:
 *
 * - Mantiene la conexión BLE con el botón y la recupera sola si se pierde.
 * - Al recibir un pánico ejecuta [PanicFlow] completo (ubicación, grabación,
 *   SMS, blockchain) sin necesitar la Activity.
 *
 * Android solo concede cámara, micrófono y ubicación a un servicio en segundo plano
 * si éste **arrancó con la app en primer plano**. Por eso lo arranca la Activity
 * (al conectar el botón o al abrir la app) declarando desde el inicio los tipos
 * connectedDevice|location|camera|microphone. Si el sistema lo reinicia por su
 * cuenta (START_STICKY) puede quedar en [ProtectionState.limitedMode].
 */
class ProtectionService : LifecycleService() {

    companion object {
        const val ACTION_START = "mx.edu.utez.gibbor.action.START_PROTECTION"
        const val ACTION_STOP = "mx.edu.utez.gibbor.action.STOP_PROTECTION"
        const val ACTION_TRIGGER = "mx.edu.utez.gibbor.action.TRIGGER"
        const val ACTION_STOP_RECORDING = "mx.edu.utez.gibbor.action.STOP_RECORDING"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_FRONT_CAMERA = "front_camera"

        private const val CHANNEL_ID = "gibbor_protection"
        private const val NOTIFICATION_ID = 1002
        private const val WAKE_LOCK_TAG = "gibbor:emergency"

        @Volatile var isRunning = false
            private set

        /** Activa la protección (llamar con la app en primer plano). */
        fun start(context: Context) = command(context, ACTION_START)

        fun stopProtection(context: Context) {
            if (!isRunning) {
                ProtectionPrefs(context).protectionEnabled = false
                return
            }
            command(context, ACTION_STOP)
        }

        fun trigger(context: Context, source: String, useFrontCamera: Boolean) =
            command(context, ACTION_TRIGGER) {
                putExtra(EXTRA_SOURCE, source)
                putExtra(EXTRA_FRONT_CAMERA, useFrontCamera)
            }

        fun stopRecording(context: Context) {
            if (isRunning) command(context, ACTION_STOP_RECORDING)
        }

        private fun command(context: Context, action: String, extras: Intent.() -> Unit = {}) {
            val intent = Intent(context, ProtectionService::class.java).setAction(action).apply(extras)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var prefs: ProtectionPrefs
    private lateinit var ble: Esp32BleClient
    private lateinit var panicFlow: PanicFlow
    private var wakeLock: PowerManager.WakeLock? = null
    private var limitedMode = false
    /** La notificación solo se actualiza mientras el servicio está en primer plano. */
    private var foregroundActive = false

    private val adapter: BluetoothAdapter?
        get() = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_OFF -> ble.onAdapterDisabled()
                BluetoothAdapter.STATE_ON -> if (prefs.protectionEnabled) adapter?.let(ble::onAdapterEnabled)
            }
        }
    }

    // ─── Ciclo de vida ────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = ProtectionPrefs(this)
        createNotificationChannel()

        ble = Esp32BleClient(
            context = this,
            postToMain = { block -> mainHandler.post(block) },
            callbacks = object : Esp32BleClient.Callbacks {
                override fun onLog(message: String) = ProtectionHub.log(message)
                override fun onStatus(status: String) {
                    ProtectionHub.update { it.copy(statusText = status, isConnecting = ble.isConnecting) }
                }
                override fun onTrigger(line: String) {
                    ProtectionHub.update { it.copy(esp32TriggerCount = it.esp32TriggerCount + 1) }
                    panicFlow.trigger("ESP32", useFrontCamera = false)
                }
                override fun onConnectionChanged(connected: Boolean) {
                    ProtectionHub.update { it.copy(isConnecting = ble.isConnecting) }
                }
            },
        )
        panicFlow = PanicFlow(
            context = this,
            lifecycleOwner = this,
            scope = lifecycleScope,
            prefs = prefs,
            onSessionChanged = ::onSessionChanged,
        )

        ContextCompat.registerReceiver(
            this,
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        ProtectionHub.update { it.copy(protectionActive = true) }

        // La notificación refleja el estado (conectado, buscando, grabando…)
        lifecycleScope.launch {
            ProtectionHub.state
                .map { NotificationKey(it.statusText, it.isConnecting, it.busy, it.isRecording, it.limitedMode) }
                .distinctUntilChanged()
                .collect { refreshNotification() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!ensureForeground()) return START_NOT_STICKY

        when (intent?.action) {
            ACTION_START -> {
                prefs.protectionEnabled = true
                connectButton()
            }
            ACTION_STOP -> {
                prefs.protectionEnabled = false
                ble.disconnect(silent = false)
                ProtectionHub.log("PROTECTION: disabled by the user.")
                stopIfIdle()
            }
            ACTION_TRIGGER -> {
                panicFlow.trigger(
                    source = intent.getStringExtra(EXTRA_SOURCE) ?: "APP",
                    useFrontCamera = intent.getBooleanExtra(EXTRA_FRONT_CAMERA, false),
                )
                stopIfIdle() // p. ej. disparo ignorado sin sesión y sin protección activa
            }
            ACTION_STOP_RECORDING -> {
                panicFlow.stopRecording()
                stopIfIdle()
            }
            null -> {
                // Reinicio del sistema (START_STICKY)
                ProtectionHub.log("PROTECTION: service restarted by the system.")
                if (prefs.protectionEnabled) connectButton() else stopIfIdle()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        foregroundActive = false
        unregisterReceiver(bluetoothStateReceiver)
        ble.disconnect(silent = true)
        panicFlow.release()
        releaseWakeLock()
        ProtectionHub.update {
            it.copy(protectionActive = false, isConnecting = false, statusText = "Status: Disconnected", busy = false)
        }
        super.onDestroy()
    }

    // ─── Botón BLE ────────────────────────────────────────────────────────

    private fun connectButton() {
        val adapter = adapter ?: run {
            ProtectionHub.log("PROTECTION: this phone has no Bluetooth.")
            return
        }
        if (!hasBluetoothPermissions()) {
            ProtectionHub.log("PROTECTION: missing Bluetooth permissions. Open GIBBOR and grant them.")
            return
        }
        if (ble.isConnected || ble.isConnecting) return
        ProtectionHub.log("PROTECTION: active. Keeping ${Esp32BleClient.DEVICE_NAME} connected in background.")
        ble.connect(adapter, autoReconnect = true)
    }

    private fun hasBluetoothPermissions(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_CONNECT))

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    // ─── Sesión de emergencia ─────────────────────────────────────────────

    private fun onSessionChanged() {
        if (panicFlow.isIdle) {
            releaseWakeLock()
            stopIfIdle()
        } else {
            acquireWakeLock()
        }
    }

    /** Se apaga solo si la protección está desactivada y no hay emergencia en curso. */
    private fun stopIfIdle() {
        if (prefs.protectionEnabled || !panicFlow.isIdle) return
        foregroundActive = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        // La CPU no se duerme mientras se graba y se ancla la evidencia (con tope de seguridad)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(PanicFlow.MAX_RECORDING_MS + 5 * 60_000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ─── Primer plano y notificación ──────────────────────────────────────

    /** Tipos de servicio que el sistema permite según los permisos concedidos. */
    private fun foregroundTypes(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var types = 0
        if (hasBluetoothPermissions()) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (granted(Manifest.permission.CAMERA)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (granted(Manifest.permission.RECORD_AUDIO)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return types
    }

    private fun ensureForeground(): Boolean {
        val types = foregroundTypes()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && types == 0) {
            ProtectionHub.log("PROTECTION: no permissions to run in background. Open GIBBOR and grant them.")
            stopSelf()
            return false
        }
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), types)
            foregroundActive = true
            setLimitedMode(false)
            true
        } catch (e: Exception) {
            // Arranque desde segundo plano: Android niega cámara/micrófono/ubicación
            ProtectionHub.log("PROTECTION: limited mode (${e.javaClass.simpleName}). Open GIBBOR to restore camera and location.")
            try {
                val fallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasBluetoothPermissions()) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                } else 0
                ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), fallback)
                foregroundActive = true
                setLimitedMode(true)
                true
            } catch (e2: Exception) {
                ProtectionHub.log("PROTECTION ERROR: could not run in background (${e2.message}).")
                stopSelf()
                false
            }
        }
    }

    private fun setLimitedMode(limited: Boolean) {
        limitedMode = limited
        ProtectionHub.update { it.copy(limitedMode = limited) }
    }

    private data class NotificationKey(
        val status: String,
        val connecting: Boolean,
        val busy: Boolean,
        val recording: Boolean,
        val limited: Boolean,
    )

    private fun refreshNotification() {
        if (!foregroundActive) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        try {
            nm.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: SecurityException) {
            // Sin POST_NOTIFICATIONS: el servicio sigue funcionando sin notificación visible
        }
    }

    private fun buildNotification(): Notification {
        val s = ProtectionHub.state.value
        val connected = s.statusText.contains("Connected to", ignoreCase = true)
        val text = when {
            s.isRecording -> "Emergencia activa · grabando evidencia"
            s.busy -> "Enviando alerta…"
            connected -> "Botón conectado · estás protegido"
            s.statusText.contains("Bluetooth off", ignoreCase = true) -> "Bluetooth apagado · enciéndelo para usar el botón"
            prefs.protectionEnabled -> "Buscando el botón…"
            else -> "Botón desconectado"
        } + if (limitedMode) " · abre GIBBOR para habilitar cámara y ubicación" else ""

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (s.isRecording) "GIBBOR · Emergencia" else "GIBBOR · Protección activa")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(if (s.isRecording) android.R.drawable.presence_video_online else android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (s.isRecording) {
            builder.addAction(0, "Detener grabación", servicePendingIntent(ACTION_STOP_RECORDING, 1))
        } else if (!s.busy) {
            builder.addAction(0, "Desactivar", servicePendingIntent(ACTION_STOP, 2))
        }
        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, ProtectionService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, requestCode, intent, flags)
        } else {
            PendingIntent.getService(this, requestCode, intent, flags)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Protección GIBBOR", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Estado del botón de pánico y de la grabación de evidencia"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
