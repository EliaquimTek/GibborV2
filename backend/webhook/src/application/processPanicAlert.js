const { storeEvidence } = require('../infrastructure/evidenceRepository');
const { sendEmergencySMS } = require('../infrastructure/smsGateway');
const { isMediaEvent } = require('../domain/panicAlert');

/**
 * Caso de uso: guarda la evidencia ligada al hash on-chain y, si es un
 * incidente nuevo, avisa a los contactos de emergencia.
 */
async function processPanicAlert(alert) {
  const { evidence_hash, location, user_id, incident_id, event_type, media_type, timestamp, transaction_hash } = alert;

  // Store the evidence (video/audio) linked to the hash
  await storeEvidence({
    evidence_hash,
    location,
    user_id,
    incident_id,
    event_type,
    media_type,
    timestamp,
    transaction_hash,
    received_at: new Date().toISOString()
  });

  // Un hash de audio/video es seguimiento de un incidente ya avisado: no se repite el SMS
  if (isMediaEvent(alert)) return;

  // Send emergency SMS to preconfigured contacts
  await sendEmergencySMS({
    user_id,
    incident_id,
    location,
    evidence_hash,
    timestamp
  });
}

module.exports = {
  processPanicAlert
};
