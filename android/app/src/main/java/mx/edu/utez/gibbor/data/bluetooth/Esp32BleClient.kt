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
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID

/**
 * Conexión BLE con el botón físico ESP32-C3 (Nordic UART Service) y lectura
 * de triggers de pánico.
 *
 * El ESP32 notifica por la característica TX el texto "GIBBOR_PANIC_TRIGGER"
 * (o "Boton"). Toda notificación hacia la UI se entrega a través de
 * [postToMain], salvo en [disconnect], que se ejecuta en el hilo que la invoca.
 */
class Esp32BleClient(
    private val context: Context,
    private val postToMain: (() -> Unit) -> Unit,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun onLog(message: String)
        fun onStatus(status: String)
        fun onTrigger()
    }

    companion object {
        const val DEVICE_NAME = "GIBBOR_ESP32"

        // Nordic UART Service (NUS)
        private val NUS_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val NUS_TX_UUID: UUID      = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        private val CCCD_UUID: UUID        = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        private const val SCAN_TIMEOUT_MS = 10_000L
    }

    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var scanning = false
    @Volatile var isConnecting = false
        private set

    private var adapterRef: BluetoothAdapter? = null

    private fun log(message: String) = callbacks.onLog(message)

    // ─── Escaneo ──────────────────────────────────────────────────────────

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name
            if (name != DEVICE_NAME || !scanning) return
            stopScan()
            postToMain { log("BLE: Found $DEVICE_NAME (MAC=${result.device.address}, RSSI=${result.rssi})") }
            connectGatt(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            postToMain {
                isConnecting = false
                callbacks.onStatus("Status: BLE scan failed")
                log("BLE ERROR: Scan failed (code=$errorCode)")
            }
        }
    }

    private val scanTimeout = Runnable {
        if (scanning) {
            stopScan()
            isConnecting = false
            callbacks.onStatus("Status: Not found")
            log("BLE: '$DEVICE_NAME' not found. Make sure the ESP32 is on and nearby.")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(adapter: BluetoothAdapter) {
        // Primero limpiar cualquier conexión anterior
        disconnect(silent = true)

        adapterRef = adapter
        val scanner = adapter.bluetoothLeScanner ?: run {
            log("BLE ERROR: BLE scanner not available.")
            return
        }

        isConnecting = true
        callbacks.onStatus("Status: Scanning...")
        log("BLE INIT: Scanning for $DEVICE_NAME...")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanning = true
            scanner.startScan(null, settings, scanCallback)
            handler.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)
        } catch (e: SecurityException) {
            scanning = false
            isConnecting = false
            callbacks.onStatus("Status: Permissions error")
            log("Permissions error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        scanning = false
        handler.removeCallbacks(scanTimeout)
        try {
            adapterRef?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
            // Adaptador apagado o sin permisos: nada que detener
        }
    }

    // ─── GATT ─────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun connectGatt(device: BluetoothDevice) {
        postToMain { log("BLE: Connecting GATT...") }
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    postToMain { log("BLE: GATT connected. Discovering services...") }
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    g.close()
                    if (gatt === g) gatt = null
                    postToMain {
                        isConnecting = false
                        callbacks.onStatus("Status: Disconnected")
                        log("BLE: Connection closed (status=$status).")
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val tx = g.getService(NUS_SERVICE_UUID)?.getCharacteristic(NUS_TX_UUID)
            if (status != BluetoothGatt.GATT_SUCCESS || tx == null) {
                postToMain {
                    isConnecting = false
                    callbacks.onStatus("Status: Incompatible device")
                    log("BLE ERROR: UART service not found on $DEVICE_NAME (status=$status).")
                }
                g.disconnect()
                return
            }

            g.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(CCCD_UUID)
            if (cccd != null) {
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

            postToMain {
                isConnecting = false
                callbacks.onStatus("Status: Connected to $DEVICE_NAME")
                log("BLE SUCCESS: Notifications enabled. Listening...")
            }
        }

        // Android 13+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncoming(value)
        }

        // Android 12 y anteriores
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                handleIncoming(characteristic.value ?: return)
            }
        }
    }

    private fun handleIncoming(value: ByteArray) {
        val text = String(value, Charsets.UTF_8)
        text.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                postToMain {
                    log("RX -> $line")

                    if (line.contains("Boton", ignoreCase = true) || line.contains("GIBBOR_PANIC_TRIGGER", ignoreCase = true)) {
                        callbacks.onTrigger()
                        log("🚨 Trigger detectado.")
                    }
                }
            }
    }

    // ─── Desconexión ──────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun disconnect(silent: Boolean) {
        stopScan()

        val g = gatt
        gatt = null
        try {
            g?.disconnect()
            g?.close()
        } catch (_: Exception) {
            // Ya cerrado
        }

        isConnecting = false
        callbacks.onStatus("Status: Disconnected")
        if (!silent) {
            log("Manually disconnected.")
        }
    }
}
