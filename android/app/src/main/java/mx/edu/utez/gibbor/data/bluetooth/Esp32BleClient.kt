package mx.edu.utez.gibbor.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.UUID

/**
 * Conexión BLE con el botón físico ESP32-C3 (Nordic UART Service).
 *
 * - Escaneo **filtrado por el UUID del servicio NUS**: Android sigue entregando
 *   resultados con la pantalla apagada y el filtrado lo hace el chip Bluetooth.
 * - Tras conectar pide un **MTU** mayor para que cada notificación llegue completa.
 * - Con [connect] en modo `autoReconnect`, si la conexión se cae sin que el usuario
 *   lo pida, vuelve a buscar el botón indefinidamente con espera creciente
 *   ([ReconnectBackoff]) y reactiva las notificaciones al reconectar.
 *
 * Todas las notificaciones hacia quien lo usa se entregan a través de [postToMain].
 */
class Esp32BleClient(
    private val context: Context,
    private val postToMain: (() -> Unit) -> Unit,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun onLog(message: String)
        fun onStatus(status: String)
        fun onTrigger(line: String)
        fun onConnectionChanged(connected: Boolean) {}
    }

    companion object {
        const val DEVICE_NAME = "GIBBOR_ESP32"

        // Nordic UART Service (NUS)
        val NUS_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val NUS_TX_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        /** MTU que se pide al conectar (el firmware acepta hasta 256). */
        const val DESIRED_MTU = 185

        /** Ventana de un escaneo: primer intento y cada reintento. */
        private const val SCAN_WINDOW_MS = 12_000L

        /** Si onMtuChanged no llega, se descubren servicios igualmente. */
        private const val MTU_FALLBACK_MS = 3_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val backoff = ReconnectBackoff()

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var scanning = false
    @Volatile private var connected = false
    @Volatile private var servicesRequested = false

    /** El usuario quiere estar conectado: se reintenta ante cualquier caída. */
    @Volatile var autoReconnect = false
        private set

    /** Escaneando, conectando o esperando para reintentar. */
    @Volatile var isConnecting = false
        private set

    val isConnected: Boolean get() = connected

    var lastDeviceAddress: String? = null
        private set

    private var adapterRef: BluetoothAdapter? = null

    private fun log(message: String) = callbacks.onLog(message)

    // ─── API pública ──────────────────────────────────────────────────────

    /**
     * Busca y conecta el botón. Con [autoReconnect] = true se mantiene la conexión
     * hasta llamar a [disconnect] (reintenta al perderla o si no lo encuentra).
     */
    fun connect(adapter: BluetoothAdapter, autoReconnect: Boolean = true) {
        closeGatt()
        stopScan()
        cancelRetry()
        adapterRef = adapter
        this.autoReconnect = autoReconnect
        backoff.reset()
        isConnecting = true
        startScan(lowLatency = true)
    }

    /** Desconexión pedida por el usuario: no se reintenta. */
    fun disconnect(silent: Boolean) {
        autoReconnect = false
        cancelRetry()
        stopScan()
        closeGatt()
        val wasConnected = connected
        connected = false
        isConnecting = false
        callbacks.onStatus("Status: Disconnected")
        if (wasConnected) callbacks.onConnectionChanged(false)
        if (!silent) log("BLE: Manually disconnected.")
    }

    /** El Bluetooth del teléfono se apagó: se pausa sin olvidar que hay que reconectar. */
    fun onAdapterDisabled() {
        cancelRetry()
        stopScan()
        closeGatt()
        val wasConnected = connected
        connected = false
        callbacks.onStatus("Status: Bluetooth off")
        if (wasConnected) callbacks.onConnectionChanged(false)
        log("BLE: Bluetooth turned off. Will reconnect when it is back on.")
    }

    /** El Bluetooth volvió a encenderse. */
    fun onAdapterEnabled(adapter: BluetoothAdapter) {
        if (!autoReconnect || connected) return
        adapterRef = adapter
        backoff.reset()
        isConnecting = true
        log("BLE: Bluetooth on again. Searching for $DEVICE_NAME...")
        startScan(lowLatency = true)
    }

    // ─── Escaneo ──────────────────────────────────────────────────────────

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!scanning) return
            // El filtro ya garantiza el servicio NUS; el nombre puede venir solo en
            // la respuesta de escaneo, así que un nombre vacío también se acepta.
            val name = result.scanRecord?.deviceName ?: result.device.name
            if (name != null && name != DEVICE_NAME) return
            stopScan()
            postToMain { log("BLE: Found $DEVICE_NAME (MAC=${result.device.address}, RSSI=${result.rssi})") }
            connectGatt(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            postToMain {
                log("BLE ERROR: Scan failed (code=$errorCode)")
                onAttemptFailed("Status: BLE scan failed")
            }
        }
    }

    private val scanWindowEnd = Runnable {
        if (!scanning) return@Runnable
        stopScan()
        log("BLE: '$DEVICE_NAME' not found in this attempt.")
        onAttemptFailed("Status: Not found")
    }

    private val retryRunnable = Runnable {
        if (autoReconnect && !connected) startScan(lowLatency = false)
    }

    @SuppressLint("MissingPermission")
    private fun startScan(lowLatency: Boolean) {
        val adapter = adapterRef
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: run {
            log("BLE ERROR: BLE scanner not available (Bluetooth off?).")
            onAttemptFailed("Status: Bluetooth off")
            return
        }

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(NUS_SERVICE_UUID)).build()
        )
        // Primer intento rápido; los reintentos en segundo plano gastan menos batería.
        val settings = ScanSettings.Builder()
            .setScanMode(if (lowLatency) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_BALANCED)
            .build()

        isConnecting = true
        callbacks.onStatus(if (backoff.attempt == 0) "Status: Scanning..." else "Status: Scanning (reconnecting)...")
        log("BLE: Scanning for $DEVICE_NAME (service filter)...")

        try {
            scanning = true
            scanner.startScan(filters, settings, scanCallback)
            handler.postDelayed(scanWindowEnd, SCAN_WINDOW_MS)
        } catch (e: SecurityException) {
            scanning = false
            log("BLE ERROR: Permissions error: ${e.message}")
            autoReconnect = false
            isConnecting = false
            callbacks.onStatus("Status: Permissions error")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        handler.removeCallbacks(scanWindowEnd)
        if (!scanning) return
        scanning = false
        try {
            adapterRef?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
            // Adaptador apagado o sin permisos: nada que detener
        }
    }

    /** Un intento (escaneo o conexión) terminó sin éxito. Se ejecuta en el hilo principal. */
    private fun onAttemptFailed(status: String) {
        if (!autoReconnect) {
            isConnecting = false
            callbacks.onStatus(status)
            return
        }
        val delayMs = backoff.nextDelayMs()
        isConnecting = true
        callbacks.onStatus("Status: Scanning (reconnecting)...")
        log("BLE: Retrying in ${delayMs / 1000} s (attempt ${backoff.attempt}).")
        handler.removeCallbacks(retryRunnable)
        handler.postDelayed(retryRunnable, delayMs)
    }

    private fun cancelRetry() {
        handler.removeCallbacks(retryRunnable)
    }

    // ─── GATT ─────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun connectGatt(device: BluetoothDevice) {
        lastDeviceAddress = device.address
        servicesRequested = false
        postToMain { log("BLE: Connecting GATT...") }
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        val g = gatt
        gatt = null
        try {
            g?.disconnect()
            g?.close()
        } catch (_: Exception) {
            // Ya cerrado
        }
    }

    @SuppressLint("MissingPermission")
    private fun discoverServicesOnce(g: BluetoothGatt) {
        if (servicesRequested) return
        servicesRequested = true
        g.discoverServices()
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    postToMain { log("BLE: GATT connected. Requesting MTU $DESIRED_MTU...") }
                    if (!g.requestMtu(DESIRED_MTU)) {
                        discoverServicesOnce(g)
                    } else {
                        handler.postDelayed({ if (gatt === g) discoverServicesOnce(g) }, MTU_FALLBACK_MS)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    g.close()
                    if (gatt !== g) return // conexión vieja ya reemplazada
                    gatt = null
                    postToMain {
                        val wasConnected = connected
                        connected = false
                        log("BLE: Connection closed (status=$status).")
                        if (wasConnected) callbacks.onConnectionChanged(false)
                        if (autoReconnect) {
                            if (wasConnected) backoff.reset()
                            onAttemptFailed("Status: Disconnected")
                        } else {
                            isConnecting = false
                            callbacks.onStatus("Status: Disconnected")
                        }
                    }
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            postToMain { log("BLE: MTU=$mtu (status=$status)") }
            discoverServicesOnce(g)
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val tx = g.getService(NUS_SERVICE_UUID)?.getCharacteristic(NUS_TX_UUID)
            if (status != BluetoothGatt.GATT_SUCCESS || tx == null) {
                postToMain { log("BLE ERROR: UART service not found on $DEVICE_NAME (status=$status).") }
                g.disconnect() // STATE_DISCONNECTED decidirá si reintenta
                return
            }

            g.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(CCCD_UUID)
            if (cccd == null) {
                postToMain { markConnected() }
                return
            }
            val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, enable)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = enable
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid != CCCD_UUID) return
            postToMain {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    markConnected()
                } else {
                    log("BLE ERROR: Could not enable notifications (status=$status).")
                    g.disconnect()
                }
            }
        }

        // Android 13+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleIncoming(value)
        }

        // Android 12 y anteriores
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleIncoming(characteristic.value ?: return)
            }
        }
    }

    private fun markConnected() {
        connected = true
        isConnecting = false
        backoff.reset()
        callbacks.onStatus("Status: Connected to $DEVICE_NAME")
        log("BLE SUCCESS: Notifications enabled. Listening...")
        callbacks.onConnectionChanged(true)
    }

    private fun handleIncoming(value: ByteArray) {
        PanicMessageParser.lines(value).forEach { line ->
            postToMain {
                log("RX -> $line")
                if (PanicMessageParser.isTrigger(line)) {
                    log(if (PanicMessageParser.isDeferred(line)) "🚨 Trigger detectado (diferido)." else "🚨 Trigger detectado.")
                    callbacks.onTrigger(line)
                }
            }
        }
    }
}
