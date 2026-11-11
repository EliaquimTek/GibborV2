# Firmware GIBBOR — botón físico ESP32-C3

Botón de pánico Bluetooth LE para la app GIBBOR. Al presionarlo envía
`GIBBOR_PANIC_TRIGGER #<n>` por el servicio Nordic UART; la app
(`Esp32BleClient.kt`) lo detecta y arranca el flujo de incidente + grabación.

## Hardware

| Señal | Pin | Notas |
|---|---|---|
| Botón | **GPIO9** (botón BOOT de la placa) | `INPUT_PULLUP`, activo en bajo. Para un botón externo conéctalo entre el pin y GND. |
| LED de estado | GPIO8 | Activo en bajo (SuperMini). Destello cada 2 s = anunciando · fijo = app escuchando · parpadeo rápido = pánico enviado |

Cambiar pines: `-DBUTTON_PIN=…`, `-DLED_PIN=…`, `-DLED_ACTIVE_LOW=0` (o editar los `#define`).
No mantengas presionado GPIO9 al conectar la placa: es pin de arranque y entraría en modo descarga.

## Compilar y flashear

Requiere el core `esp32:esp32` (probado con 3.3.7). Con el `arduino-cli` que trae Arduino IDE:

```bash
arduino-cli compile --fqbn esp32:esp32:esp32c3:CDCOnBoot=cdc firmware/gibbor_esp32c3
```

```bash
arduino-cli upload --fqbn esp32:esp32:esp32c3:CDCOnBoot=cdc -p COM15 firmware/gibbor_esp32c3
```

En Arduino IDE: placa **ESP32C3 Dev Module**, **USB CDC On Boot: Enabled**. Monitor serie a 115200.

## Protocolo BLE

| | UUID / valor |
|---|---|
| Nombre | `GIBBOR_ESP32` |
| Servicio NUS | `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` |
| TX notify (ESP→app) | `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` + CCCD `0x2902` |
| RX write (app→ESP) | `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` |

Comportamiento:
- **Antirrebote** 50 ms y **cooldown** 1.5 s entre pánicos (evita dobles disparos).
- **Pánico sin conexión**: se guarda y se entrega al suscribirse la app si ocurrió hace < 5 min,
  como `GIBBOR_PANIC_TRIGGER #n (diferido)`. La app reconecta sola (espera máx. 30 s entre
  intentos), así que el pánico llega aunque el teléfono se haya alejado un momento.
- **MTU**: las notificaciones se trocean según el MTU del enlace (20 bytes con MTU 23). El pánico
  siempre empieza por `GIBBOR_PANIC_TRIGGER`, que cabe en el primer trozo.
- Tras desconectarse vuelve a anunciarse automáticamente.

### Comandos (por RX de BLE o por Serial)

| Comando | Respuesta |
|---|---|
| `PING` | `PONG` |
| `INFO` | `INFO name=GIBBOR_ESP32 fw=1.0.0 mac=… btn=GPIO9 uptime_s=…` |
| `STATUS` | `STATUS connected=… subscribed=… triggers=… pending=… mtu=…` |
| `TRIGGER` | Simula una pulsación (misma función `firePanic()` que el botón) |
| `SIMMTU <n>` | Solo Serial, para pruebas: simula un MTU pequeño. `SIMMTU 0` lo desactiva |
| `DROP` | Solo Serial, para pruebas: corta la conexión BLE como si el teléfono se alejara |
| `HELP` | Lista de comandos |

Ninguna respuesta contiene `GIBBOR_PANIC_TRIGGER` ni `Boton`, así que la app no las confunde con un pánico.

## Pruebas automáticas

`tests/test_gibbor_esp32.py` prueba la placa por Serial y hace desde la PC (con `bleak`) lo mismo que
la app Android: escanear por nombre, conectar GATT, validar servicio/características/CCCD,
suscribirse y aplicar el mismo parser de líneas que `handleIncoming`.

```bash
cd firmware && python -m venv .venv && .venv/Scripts/pip install -r tests/requirements.txt
```

```bash
cd firmware && .venv/Scripts/python tests/test_gibbor_esp32.py --port COM15
```

Usa `--skip-ble` si la PC no tiene Bluetooth LE.

### Resultado en la placa real (2026-11-10, ESP32-C3 rev 0.4, Windows 11): 33/33 ✅

| Grupo | Prueba | Resultado |
|---|---|---|
| Serial | Reset por RTS reinicia el firmware | ✅ |
| Serial | Arranque sin panic/abort | ✅ |
| Serial | PING→PONG, INFO, STATUS, comando desconocido→ERR | ✅ |
| Serial | Las respuestas no se confunden con pánico | ✅ |
| Serial | Sin reinicios espontáneos | ✅ |
| BLE | Escaneo encuentra `GIBBOR_ESP32` en < 10 s (timeout de la app) | ✅ ~0.6 s |
| BLE | Conexión GATT, servicio NUS, TX notify + CCCD, RX write | ✅ |
| BLE | PING/STATUS/INFO por BLE | ✅ |
| BLE | Pánico llega y el parser de la app lo detecta | ✅ |
| BLE | Latencia trigger → notificación | ✅ ~14 ms |
| BLE | Cooldown evita duplicado; segundo pánico tras cooldown | ✅ |
| BLE | MTU 23: trozos ≤ 20 bytes y exactamente 1 pánico detectado | ✅ |
| BLE | Re-anuncio tras desconexión | ✅ ~2 s |
| BLE | Pánico sin conexión se entrega al reconectar | ✅ |
| BLE | 3 ciclos conectar/PING/desconectar | ✅ (en 1 de 4 corridas falló un ciclo por el stack BLE de Windows) |
| Reconexión | El anuncio incluye el UUID NUS (lo encuentra el `ScanFilter` de Android) | ✅ |
| Reconexión | STATUS completo en una sola notificación (MTU 256) | ✅ |
| Reconexión | Caída de conexión (`DROP`) detectada | ✅ |
| Reconexión | Reconexión automática con escaneo filtrado + espera creciente | ✅ ~4–5 s, 1 intento |
| Reconexión | Pánico presionado sin conexión llega como `(diferido)` | ✅ |
| Global | Sin crash durante toda la sesión | ✅ |

Las pruebas de "Reconexión" reproducen desde la PC lo que hace la app con la protección en
segundo plano (`ProtectionService` + `Esp32BleClient`).

Pendiente: prueba manual presionando el botón físico con la app instalada en un teléfono.
