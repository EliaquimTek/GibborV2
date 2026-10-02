package mx.edu.utez.gibbor.presentation.ui.components

/**
 * Traducciones de solo lectura del estado técnico existente (statusText, onChainStatus,
 * incidentPreview) a estados de UI. No alteran la lógica: solo interpretan los textos.
 */

enum class DeviceConnection { Connected, Connecting, Disconnected, Failed }

fun deviceConnection(statusText: String, isConnecting: Boolean): DeviceConnection = when {
    isConnecting || statusText.contains("Scanning", ignoreCase = true) -> DeviceConnection.Connecting
    statusText.contains("Disconnected", ignoreCase = true) -> DeviceConnection.Disconnected
    statusText.contains("Connected", ignoreCase = true) ||
        statusText.contains("Conectado", ignoreCase = true) -> DeviceConnection.Connected
    statusText.contains("failed", ignoreCase = true) ||
        statusText.contains("error", ignoreCase = true) ||
        statusText.contains("Not found", ignoreCase = true) ||
        statusText.contains("Incompatible", ignoreCase = true) ||
        statusText.contains("not support", ignoreCase = true) -> DeviceConnection.Failed
    else -> DeviceConnection.Disconnected
}

fun DeviceConnection.label(): String = when (this) {
    DeviceConnection.Connected -> "Conectado"
    DeviceConnection.Connecting -> "Conectando…"
    DeviceConnection.Disconnected -> "Desconectado"
    DeviceConnection.Failed -> "No se encontró"
}

fun DeviceConnection.tone(): StatusTone = when (this) {
    DeviceConnection.Connected -> StatusTone.Ok
    DeviceConnection.Connecting -> StatusTone.Pending
    DeviceConnection.Disconnected -> StatusTone.Neutral
    DeviceConnection.Failed -> StatusTone.Error
}

enum class ChainState { None, Pending, Success, Error }

fun chainState(onChainStatus: String): ChainState = when {
    onChainStatus.isBlank() -> ChainState.None
    onChainStatus.startsWith("✅") -> ChainState.Success
    onChainStatus.startsWith("❌") ||
        onChainStatus.startsWith("Error", ignoreCase = true) -> ChainState.Error
    else -> ChainState.Pending
}

fun ChainState.tone(): StatusTone = when (this) {
    ChainState.None -> StatusTone.Neutral
    ChainState.Pending -> StatusTone.Pending
    ChainState.Success -> StatusTone.Ok
    ChainState.Error -> StatusTone.Error
}

/** Líneas `clave: valor` / `clave=valor` sin emojis, para "Detalles técnicos". */
fun technicalLines(incidentPreview: String, onChainStatus: String): List<Pair<String, String>> {
    val incident = if (incidentPreview == "Ninguno") emptyList() else incidentPreview.lines()
        .filter { it.contains('=') }
        .map { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    val chain = onChainStatus
        .replace("✅", "").replace("❌", "").replace("⏳", "")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            if (line.contains(':')) line.substringBefore(':').trim() to line.substringAfter(':').trim()
            else "estado" to line
        }
    return incident + chain
}

/** Valor de una línea `clave=valor` del preview del incidente. */
fun incidentField(incidentPreview: String, key: String): String? =
    incidentPreview.lines()
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter('=')
