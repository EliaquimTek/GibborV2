# GIBBOR — Botón de pánico con evidencia en blockchain

Proyecto que al presionar el botón de pánico (en la app o en el botón físico ESP32):

1. Se calcula un hash SHA-256 de la ubicación GPS + hora y se registra en Stellar mediante el contrato Soroban.
2. El teléfono empieza a grabar audio y video automáticamente.
3. Al detener la grabación, el SHA-256 del archivo se ancla on-chain. El archivo se queda en el teléfono.
4. GoldSky indexa los eventos para consultarlos con SQL; Crossmint crea la wallet sin que el usuario maneje llaves.

## Estructura (Clean Architecture ligera / modular)

```
GIBBOR-Refactor/
├── android/                     App Android (Kotlin + Compose) — paquete mx.edu.utez.gibbor
│   └── app/src/main/java/mx/edu/utez/gibbor/
│       ├── core/util/           HashUtils (SHA-256 de texto y archivos)
│       ├── domain/
│       │   ├── model/           IncidentDraft, BackendResult, EvidenceResult
│       │   ├── repository/      IncidentRepository (interfaz)
│       │   └── usecase/         BuildIncidentDraftUseCase
│       ├── data/
│       │   ├── remote/          HttpIncidentRepository (backend GIBBOR)
│       │   ├── bluetooth/       Esp32BleClient (BLE / Nordic UART con el botón ESP32-C3)
│       │   ├── location/        LocationDataSource (Fused Location)
│       │   └── recording/       AudioEvidenceRecorder, RecordingService (CameraX)
│       └── presentation/
│           ├── MainActivity     Permisos, ciclo de vida y orquestación
│           ├── MainScreenController  Estado/acciones que consume la UI
│           └── ui/              screen/AppScreen, components/, theme/
├── backend/
│   ├── api/                     API para la app (Crossmint → Soroban), puerto 3001
│   │   └── src/ config · domain · application · infrastructure/crossmint · presentation/http
│   └── webhook/                 Receptor de eventos GoldSky (PanicAlert), puerto 3000
│       ├── goldsky.yaml
│       └── src/ config · domain · application · infrastructure · presentation/http · shared
└── contract/                    Contrato Soroban (Rust)
    └── contracts/incident-registry/src/
        domain.rs · storage.rs · events.rs · contract.rs · lib.rs · test.rs
```

Regla de dependencias: `presentation → application/usecase → domain ← infrastructure/data`.
El dominio no conoce Express, Android ni Crossmint.

## Cómo correrlo

### Contrato (Rust / Soroban)
```bash
cd contract
cargo test
stellar contract build
```

### Backend API (para la app)
```bash
cd backend/api
npm install
npm start
```
Requiere `backend/api/.env` (ver `.env.example`) con una `CROSSMINT_SERVER_API_KEY` real; sin ella el servidor se detiene al iniciar.

### Backend webhook (GoldSky)
```bash
cd backend/webhook
npm install
npm start
```

### App Android
Abrir `android/` en Android Studio, o bien:
```bash
cd android
./gradlew assembleDebug
```
`android/local.properties` admite `CROSSMINT_API_KEY` y `BACKEND_URL` (por defecto `http://10.0.2.2:3001`).

## Por qué cada tecnología

- **Stellar + Soroban**: blockchain pública que confirma en 3–5 s. El contrato guarda cuándo, dónde y el hash de la evidencia; nadie puede alterar ese registro.
- **GoldSky**: indexa en tiempo real los eventos del contrato y los expone con SQL, para que abogados o un juez puedan consultar incidentes sin saber de blockchain.
- **SHA-256**: el video nunca sale del teléfono; on-chain solo va su huella de 64 caracteres. Si el hash coincide, el archivo no fue alterado.
- **Crossmint**: el usuario entra con su correo institucional y Crossmint crea y administra la wallet de Stellar de forma invisible.

## Para quién es

Campus universitarios, programas municipales de seguridad, periodistas, ONG de derechos humanos y cualquier persona que necesite crear evidencia creíble e inalterable con un solo toque.
