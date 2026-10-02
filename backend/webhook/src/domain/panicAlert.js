/**
 * Reglas de dominio de los eventos del contrato GIBBOR (independientes de Express).
 *
 * El contrato emite dos eventos, que GoldSky reenvía a este webhook:
 *   - "created": incidente nuevo  → evidence_hash = initial_hash, trae location
 *   - "media":   hash de audio/video anclado → evidence_hash = media_hash, trae media_type
 */

const EVENT_CREATED = 'created';
const EVENT_MEDIA = 'media';

/**
 * Convierte el timestamp a milisegundos. Acepta número o texto ("1700000000"),
 * en segundos (como lo guarda el contrato) o en milisegundos.
 * Devuelve null si no es un número válido.
 */
function normalizeTimestampMs(timestamp) {
  if (timestamp === null || timestamp === undefined || timestamp === '') return null;
  const value = Number(timestamp);
  if (!Number.isFinite(value)) return null;
  return value < 1e12 ? value * 1000 : value;
}

function toIsoTime(timestamp) {
  const ms = normalizeTimestampMs(timestamp);
  return ms === null ? 'unknown' : new Date(ms).toISOString();
}

function isMediaEvent({ event_type }) {
  return event_type === EVENT_MEDIA;
}

/** Quién/qué disparó la alerta: user_id (formato anterior) o incident_id (evento del contrato). */
function subjectOf({ user_id, incident_id }) {
  return user_id || incident_id;
}

function hasRequiredPanicAlertFields(alert) {
  if (!alert.evidence_hash || !subjectOf(alert)) return false;
  // Los eventos "media" no traen ubicación; los de incidente sí la necesitan
  return isMediaEvent(alert) || Boolean(alert.location);
}

function buildEvidenceRecord({ evidence_hash, location, user_id, incident_id, event_type, media_type, timestamp, transaction_hash, received_at }) {
  const ms = normalizeTimestampMs(timestamp);
  return {
    id: `evidence-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    event_type: event_type || EVENT_CREATED,
    incident_id,
    evidence_hash,
    media_type,
    location,
    user_id,
    timestamp: ms === null ? null : Math.floor(ms / 1000),
    transaction_hash,
    received_at,
    stored_at: new Date().toISOString(),
    status: 'secured'
  };
}

function buildEmergencyMessage({ user_id, incident_id, location, evidence_hash, timestamp }) {
  const subject = user_id ? `User ${user_id}` : `Incident ${incident_id}`;
  return `EMERGENCY ALERT: ${subject} has triggered a panic alert at ${location}. Evidence hash: ${evidence_hash}. Time: ${toIsoTime(timestamp)}`;
}

module.exports = {
  EVENT_CREATED,
  EVENT_MEDIA,
  normalizeTimestampMs,
  toIsoTime,
  isMediaEvent,
  subjectOf,
  hasRequiredPanicAlertFields,
  buildEvidenceRecord,
  buildEmergencyMessage
};
