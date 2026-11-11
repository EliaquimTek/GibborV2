# GIBBOR — Contexto del proyecto

> Documento de contexto escrito a partir de la lectura del repositorio `EliaquimTek/GibborV2`
> (rama `dev`, commit `390121b`) y de pruebas con el hardware real. Rama de trabajo: `DHDE`.

## 1. ¿Qué es?

GIBBOR es un **botón de pánico que crea evidencia inalterable**. Cuando alguien está en peligro
presiona un botón (físico, en la app o remoto) y el sistema:

1. Toma **ubicación GPS + hora**, calcula su **SHA-256** y registra el incidente en la blockchain
   **Stellar** mediante un contrato **Soroban**.
2. Empieza a **grabar audio y video** automáticamente con el teléfono (cámara trasera para el botón físico).
3. Al detener la grabación calcula el SHA-256 del archivo y **ancla ese hash on-chain**.
   El video nunca sale del teléfono: solo su huella de 64 caracteres.
4. **GoldSky** indexa los eventos del contrato y los expone por SQL / webhook, y el backend de webhook
   avisa por **SMS** a los contactos de emergencia.
5. **Crossmint** crea y administra la wallet Stellar del usuario (entra con su correo, sin manejar llaves).

Si alguien compara después el hash del video con el registrado en Stellar y coincide, queda demostrado
que el archivo no fue alterado. Está pensado para campus universitarios (es un proyecto de la UTEZ,
paquete `mx.edu.utez.gibbor`), programas municipales de seguridad, periodistas y ONG.

## 2. Componentes del repositorio

| Carpeta | Tecnología | Qué hace |
|---|---|---|
| `android/` | Kotlin + Jetpack Compose, minSdk 24, target 35 | App principal: permisos, protección en segundo plano (`ProtectionService`), BLE con el botón, GPS, grabación (CameraX), llamadas al backend, SMS de emergencia (rama `dev`), pestaña Evento (rama `events`) |
| `firmware/` | Arduino (core esp32 3.3.x), ESP32-C3 | **Nuevo en `DHDE`.** Botón físico: se anuncia por BLE como `GIBBOR_ESP32` y notifica `GIBBOR_PANIC_TRIGGER` al presionar |
| `backend/api/` | Node.js + Express, puerto 3001 | API para la app: crea incidentes y adjunta hashes de evidencia firmando con Crossmint hacia Soroban |
| `backend/webhook/` | Node.js + Express, puerto 3000 | Recibe eventos de GoldSky (firma validada), guarda la evidencia y envía SMS |
| `contract/` | Rust + Soroban SDK | Contrato `incident-registry`: `create_incident`, `attach_media_hash`, `get_incident`, `get_media_hashes`; emite eventos `created` y `media` |
| `docs/redesign/` | Markdown | Briefs de rediseño de la UI (reglas duras, flujo DEMO, UI "pro") |

Arquitectura: **Clean Architecture ligera** en todos los módulos —
`presentation → application/usecase → domain ← infrastructure/data`. El dominio no conoce Express,
Android ni Crossmint.

## 3. Flujo completo de un pánico con el botón físico

Desde la rama `DHDE` todo el flujo corre en **`ProtectionService`** (servicio en primer plano), así que
funciona con la app cerrada y la pantalla apagada. La UI solo refleja su estado.

```
 [Botón ESP32-C3]            [ProtectionService en el teléfono]                [Backend API]      [Stellar/Soroban]   [GoldSky → Webhook]
  GPIO9 presionado
  antirrebote + cooldown
  BLE notify TX ──────────▶ Esp32BleClient → PanicMessageParser
  "GIBBOR_PANIC_TRIGGER #n"  → PanicFlow.trigger("ESP32")
                             1. GPS + hora → SHA-256 (borrador)
                             2. graba audio + video YA (cámara trasera)
                             3. SMS con ubicación a contactos
                             4. POST /api/incident ───────────────────────▶ Crossmint ──▶ create_incident ──▶ evento "created"
                                (+ polling hasta confirmar)
                             5. al detener (notificación, app o 10 min):
                                SHA-256 de cada archivo
                                POST /api/incident/:id/evidence ──────────▶ Crossmint ──▶ attach_media_hash ─▶ evento "media"
```

Si el botón pierde la conexión, el servicio lo vuelve a buscar solo (escaneo filtrado por servicio,
espera creciente de 2 → 30 s). Un pánico presionado mientras tanto queda guardado en el ESP32 hasta 5 min
y llega como `GIBBOR_PANIC_TRIGGER #n (diferido)` al reconectar.

## 4. Contrato BLE entre el ESP32 y la app

Definido por `android/.../data/bluetooth/Esp32BleClient.kt` e implementado en
`firmware/gibbor_esp32c3/gibbor_esp32c3.ino`.

| Elemento | Valor |
|---|---|
| Nombre anunciado | `GIBBOR_ESP32` (la app escanea 10 s buscando exactamente ese nombre) |
| Servicio | Nordic UART `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` |
| TX (ESP→app) | `6E400003-…` NOTIFY, con CCCD `0x2902` (la app lo escribe para activar notificaciones) |
| RX (app→ESP) | `6E400002-…` WRITE / WRITE_NR (la app actual no escribe, sirve para diagnóstico) |
| Mensaje de pánico | `GIBBOR_PANIC_TRIGGER #<n>\n` — la app dispara si la línea contiene `GIBBOR_PANIC_TRIGGER` o `Boton` |
| Emparejamiento | No requiere (conexión GATT directa sin bonding) |

