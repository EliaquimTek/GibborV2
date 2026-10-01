const { storeEvidence } = require('../infrastructure/evidenceRepository');
const { sendEmergencySMS } = require('../infrastructure/smsGateway');

/**
 * Caso de uso: guarda la evidencia ligada al hash on-chain y avisa a los contactos.
 */
async function processPanicAlert({ evidence_hash, location, user_id, timestamp, transaction_hash }) {
  // Store the evidence (video/audio) linked to the hash
  // In a real implementation, this would retrieve the actual media from storage
  // and store it in a secure database with reference to the blockchain hash
  await storeEvidence({
    evidence_hash,
    location,
    user_id,
    timestamp,
    transaction_hash,
    received_at: new Date().toISOString()
  });

  // Send emergency SMS to preconfigured contacts
  await sendEmergencySMS({
    user_id,
    location,
    evidence_hash,
    timestamp
  });
}

module.exports = {
  processPanicAlert
};
