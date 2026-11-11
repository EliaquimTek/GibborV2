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
| `android/` | Kotlin + Jetpack Compose, minSdk 24, target 35 | App principal: permisos, BLE con el botón, GPS, grabación (CameraX), llamadas al backend, SMS de emergencia (rama `dev`), pestaña Evento (rama `events`) |
| `firmware/` | Arduino (core esp32 3.3.x), ESP32-C3 | **Nuevo en `DHDE`.** Botón físico: se anuncia por BLE como `GIBBOR_ESP32` y notifica `GIBBOR_PANIC_TRIGGER` al presionar |
| `backend/api/` | Node.js + Express, puerto 3001 | API para la app: crea incidentes y adjunta hashes de evidencia firmando con Crossmint hacia Soroban |
| `backend/webhook/` | Node.js + Express, puerto 3000 | Recibe eventos de GoldSky (firma validada), guarda la evidencia y envía SMS |
| `contract/` | Rust + Soroban SDK | Contrato `incident-registry`: `create_incident`, `attach_media_hash`, `get_incident`, `get_media_hashes`; emite eventos `created` y `media` |
| `docs/redesign/` | Markdown | Briefs de rediseño de la UI (reglas duras, flujo DEMO, UI "pro") |

Arquitectura: **Clean Architecture ligera** en todos los módulos —
`presentation → application/usecase → domain ← infrastructure/data`. El dominio no conoce Express,
Android ni Crossmint.

## 3. Flujo completo de un pánico con el botón físico

```
 [Botón ESP32-C3]                 [App Android]                       [Backend API]        [Stellar/Soroban]     [GoldSky]      [Webhook]
  GPIO9 presionado
  antirrebote + cooldown
  BLE notify TX ───────────────▶ Esp32BleClient.handleIncoming
  "GIBBOR_PANIC_TRIGGER #n"       línea contiene el texto
                                   → onTrigger() → triggerCounter++
                                   → AppScreen LaunchedEffect
                                   → handleHardwareTrigger("ESP32")
                                   GPS + hora → SHA-256
                                   POST /api/incident ─────────────▶ Crossmint firma ─────▶ create_incident
                                   RecordingService (audio+video)                          evento "created" ──▶ indexa ──────▶ POST /goldsky
                                   al detener: SHA-256 del archivo                                                              → SMS contactos
                                   POST /api/incident/:id/evidence ▶ Crossmint firma ─────▶ attach_media_hash
                                                                                          evento "media" ───▶ indexa ──────▶ guarda evidencia
```

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
| Firmware compila (core esp32 3.3.7, ESP32C3 Dev Module, USB CDC On Boot) | ✅ 663 KB (50 % flash), 19 KB RAM (5 %) |
| Flasheo por USB-Serial/JTAG nativo (COM15) | ✅ hash verificado |
| Pruebas de hardware Serial + BLE (`firmware/tests`) | ✅ **28/28** |
| App Android `assembleDebug` | ✅ APK debug generado |
| App Android tests unitarios (`testDebugUnitTest`) | ✅ 8/8 (`EmergencySmsTest` 7, `ExampleUnitTest` 1) |

Detalle de las pruebas de hardware en `firmware/README.md`.

## 7. Observaciones de compatibilidad (pendientes / recomendaciones)

1. **MTU**: conviene que la app llame a `gatt.requestMtu(185)` tras conectar. No es necesario para el
   pánico (ya funciona con 20 bytes), pero sí para leer completas las respuestas de diagnóstico.
2. **Escaneo sin filtro**: `startScan(null, …)` en Android 8.1+ se detiene con la pantalla apagada.
   Un `ScanFilter` por UUID de servicio NUS permitiría escaneo en segundo plano.
3. **Reconexión**: si el botón se aleja y se desconecta, la app no reintenta sola. El firmware vuelve a
   anunciarse en ~2 s y además guarda el último pánico hecho sin conexión durante 60 s para entregarlo
   al reconectar (marcado `(diferido)`).
4. **Android < 12**: el escaneo BLE requiere tener la **ubicación del sistema activada**, además del permiso.
5. **Advertencia de compilación existente**: `Esp32BleClient.kt:214` sobreescribe un miembro deprecado
   sin `@Deprecated` (solo warning).
6. **Pendiente de probar con manos humanas**: presionar físicamente el botón BOOT (GPIO9) con la app en
   un teléfono real. Las pruebas automáticas usan el comando `TRIGGER`, que ejecuta la misma función
   `firePanic()` que el botón.
