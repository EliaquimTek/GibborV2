# Contrato Soroban — `incident-registry`

Registro inmutable de incidentes de GIBBOR en Stellar.

## Funciones públicas

| Función | Descripción |
|---|---|
| `create_incident(incident_id, timestamp, lat_e7, lon_e7, initial_hash)` | Registra un incidente nuevo. Falla con `IncidentAlreadyExists` si el id ya existe. Emite el evento `created`. |
| `attach_media_hash(incident_id, media_type, media_hash, uploaded_at)` | Agrega el SHA-256 de un archivo de evidencia y marca el incidente como `MEDIA_ATTACHED`. Emite el evento `media`. |
| `get_incident(incident_id)` | Devuelve el incidente o `IncidentNotFound`. |
| `get_media_hashes(incident_id)` | Devuelve la lista de hashes de evidencia. |

## Módulos

- `domain.rs`: `Incident`, `MediaHash`, `DataKey`, `Error` y constantes de estado.
- `storage.rs`: lectura y escritura en el almacenamiento persistente.
- `events.rs`: publicación de eventos (los indexa GoldSky).
- `contract.rs`: punto de entrada `IncidentRegistryContract`.

## Comandos

```bash
cargo test              # pruebas: crear/consultar, adjuntar hash, rechazar duplicados
stellar contract build  # genera target/wasm32v1-none/release/incident_registry.wasm
```
