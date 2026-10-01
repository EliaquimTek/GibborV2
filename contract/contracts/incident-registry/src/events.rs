use soroban_sdk::{symbol_short, Env, String, Symbol};

/// Tópicos del evento create_incident: (status, incident_id)
/// GoldSky indexa los campos data como: timestamp, lat_e7, lon_e7, initial_hash
pub struct IncidentCreatedTopics(pub Symbol, pub String);

/// Tópicos del evento attach_media_hash: (status, incident_id)
pub struct MediaAttachedTopics(pub Symbol, pub String);

pub fn publish_incident_created(
    env: &Env,
    incident_id: String,
    timestamp: i64,
    lat_e7: i32,
    lon_e7: i32,
    initial_hash: String,
) {
    env.events().publish(
        (symbol_short!("created"), incident_id),
        (timestamp, lat_e7, lon_e7, initial_hash),
    );
}

pub fn publish_media_attached(
    env: &Env,
    incident_id: String,
    media_type: String,
    media_hash: String,
    uploaded_at: i64,
) {
    env.events().publish(
        (symbol_short!("media"), incident_id),
        (media_type, media_hash, uploaded_at),
    );
}