## 5. Estado encontrado y cambios en la rama `DHDE`

**Lo que había:**
- El repositorio **no contenía el firmware del ESP32**.
- La placa conectada (ESP32-C3 QFN32 rev 0.4, 4 MB flash, MAC base `14:63:93:91:8e:84`) traía un
  sketch de prueba compilado con core 3.3.8 que se anunciaba como **`ESP32C3-Test`** y enviaba
  `Boton presionado #n`. Con ese nombre **la app nunca lo encontraba** (busca `GIBBOR_ESP32`).
- Se respaldó la flash original completa en `C:\Gibborrim\Gibbor\firmware_backup\esp32c3_original_4MB.bin`
  (fuera del repo). Para restaurarla:
  `esptool --port COM15 write-flash 0 esp32c3_original_4MB.bin`.

**Lo que se agregó:**
- `firmware/gibbor_esp32c3/` — firmware nuevo, compatible con la app (ver `firmware/README.md`).
- `firmware/tests/` — pruebas automáticas de hardware por Serial y BLE que replican el comportamiento
  de `Esp32BleClient.kt`.
- Corrección de compatibilidad: la app no pide un MTU mayor, por lo que en Android < 14 cada
  notificación lleva como máximo 20 bytes. El firmware trocea según el MTU real y el mensaje de pánico
  siempre empieza por `GIBBOR_PANIC_TRIGGER` (20 bytes exactos), así que el primer trozo basta.

## 6. Resultados de las pruebas (2026-11-10)

| Prueba | Resultado |
|---|---|
| Firmware compila (core esp32 3.3.7, ESP32C3 Dev Module, USB CDC On Boot) | ✅ 664 KB (50 % flash), 19 KB RAM (5 %) |
| Flasheo por USB-Serial/JTAG nativo (COM15) | ✅ hash verificado |
| Pruebas de hardware Serial + BLE + reconexión (`firmware/tests`) | ✅ **33/33** |
| App Android `assembleDebug` | ✅ APK debug generado |
| App Android tests unitarios (`testDebugUnitTest`) | ✅ 18/18 (SMS 7, parser BLE 6, reconexión 4, ejemplo 1) |
| Android Lint en el código nuevo | ✅ sin hallazgos (los 8 errores restantes ya existían: versión de Fragment, `dispatchKeyEvent`, `uses-feature` de cámara) |
| App en un teléfono real | ⏳ **pendiente**: no había un Android conectado por adb |

Detalle de las pruebas de hardware en `firmware/README.md`.

## 7. Protección en segundo plano (rama `DHDE`)

| Pieza | Archivo | Qué hace |
|---|---|---|
| Servicio en primer plano | `service/ProtectionService.kt` | Tipos connectedDevice, location, camera y microphone. Mantiene el BLE, ejecuta el pánico, notificación fija con "Desactivar" / "Detener grabación", wake lock durante la emergencia, reacciona a Bluetooth apagado/encendido |
| Flujo de pánico | `service/PanicFlow.kt` | Lo que antes estaba en `AppScreen.createAndSendIncident`, ahora sin UI. La grabación arranca antes de registrar en blockchain y se detiene sola a los 10 min |
| Estado compartido | `service/ProtectionHub.kt` | `StateFlow` que la Activity observa (estado BLE, log, incidente, grabación) |
| BLE | `data/bluetooth/Esp32BleClient.kt` | `ScanFilter` por UUID NUS, `requestMtu(185)`, reconexión automática ([ReconnectBackoff](../android/app/src/main/java/mx/edu/utez/gibbor/data/bluetooth/ReconnectBackoff.kt)), reactivación de notificaciones |
| Video | `data/recording/VideoEvidenceRecorder.kt` | CameraX atado al ciclo de vida **del servicio** (antes `ProcessLifecycleOwner`, que apagaba la cámara al salir de la app). Reemplaza a `RecordingService` |
| Sesión persistente | `data/session/ProtectionPrefs.kt` | Correo, URL del backend y "protección activa" sobreviven al cierre de la app |

Cómo se usa: iniciar sesión → Ajustes → **Conectar**. Desde ese momento el botón funciona con la app
cerrada. **Desconectar** (o "Desactivar" en la notificación) apaga la protección.

### Limitaciones de Android (no se pueden evitar desde la app)

1. **Hay que abrir la app una vez** después de reiniciar el teléfono: Android 14+ prohíbe arrancar
   servicios con cámara o micrófono desde el arranque o desde segundo plano.
2. Si Android mata el servicio y lo reinicia por su cuenta, arranca en **modo limitado** (solo BLE). La
   notificación lo indica y, al abrir la app, se restaura el acceso completo.
3. Algunos fabricantes (Xiaomi, Huawei, Samsung…) cierran servicios agresivamente. La app pide una sola
   vez quitar la optimización de batería; en esas marcas puede hacer falta permitir el "inicio automático".
4. En Android < 12 el escaneo BLE requiere tener la **ubicación del sistema activada**.

### Pendiente

1. Instalar el APK en un teléfono real y probar: app cerrada + pantalla apagada → presionar el botón
   físico (BOOT/GPIO9) → verificar SMS, grabación, registro on-chain y la notificación.
2. Si se necesita que funcione incluso tras reiniciar sin abrir la app, evaluar
   `CompanionDeviceManager` (Android lo despierta cuando el botón está cerca).
3. Advertencia de compilación existente: `Esp32BleClient.kt` sobreescribe un miembro deprecado (solo warning).
