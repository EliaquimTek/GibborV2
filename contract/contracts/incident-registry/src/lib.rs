#![no_std]

//! GIBBOR — Registro inmutable de incidentes en Soroban.
//!
//! Capas:
//! - `domain`:   entidades, constantes de estado y errores.
//! - `storage`:  acceso al almacenamiento persistente del contrato.
//! - `events`:   publicación de eventos (indexados por GoldSky).
//! - `contract`: punto de entrada público (casos de uso del contrato).

mod contract;
mod domain;
mod events;
mod storage;

pub use contract::{IncidentRegistryContract, IncidentRegistryContractClient};
pub use domain::{DataKey, Error, Incident, MediaHash};
pub use events::{IncidentCreatedTopics, MediaAttachedTopics};

mod test;
