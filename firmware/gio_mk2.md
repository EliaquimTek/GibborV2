# GIO MK2 — configuración de la placa A (carcasa dorada)

Primera ESP32-C3 del proyecto, montada en la carcasa impresa dorada con el pulsador rojo soldado.
Usa **el mismo firmware y la misma app** que la placa GibborV2 ([gibborv2.md](gibborv2.md)).

## Identidad

| | Valor |
|---|---|
| Placa | ESP32-C3 SuperMini V1601 (rev v0.4, 4 MB flash) |
| MAC BLE (`INFO`) | `14:63:93:91:8e:86` |
| Puerto en esta PC | **COM15** (USB-Serial/JTAG nativo) |
| Firmware | `gibbor_esp32c3` 1.2.0 (cualquier versión ≥ 1.1.0 escucha GPIO4) |
| Respaldo del firmware de fábrica | `C:\Gibborrim\Gibbor\firmware_backup\esp32c3_original_4MB.bin` (sketch de prueba "ESP32C3-Test") |

## Cableado

| Elemento | Conexión | Polaridad |
|---|---|---|
| Pulsador rojo externo | pad **GPIO4** ↔ pad **G** | activo en BAJO (pull-up interno) |
| Botón BOOT de la placa | GPIO9 | activo en BAJO |
| Pad alternativo | GPIO10 (libre, para resoldar el botón si el pad 4 se daña) | activo en BAJO |
| LED de estado | GPIO8 (integrado) | activo en bajo |

## Estado conocido (2026-11-11)

`SCAN` mostró **GPIO4 siempre en GND** (`up=0 down=0`) y la placa se apagaba (`reset=POWERON`) al presionar
el botón. En la foto, los pads **G, 3.3 y 4** están unidos por estaño: GPIO4 está en corto con GND y el
botón cierra un corto 3.3 V → GND. Mientras siga así:

- el firmware detecta GPIO4 como "en GND" y no dispara nada por ese pin (no hay falsos pánicos);
- el botón **BOOT** de la placa sí funciona como botón de pánico;
- arreglo: limpiar el estaño entre G / 3.3 / 4 y dejar el botón entre GPIO4 (o GPIO10) y G, **nunca a 3.3 V**.

## Verificar

```bash
arduino-cli upload --fqbn esp32:esp32:esp32c3:CDCOnBoot=cdc -p COM15 firmware/gibbor_esp32c3
```

- `INFO` → `btn=GPIO4(bajo)+GPIO3(bajo)+GPIO9(bajo)+GPIO10(bajo)`. Si GPIO4 sale `(alto)`, el pad sigue en corto con GND.
- `STATUS` → `btn4=off` en reposo, `btn4=ON` presionado.
- Al presionar: `[GIBBOR] BOTON (GPIO4) presionado` → `GIBBOR_PANIC_TRIGGER #n` por BLE.
