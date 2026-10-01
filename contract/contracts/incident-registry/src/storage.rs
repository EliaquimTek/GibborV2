use soroban_sdk::{Env, String, Vec};

use crate::domain::{DataKey, Incident, MediaHash};

pub fn has_incident(env: &Env, incident_id: &String) -> bool {
    env.storage()
        .persistent()
        .has(&DataKey::Incident(incident_id.clone()))
}

pub fn get_incident(env: &Env, incident_id: &String) -> Option<Incident> {
    env.storage()
        .persistent()
        .get(&DataKey::Incident(incident_id.clone()))
}

pub fn set_incident(env: &Env, incident_id: &String, incident: &Incident) {
    env.storage()
        .persistent()
        .set(&DataKey::Incident(incident_id.clone()), incident);
}

pub fn get_media(env: &Env, incident_id: &String) -> Vec<MediaHash> {
    env.storage()
        .persistent()
        .get(&DataKey::Media(incident_id.clone()))
        .unwrap_or(Vec::new(env))
}

pub fn set_media(env: &Env, incident_id: &String, media: &Vec<MediaHash>) {
    env.storage()
        .persistent()
        .set(&DataKey::Media(incident_id.clone()), media);
}
