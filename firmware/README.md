# Firmware GIBBOR — botón físico ESP32-C3

Botón de pánico Bluetooth LE para la app GIBBOR. Al presionarlo envía
`GIBBOR_PANIC_TRIGGER #<n>` por el servicio Nordic UART; la app
(`Esp32BleClient.kt`) lo detecta y arranca el flujo de incidente + grabación.

## Hardware

Placa: **ESP32-C3 SuperMini** (V1601) dentro de la carcasa impresa, con un pulsador rojo externo.

| Señal | Pin | Notas |
|---|---|---|
| Botón rojo (externo) | **GPIO4** | `INPUT_PULLUP`, activo en bajo. **Una pata a GPIO4 y la otra a GND. Nunca a 3.3 V.** |
| Botón BOOT de la placa | GPIO9 | También dispara el pánico (útil para pruebas) |
| Botón alternativo | GPIO10 | Pad libre (no es pin de arranque) para resoldar el botón rojo entre GPIO10 y GND sin reflashear |
| Botón de la placa B | GPIO3 | Protoboard: botón al riel + con pull-down externa (activo en alto). Ver [gibborv2.md](gibborv2.md) |

### Placas e intercambio de botones

| Placa | Rama | Botón | Configuración |
|---|---|---|---|
| **GibborV2 prototype** (protoboard) | `GibborV2prototype` | GPIO3, activo en alto | [gibborv2.md](gibborv2.md) |
| **GIO MK2 prototype** (carcasa dorada) | `GIO-MK2-prototype` | GPIO4, activo en bajo | [gio_mk2.md](gio_mk2.md) |

Las dos usan **el mismo firmware y la misma app**: anuncian el mismo nombre (`GIBBOR_ESP32`) y el mismo
servicio NUS, y el firmware detecta solo el pin y la polaridad del botón. Para cambiar de botón basta con
desconectar una placa y conectar la otra (cada una ya tiene el firmware): la app la encuentra por el
servicio, no por la MAC. Si las dos están encendidas a la vez, la app se conecta a la primera que encuentre.
| LED de estado | GPIO8 | Activo en bajo (SuperMini). Destello cada 2 s = anunciando · fijo = app escuchando · parpadeo rápido = pánico enviado |

Cambiar pines: `-DBUTTON_PIN=…`, `-DBUTTON2_PIN=…`, `-DBOOT_PIN=…`, `-DLED_PIN=…`, `-DLED_ACTIVE_LOW=0` (o editar los `#define`).

**Polaridad automática (fw 1.2.0):** al arrancar, cada botón externo (GPIO4, GPIO3, GPIO10) se mide con
pull-up y pull-down. Si lee GND aun con el pull-up activo, hay una resistencia pull-down externa y el
botón se trata como **activo en alto** (botón a 3.3 V). Si no, **activo en bajo** (botón a GND).
`-DBUTTON_ACTIVE_HIGH=0|1` la fuerza. No presiones el botón mientras la placa arranca.
Nunca conectes un botón a **5V**: los GPIO del C3 aguantan ~3.6 V.
No mantengas presionado GPIO9 al conectar la placa: es pin de arranque y entraría en modo descarga.

### Problema de hardware encontrado (2026-11-11)

`SCAN` mostró que **GPIO4 lee GND todo el tiempo** (`up=0 down=0`), con o sin presionar el botón. En la
placa, los pads **G, 3.3 y 4** están cubiertos por una mancha de estaño, así que GPIO4 está en corto con GND
y el botón no puede cambiar el nivel del pin. Si la otra pata del botón va a 3.3 V, cada pulsación hace un
corto 3.3 V → GND, lo que explica los reinicios `reset=POWERON` observados.

Arreglo: retirar el estaño entre los pads con malla desoldadora y dejar el botón entre **GPIO4 y GND**
(un pad G limpio). Al arrancar, el firmware avisa por Serial si un botón está en GND
(`AVISO: BOTON (GPIO4) esta en GND al arrancar`), y `STATUS` muestra `btn4=` (1 = suelto, 0 = presionado).

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
| `INFO` | `INFO name=GIBBOR_ESP32 fw=1.2.0 mac=… btn=GPIO4(bajo)+GPIO3(alto)+GPIO9(bajo)+GPIO10(bajo) uptime_s=… reset=…` |
| `STATUS` | `STATUS connected=… subscribed=… triggers=… pending=… mtu=… btn4=off btn3=off btn9=off btn10=off` (`ON` = presionado) |
| `TRIGGER` | Simula una pulsación (misma función `firePanic()` que el botón) |
| `SIMMTU <n>` | Solo Serial, para pruebas: simula un MTU pequeño. `SIMMTU 0` lo desactiva |
| `DROP` | Solo Serial, para pruebas: corta la conexión BLE como si el teléfono se alejara |
| `SCAN <s>` | Solo Serial: durante `<s>` segundos lee GPIO 0–10, 20 y 21 con pull-up y pull-down y reporta qué pin cambia al presionar (`1/0` libre, `0/0` a GND, `1/1` a 3.3 V). Sirve para ubicar un botón externo o un corto |

`INFO` incluye `reset=` con el motivo del último reinicio: `POWERON` (corte de alimentación),
`BROWNOUT` (voltaje bajo), `PANIC`/`*_WDT` (falla de firmware), `USB` (reinicio pedido por la PC).
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
