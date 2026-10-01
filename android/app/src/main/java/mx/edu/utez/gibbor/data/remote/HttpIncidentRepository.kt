package mx.edu.utez.gibbor.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mx.edu.utez.gibbor.domain.model.BackendResult
import mx.edu.utez.gibbor.domain.model.EvidenceResult
import mx.edu.utez.gibbor.domain.model.IncidentDraft
import mx.edu.utez.gibbor.domain.repository.IncidentRepository
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Llamadas HTTP al backend GIBBOR (reemplazo de Crossmint directo).
 * El backend usa la server-side key para crear wallet + transacción.
 */
class HttpIncidentRepository : IncidentRepository {

    private fun readResponseBody(connection: HttpURLConnection): String {
        val stream = try {
            connection.inputStream
        } catch (_: Exception) {
            connection.errorStream
        }

        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    /**
     * POST /api/incident/:incidentId/evidence
     * Ancla el hash del archivo de evidencia en el contrato Soroban.
     */
    override suspend fun sendEvidence(
        backendUrl: String,
        incidentId: String,
        mediaType: String,
        mediaHash: String
    ): EvidenceResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("mediaType", mediaType)
                .put("mediaHash", mediaHash)

            val connection = (URL("$backendUrl/api/incident/$incidentId/evidence")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15000
                readTimeout = 60000
                doInput = true
                doOutput = true
            }

            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }

            val code = connection.responseCode
            val raw  = readResponseBody(connection)
            val json = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }

            val ok = code in 200..299 && json.optBoolean("success", false)
            if (ok) {
                EvidenceResult(success = true, txHash = json.optString("txHash", ""))
            } else {
                EvidenceResult(success = false, error = json.optString("error", "HTTP $code"))
            }
        } catch (e: Exception) {
            EvidenceResult(success = false, exceptionMessage = e.message.toString())
        }
    }

    override suspend fun sendIncident(
        backendUrl: String,
        email: String,
        draft: IncidentDraft
    ): BackendResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("email", email)
            .put("incidentId", draft.incidentId)
            .put("timestamp", draft.timestamp)
            .put("latE7", draft.latE7)
            .put("lonE7", draft.lonE7)
            .put("initialHash", draft.initialHash)

        val connection = (URL("$backendUrl/api/incident").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15000
            readTimeout = 60000  // 60s — el backend puede tardar en crear wallet + tx
            doInput = true
            doOutput = true
        }

        connection.outputStream.use { output ->
            output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
        }

        val raw = readResponseBody(connection)
        val code = connection.responseCode

        val json = try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject().put("raw", raw)
        }

        if (code !in 200..299) {
            val errorMsg = json.optString("error", "HTTP $code")
            BackendResult(
                success = false,
                status = "error",
                txId = "",
                txHash = "",
                explorerLink = "",
                error = errorMsg,
                rawJson = json.toString(2)
            )
        } else {
            BackendResult(
                success = json.optBoolean("success", false),
                status = json.optString("status", "unknown"),
                txId = json.optString("txId", ""),
                txHash = json.optString("txHash", ""),
                explorerLink = json.optString("explorerLink", ""),
                error = json.optString("error", ""),
                rawJson = json.toString(2)
            )
        }
    }

    override suspend fun pollStatus(
        backendUrl: String,
        email: String,
        txId: String,
        maxAttempts: Int,
        delayMs: Long
    ): BackendResult = withContext(Dispatchers.IO) {
        var lastResult: BackendResult? = null

        repeat(maxAttempts) { attempt ->
            delay(delayMs)

            try {
                val encodedEmail = java.net.URLEncoder.encode(email, "UTF-8")
                val encodedTxId = java.net.URLEncoder.encode(txId, "UTF-8")
                val url = "$backendUrl/api/incident/$encodedTxId?email=$encodedEmail"

                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 15000
                    doInput = true
                }

                val raw = readResponseBody(connection)
                val json = try { JSONObject(raw) } catch (_: Exception) { JSONObject().put("raw", raw) }

                lastResult = BackendResult(
                    success = json.optBoolean("success", false),
                    status = json.optString("status", "unknown"),
                    txId = json.optString("txId", txId),
                    txHash = json.optString("txHash", ""),
                    explorerLink = json.optString("explorerLink", ""),
                    error = json.optString("error", ""),
                    rawJson = json.toString(2)
                )

                if (lastResult!!.status in listOf("success", "failed")) {
                    return@withContext lastResult!!
                }
            } catch (e: Exception) {
                lastResult = BackendResult(
                    success = false,
                    status = "polling-error",
                    txId = txId,
                    txHash = "",
                    explorerLink = "",
                    error = "Polling attempt ${attempt + 1}: ${e.message}",
                    rawJson = ""
                )
            }
        }

        lastResult ?: BackendResult(
            success = false,
            status = "timeout",
            txId = txId,
            txHash = "",
            explorerLink = "",
            error = "Polling timeout after $maxAttempts attempts",
            rawJson = ""
        )
    }
}
