package mx.edu.utez.gibbor.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Conexión SPP con el botón físico ESP32 y lectura de triggers de pánico.
 *
 * Toda notificación hacia la UI se entrega a través de [postToMain]
 * (equivalente a runOnUiThread), salvo en [disconnect], que se ejecuta
 * en el hilo que la invoca.
 */
class Esp32BluetoothClient(
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

        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @Volatile private var bluetoothSocket: BluetoothSocket? = null
    @Volatile private var readThread: Thread? = null
    @Volatile var isConnecting = false
        private set

    private fun log(message: String) = callbacks.onLog(message)

    @SuppressLint("MissingPermission")
    fun findPairedDevice(adapter: BluetoothAdapter): BluetoothDevice? {
        return adapter.bondedDevices.firstOrNull { it.name == DEVICE_NAME }
    }

    @SuppressLint("MissingPermission")
    fun connect(adapter: BluetoothAdapter, device: BluetoothDevice) {
        // Primero limpiar cualquier conexión anterior
        disconnect(silent = true)

        isConnecting = true
        log("BT INIT: Target=${device.name}, MAC=${device.address}, BondState=${device.bondState}")

        readThread = thread(start = true) {
            try {
                // 1) Verificar que el dispositivo sí esté emparejado (BOND_BONDED = 12)
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    postToMain {
                        isConnecting = false
                        callbacks.onStatus("Status: Not paired")
                        log("BT ERROR: Device NOT paired (bondState=${device.bondState}). Aborting.")
                    }
                    return@thread
                }

                // 2) Siempre cancelar discovery antes de conectar y esperar
                adapter.cancelDiscovery()
                postToMain { log("BT: Discovery cancelled. Purging stack...") }
                Thread.sleep(1500) // Cooldown generoso para que el stack BT se estabilice

                var socket: BluetoothSocket? = null
                var connectSuccess = false

                // ─── Intento 1: SPP Inseguro (más compatible con ESP32) ───
                try {
                    postToMain { log("BT [Intento 1]: SPP Inseguro (createInsecureRfcommSocketToServiceRecord)") }
                    socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    postToMain { log("BT [Intento 1]: Socket Creado. Iniciando connect()...") }
                    socket.connect()
                    connectSuccess = true
                } catch (e: Exception) {
                    val err = e.message ?: e.javaClass.simpleName
                    postToMain { log("BT [Intento 1] Falló: $err") }
                    try { socket?.close() } catch (_: Exception) {}
                    socket = null
                }

                // ─── Intento 2: Reflection Fallback (Puerto 1) ───
                if (!connectSuccess) {
                    Thread.sleep(2000)
                    adapter.cancelDiscovery() // Re-cancelar por seguridad
                    try {
                        postToMain { log("BT [Intento 2]: Fallback Reflection (createRfcommSocket, canal 1)") }
                        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                        socket = method.invoke(device, 1) as BluetoothSocket
                        postToMain { log("BT [Intento 2]: Socket Creado. Iniciando connect()...") }
                        socket.connect()
                        connectSuccess = true
                    } catch (e: Exception) {
                        val err = e.message ?: e.javaClass.simpleName
                        postToMain { log("BT [Intento 2] Falló: $err") }
                        try { socket?.close() } catch (_: Exception) {}
                        socket = null
                    }
                }

                // ─── Intento 3: SPP Seguro Estándar ───
                if (!connectSuccess) {
                    Thread.sleep(2000)
                    adapter.cancelDiscovery()
                    try {
                        postToMain { log("BT [Intento 3]: SPP Seguro (createRfcommSocketToServiceRecord)") }
                        socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                        postToMain { log("BT [Intento 3]: Socket Creado. Iniciando connect()...") }
                        socket.connect()
                        connectSuccess = true
                    } catch (e: Exception) {
                        val err = e.message ?: e.javaClass.simpleName
                        postToMain { log("BT [Intento 3] Falló: $err") }
                        try { socket?.close() } catch (_: Exception) {}
                        socket = null
                    }
                }

                // ─── Intento 4: Reflection canales 2-3 ───
                if (!connectSuccess) {
                    for (channel in 2..3) {
                        Thread.sleep(1500)
                        adapter.cancelDiscovery()
                        try {
                            postToMain { log("BT [Intento 4.$channel]: Reflection canal $channel") }
                            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                            socket = method.invoke(device, channel) as BluetoothSocket
                            socket.connect()
                            connectSuccess = true
                            break
                        } catch (e: Exception) {
                            val err = e.message ?: e.javaClass.simpleName
                            postToMain { log("BT [Intento 4.$channel] Falló: $err") }
                            try { socket?.close() } catch (_: Exception) {}
                            socket = null
                        }
                    }
                }

                // ─── Evaluar Resultado ───
                if (!connectSuccess || socket == null) {
                    postToMain {
                        isConnecting = false
                        callbacks.onStatus("Status: BT critical failure")
                        log("BT FATAL: All 3 methods exhausted. Could not open RFCOMM channel.")
                    }
                    return@thread
                }

                bluetoothSocket = socket

                postToMain {
                    isConnecting = false
                    callbacks.onStatus("Status: Connected to ${device.name}")
                    log("BT SUCCESS: SPP channel open. Listening...")
                }

                // Arrancar el reader solo si hubo conexión exitosa
                listenForMessages(socket)
            } catch (e: SecurityException) {
                postToMain {
                    isConnecting = false
                    callbacks.onStatus("Status: Permissions error")
                    log("Permissions error: ${e.message}")
                }
            } catch (e: IOException) {
                postToMain {
                    isConnecting = false
                    callbacks.onStatus("Status: Connection error")
                    log("Could not connect: ${e.message}")
                }
                safeCloseSocket()
            } catch (e: Exception) {
                postToMain {
                    isConnecting = false
                    callbacks.onStatus("Status: Unexpected error")
                    log("Unexpected error: ${e.message}")
                }
                safeCloseSocket()
            }
        }
    }

    private fun listenForMessages(socket: BluetoothSocket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream))

            // Loop se detiene si se interrumpe el hilo o la capa base BT corta conexión
            while (!Thread.currentThread().isInterrupted) {
                // readLine bloqueará hasta recibir '/n'.
                // Si retorna null o lanza IOException, se cayó la conexión.
                val line = reader.readLine() ?: break

                postToMain {
                    log("RX -> $line")

                    if (line.contains("Boton", ignoreCase = true) || line.contains("GIBBOR_PANIC_TRIGGER", ignoreCase = true)) {
                        callbacks.onTrigger()
                        log("🚨 Trigger detectado.")
                    }
                }
            }

            postToMain {
                callbacks.onStatus("Status: Disconnected")
                log("BT: Connection closed by remote device.")
            }
        } catch (e: IOException) {
            postToMain {
                callbacks.onStatus("Status: Disconnected")
                log("BT: Read stopped (socket broken or closed).")
            }
        } finally {
            safeCloseSocket()
            postToMain {
                isConnecting = false
            }
        }
    }

    /**
     * Desconexión limpia — cierra streams, socket, y espera a que el hilo muera.
     */
    fun disconnect(silent: Boolean) {
        val thread = readThread
        readThread = null

        // Cerrar socket primero para desbloquear el readLine()
        safeCloseSocket()

        // Esperar a que el hilo termine (max 2s)
        thread?.let {
            it.interrupt()
            try {
                it.join(2000)
            } catch (_: InterruptedException) {
                // OK
            }
        }

        isConnecting = false
        callbacks.onStatus("Status: Disconnected")
        if (!silent) {
            log("Manually disconnected.")
        }
    }

    private fun safeCloseSocket() {
        val socket = bluetoothSocket
        bluetoothSocket = null

        if (socket == null) return

        try {
            // Cerrar streams explícitamente antes del socket
            try { socket.inputStream?.close() } catch (_: IOException) {}
            try { socket.outputStream?.close() } catch (_: IOException) {}
            socket.close()
        } catch (_: IOException) {
            // Ya cerrado
        }
    }
}
