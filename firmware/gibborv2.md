# GibborV2 — configuración de la placa B (protoboard)

Segunda ESP32-C3 del proyecto. **Reemplaza a la placa A** (la de la carcasa dorada, MAC `14:63:93:91:8e:86`,
con el pad de GPIO4 en corto). Las dos llevan el mismo firmware y se anuncian como `GIBBOR_ESP32`; la
app se conecta a la que encuentre.

## Identidad

| | Valor |
|---|---|
| Placa | ESP32-C3 SuperMini V1601 (rev v0.4, 4 MB flash XMC) |
| MAC Wi-Fi / base | `ac:a7:04:d1:5c:cc` (el BLE/`INFO` reporta `ac:a7:04:d1:5c:ce`) |
| Puerto en esta PC | **COM12** (USB-Serial/JTAG nativo; la placa A era COM15) |
| Firmware | `gibbor_esp32c3` **1.2.0** |
| Respaldo del firmware de fábrica | `C:\Gibborrim\Gibbor\firmware_backup\esp32c3_B_aca704d15ccc_original_4MB.bin` |

## Cableado actual (fotos del 2026-11-12 + medición con `SCAN`)

Pines de la SuperMini del lado del USB, empezando por el más cercano al conector:
`5V · G · 3.3 · 4 · 3 · 2 · 1 · 0`.

| Elemento | Conexión |
|---|---|
| Cable rojo | pin **5V** → riel **+** superior ⚠️ |
| Cable azul | pin **G** → riel **−** inferior |
| Cable naranja | pin **GPIO3** → nodo del botón (columna 17 del protoboard) |
| Botón rojo (arcade, microswitch) | una pata al riel **+**, la otra al nodo de GPIO3 |
| Resistencia (verde) | nodo GPIO3 → GND: **pull-down** |
| Resistencia (azul) + LED amarillo | nodo GPIO3 → LED → GND: se enciende al presionar (no lo controla el firmware) |

Medición `SCAN` en reposo: `GPIO3 up=0 down=0` (lo jala a GND la pull-down externa), los demás pines libres.
Es decir, el botón es **activo en ALTO**: GPIO3 está en 0 V y sube al presionar.

### ⚠️ Riesgo conocido: el riel + está a 5 V

Con el cable rojo en **5V**, cada pulsación mete 5 V directo a GPIO3. La ESP32-C3 aguanta como máximo
~3.6 V en sus pines (los diodos de protección conducen y suben el riel de 3.3 V), así que puede dañar el
pin o el chip con el uso. **Ningún cambio de firmware o de la app puede bajar ese voltaje**: el firmware
solo lee el pin.

Decisión del usuario (2026-11-12): **se deja el cableado así**, bajo su riesgo, y el firmware lo lee tal
cual (GPIO3 activo en alto). Si la placa empieza a reiniciarse al presionar (`reset=BROWNOUT`/`POWERON`
en `INFO`) o GPIO3 deja de cambiar en `STATUS`, el pin ya se dañó.

**Corrección recomendada (un solo cable):** mover el cable rojo del pin 5V (el más cercano al USB) al
pin **3.3** (el tercero, después de G). Lo demás se queda igual y no hay que reflashear.

## Cableado seguro (cualquiera de los dos sirve, sin reflashear)

| Opción | Conexión | Polaridad |
|---|---|---|
| A (la actual, corregida) | botón entre **3.3 V** y GPIO3, pull-down ~10 kΩ de GPIO3 a GND | activo en ALTO |
| B (la más simple) | botón entre GPIO3 (o GPIO4/GPIO10) y **G**, sin resistencias | activo en BAJO (pull-up interno) |

El firmware 1.2.0 **detecta la polaridad al arrancar**: si el pin lee GND aun con el pull-up interno
activo, asume una pull-down externa y trata el botón como activo en alto. Por eso no hay que
presionar el botón mientras la placa arranca. Para forzarla: `-DBUTTON_ACTIVE_HIGH=1` (alto) o `=0` (bajo).

Botones que escucha el firmware: GPIO4 (placa A), **GPIO3 (placa B)**, GPIO9 (BOOT) y GPIO10.

## Verificar

```bash
arduino-cli upload --fqbn esp32:esp32:esp32c3:CDCOnBoot=cdc -p COM12 firmware/gibbor_esp32c3
```

Por el monitor serie (115200):

- `INFO` → `btn=GPIO4(bajo)+GPIO3(alto)+GPIO9(bajo)+GPIO10(bajo)`. GPIO3 tiene que salir `(alto)`.
- `STATUS` → `btn3=off` en reposo y `btn3=ON` con el botón presionado.
- Al presionar: `[GIBBOR] BOTON_B (GPIO3) presionado` → `GIBBOR_PANIC_TRIGGER #n` por BLE.

## Qué hace un click (app con la protección activa)

Un click → `GIBBOR_PANIC_TRIGGER` → `ProtectionService` en el teléfono (aunque esté bloqueado):
ubicación → grabación de audio y video → SMS a los contactos → incidente al backend →
hash SHA-256 de la evidencia → transacción en Stellar (contrato `incident-registry`).

Requisitos para que la cadena llegue hasta la transacción:

- `backend/api/.env` con `CROSSMINT_SERVER_API_KEY` y `CROSSMINT_SIGNER_SECRET` (los pone el usuario) y el
  backend corriendo (`npm start` en `backend/api`, puerto 3001).
- En la app, Ajustes → URL del backend = `http://<IP de la PC en la Wi-Fi>:3001`, con el teléfono en la misma Wi-Fi.
- Permisos de la app (ubicación, cámara, micrófono, SMS, Bluetooth, notificaciones) y al menos un contacto de emergencia.

## Historial

| Fecha | Evento |
|---|---|
| 2026-11-12 | Respaldo del firmware de fábrica, flasheo de GIBBOR 1.2.0, `SCAN`: botón en GPIO3 con pull-down. Encontrado el riel + a 5 V. |
| 2026-11-12 | **Click físico verificado de punta a punta** (Pixel 8, backend local por `adb reverse`): `BOTON_B (GPIO3)` → app en ~0.1 s → ubicación, audio, video, SMS → `create_incident` [1b73cf7d…](https://stellar.expert/explorer/testnet/tx/1b73cf7d10cf03ea9045706979c94364bffb39c7acf82a9e7a019b40d3b8bdb8) → hash del audio [5b84d5d5…](https://stellar.expert/explorer/testnet/tx/5b84d5d5e900db0a356a045b78042b6c80abc130fe21722c5fe2c809a3b46744) → hash del video [36bc7498…](https://stellar.expert/explorer/testnet/tx/36bc749859161d93a2da1acddc4acc6d158eb00ed89dfff09278b0b10bb1e68a). |
| 2026-11-12 | **fw 1.3.0, 1 click + 4 clicks verificados con el botón físico**: 1 click → pánico a ~0.8 s → grabación, SMS *enviado* y *entregado* (acuse de la red), incidente on-chain; 4 clicks rápidos (~0.8 s) → `GIBBOR_STOP_RECORDING` → audio y video detenidos sin alerta → hashes de audio y video anclados. |
