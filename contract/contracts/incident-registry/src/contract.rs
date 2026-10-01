use soroban_sdk::{contract, contractimpl, Env, String, Vec};

use crate::domain::{Error, Incident, MediaHash, STATUS_MEDIA_ATTACHED, STATUS_TRIGGERED};
use crate::{events, storage};

#[contract]
pub struct IncidentRegistryContract;

#[contractimpl]
impl IncidentRegistryContract {
    pub fn create_incident(
        env: Env,
        incident_id: String,
        timestamp: i64,
        lat_e7: i32,
        lon_e7: i32,
        initial_hash: String,
    ) -> Result<(), Error> {
        if storage::has_incident(&env, &incident_id) {
            return Err(Error::IncidentAlreadyExists);
        }

        let incident = Incident {
            incident_id: incident_id.clone(),
            timestamp,
            lat_e7,
            lon_e7,
            initial_hash: initial_hash.clone(),
            status: STATUS_TRIGGERED,
        };

        storage::set_incident(&env, &incident_id, &incident);
        storage::set_media(&env, &incident_id, &Vec::<MediaHash>::new(&env));

        events::publish_incident_created(&env, incident_id, timestamp, lat_e7, lon_e7, initial_hash);

        Ok(())
    }

    pub fn attach_media_hash(
        env: Env,
        incident_id: String,
        media_type: String,
        media_hash: String,
        uploaded_at: i64,
    ) -> Result<(), Error> {
        let mut incident =
            storage::get_incident(&env, &incident_id).ok_or(Error::IncidentNotFound)?;

        let mut proofs = storage::get_media(&env, &incident_id);
        proofs.push_back(MediaHash {
            media_type: media_type.clone(),
            media_hash: media_hash.clone(),
            uploaded_at,
        });
        storage::set_media(&env, &incident_id, &proofs);

        incident.status = STATUS_MEDIA_ATTACHED;
        storage::set_incident(&env, &incident_id, &incident);

        events::publish_media_attached(&env, incident_id, media_type, media_hash, uploaded_at);

        Ok(())
    }

    pub fn get_incident(env: Env, incident_id: String) -> Result<Incident, Error> {
        storage::get_incident(&env, &incident_id).ok_or(Error::IncidentNotFound)
    }

    pub fn get_media_hashes(env: Env, incident_id: String) -> Result<Vec<MediaHash>, Error> {
        if !storage::has_incident(&env, &incident_id) {
            return Err(Error::IncidentNotFound);
        }

        Ok(storage::get_media(&env, &incident_id))
    }
}
