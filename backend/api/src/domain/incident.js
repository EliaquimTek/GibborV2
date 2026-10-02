/**
 * Reglas de dominio del incidente (independientes de Express y Crossmint).
 */

const MEDIA_TYPES = ["audio", "video"];

function hasRequiredIncidentFields({ email, incidentId, timestamp, latE7, lonE7, initialHash }) {
  return !(!email || !incidentId || timestamp == null || latE7 == null || lonE7 == null || !initialHash);
}

function hasRequiredEvidenceFields({ email, incidentId, mediaType, mediaHash }) {
  return Boolean(email && incidentId && MEDIA_TYPES.includes(mediaType) && mediaHash);
}

/** Llamada al método create_incident del contrato. */
function createIncidentCall({ incidentId, timestamp, latE7, lonE7, initialHash }) {
  return {
    method: "create_incident",
    args: {
      incident_id: incidentId,
      timestamp: timestamp,
      lat_e7: latE7,
      lon_e7: lonE7,
      initial_hash: initialHash,
    },
  };
}

/** Llamada al método attach_media_hash del contrato. */
function attachMediaCall({ incidentId, mediaType, mediaHash, uploadedAt }) {
  return {
    method: "attach_media_hash",
    args: {
      incident_id: incidentId,
      media_type: mediaType,
      media_hash: mediaHash,
      uploaded_at: uploadedAt,
    },
  };
}

/**
 * Extrae los datos relevantes de una respuesta REST de transacción de Crossmint.
 */
function mapTransaction(txResult, fallbackTxId) {
  const status = txResult.status || "unknown";
  const onChain = txResult.onChain || {};
  const txHash = onChain.hash || onChain.txId || fallbackTxId;
  const explorerLink = onChain.explorerLink || "";
  return { status, txHash, explorerLink };
}

module.exports = {
  hasRequiredIncidentFields,
  hasRequiredEvidenceFields,
  createIncidentCall,
  attachMediaCall,
  mapTransaction,
};
