/**
 * Reglas de dominio de un evento PanicAlert (independientes de Express).
 */

function hasRequiredPanicAlertFields({ evidence_hash, location, user_id }) {
  return !(!evidence_hash || !location || !user_id);
}

function buildEvidenceRecord({ evidence_hash, location, user_id, timestamp, transaction_hash, received_at }) {
  return {
    id: `evidence-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    evidence_hash,
    location,
    user_id,
    timestamp: parseInt(timestamp),
    transaction_hash,
    received_at,
    stored_at: new Date().toISOString(),
    status: 'secured'
  };
}

function buildEmergencyMessage({ user_id, location, evidence_hash, timestamp }) {
  return `EMERGENCY ALERT: User ${user_id} has triggered a panic alert at ${location}. Evidence hash: ${evidence_hash}. Time: ${new Date(timestamp).toISOString()}`;
}

module.exports = {
  hasRequiredPanicAlertFields,
  buildEvidenceRecord,
  buildEmergencyMessage
};
