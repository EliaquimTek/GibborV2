#![cfg(test)]

use crate::{IncidentRegistryContract, IncidentRegistryContractClient};
use soroban_sdk::{Env, String};

#[test]
fn test_create_and_get_incident() {
    let env = Env::default();
    let contract_id = env.register(IncidentRegistryContract, ());
    let client = IncidentRegistryContractClient::new(&env, &contract_id);

    let incident_id  = String::from_str(&env, "inc-001");
    let initial_hash = String::from_str(&env, "abc123hash");

    client.create_incident(&incident_id, &1_000_000_i64, &19_432_000_i32, &-99_133_000_i32, &initial_hash);

    let incident = client.get_incident(&incident_id);
    assert_eq!(incident.incident_id, incident_id);
    assert_eq!(incident.lat_e7, 19_432_000_i32);
    assert_eq!(incident.lon_e7, -99_133_000_i32);
    assert_eq!(incident.status, 0u32); // STATUS_TRIGGERED
}

#[test]
fn test_attach_media_hash() {
    let env = Env::default();
    let contract_id = env.register(IncidentRegistryContract, ());
    let client = IncidentRegistryContractClient::new(&env, &contract_id);

    let incident_id  = String::from_str(&env, "inc-002");
    let initial_hash = String::from_str(&env, "abc123hash");
    let media_type   = String::from_str(&env, "video");
    let media_hash   = String::from_str(&env, "videohash456");

    client.create_incident(&incident_id, &1_000_000_i64, &0_i32, &0_i32, &initial_hash);
    client.attach_media_hash(&incident_id, &media_type, &media_hash, &1_000_001_i64);

    let incident = client.get_incident(&incident_id);
    assert_eq!(incident.status, 1u32); // STATUS_MEDIA_ATTACHED

    let proofs = client.get_media_hashes(&incident_id);
    assert_eq!(proofs.len(), 1);
}

#[test]
fn test_duplicate_incident_fails() {
    let env = Env::default();
    let contract_id = env.register(IncidentRegistryContract, ());
    let client = IncidentRegistryContractClient::new(&env, &contract_id);

    let incident_id  = String::from_str(&env, "inc-003");
    let initial_hash = String::from_str(&env, "abc123hash");

    client.create_incident(&incident_id, &1_000_000_i64, &0_i32, &0_i32, &initial_hash);

    let result = client.try_create_incident(&incident_id, &1_000_000_i64, &0_i32, &0_i32, &initial_hash);
    assert!(result.is_err());
}
