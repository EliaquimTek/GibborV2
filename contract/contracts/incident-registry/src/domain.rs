use soroban_sdk::{contracterror, contracttype, String};

pub const STATUS_TRIGGERED: u32 = 0;
pub const STATUS_MEDIA_ATTACHED: u32 = 1;

#[derive(Clone)]
#[contracttype]
pub struct Incident {
    pub incident_id: String,
    pub timestamp: i64,
    pub lat_e7: i32,
    pub lon_e7: i32,
    pub initial_hash: String,
    pub status: u32,
}

#[derive(Clone)]
#[contracttype]
pub struct MediaHash {
    pub media_type: String,
    pub media_hash: String,
    pub uploaded_at: i64,
}

#[derive(Clone)]
#[contracttype]
pub enum DataKey {
    Incident(String),
    Media(String),
}

#[contracterror]
#[derive(Copy, Clone, Debug, Eq, PartialEq, PartialOrd, Ord)]
#[repr(u32)]
pub enum Error {
    IncidentAlreadyExists = 1,
    IncidentNotFound = 2,
}
