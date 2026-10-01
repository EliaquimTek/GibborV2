/**
 * Reglas de dominio del incidente (independientes de Express y Crossmint).
 */

function hasRequiredIncidentFields({ email, incidentId, timestamp, latE7, lonE7, initialHash }) {
  return !(!email || !incidentId || timestamp == null || latE7 == null || lonE7 == null || !initialHash);
}

function buildCreateIncidentTx(contractId, { incidentId, timestamp, latE7, lonE7, initialHash }) {
  return {
    params: {
      transaction: {
        type: "contract-call",
        contractId,
        method: "create_incident",
        args: {
          incident_id: incidentId,
          timestamp: timestamp,
          lat_e7: latE7,
          lon_e7: lonE7,
          initial_hash: initialHash,
        },
      },
      signer: "api-key",
    },
  };
}

/**
 * Extrae los datos relevantes de una respuesta de transacción de Crossmint.
 */
function mapTransaction(txResult, fallbackTxId) {
  const status = txResult.status || "unknown";
  const onChain = txResult.onChain || {};
  const txHash = onChain.hash || onChain.txId || fallbackTxId;
  const explorerLink = onChain.explorerLink || "";
  return { status, txHash, explorerLink };
}

module.exports = { hasRequiredIncidentFields, buildCreateIncidentTx, mapTransaction };
