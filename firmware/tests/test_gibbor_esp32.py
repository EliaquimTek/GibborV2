"""
Pruebas de hardware del botón GIBBOR (ESP32-C3) y de compatibilidad con la app.

Simula desde la PC lo que hace Esp32BleClient.kt en Android:
  escaneo por nombre -> conexión GATT -> servicio NUS -> CCCD de TX -> notificaciones
y aplica el mismo parser de líneas que la app (handleIncoming).

Uso:
  python -m venv .venv && .venv/Scripts/pip install -r tests/requirements.txt
  .venv/Scripts/python tests/test_gibbor_esp32.py --port COM15
  (añade --skip-ble si la PC no tiene Bluetooth LE)
"""
import argparse
import asyncio
import json
import sys
import time

import serial
from bleak import BleakClient, BleakScanner

DEVICE_NAME = "GIBBOR_ESP32"
NUS_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
NUS_RX = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
NUS_TX = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
CCCD = "00002902-0000-1000-8000-00805f9b34fb"
APP_SCAN_TIMEOUT_S = 10.0  # Esp32BleClient.SCAN_TIMEOUT_MS

results = []


def record(name, ok, detail=""):
    results.append({"test": name, "ok": bool(ok), "detail": str(detail)})
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f" — {detail}" if detail else ""))


def app_is_stop(line: str) -> bool:
    """Réplica de PanicMessageParser.isStop (4 clicks rápidos)."""
    return "gibbor_stop_recording" in line.lower()


def app_is_trigger(line: str) -> bool:
    """Réplica exacta de PanicMessageParser.isTrigger."""
    return not app_is_stop(line) and ("boton" in line.lower() or "gibbor_panic_trigger" in line.lower())


def app_parse(payload: bytes):
    """Réplica de handleIncoming: UTF-8 -> split('\\n') -> trim -> no vacías."""
    text = payload.decode("utf-8", errors="replace")
    return [l.strip() for l in text.split("\n") if l.strip()]


# ─── Serial ──────────────────────────────────────────────────────────────
class Dev:
    def __init__(self, port):
        self.port = port
        self.ser = None
        self.buf = ""

    def open(self, retries=40):
        for _ in range(retries):
            try:
                self.ser = serial.Serial(self.port, 115200, timeout=0.05)
                return
            except serial.SerialException:
                time.sleep(0.25)
        raise RuntimeError(f"No se pudo abrir {self.port}")

    def close(self):
        if self.ser:
            # Bajar RTS antes que DTR: el estado RTS=1/DTR=0 reinicia el ESP32-C3 (USB-Serial/JTAG)
            try:
                self.ser.rts = False
                self.ser.dtr = False
            except serial.SerialException:
                pass
            self.ser.close()
            self.ser = None

    def reset(self):
        """Reset por RTS (igual que esptool). El USB se re-enumera: reabrimos."""
        self.ser.dtr = False
        self.ser.rts = True
        time.sleep(0.1)
        self.ser.rts = False
        self.close()
        time.sleep(0.8)
        self.open()

    def read_for(self, seconds):
        end = time.time() + seconds
        out = ""
        while time.time() < end:
            try:
                chunk = self.ser.read(512).decode("utf-8", errors="replace")
            except serial.SerialException:
                self.close()
                time.sleep(0.3)
                self.open()
                continue
            out += chunk
        self.buf += out
        return out

    def cmd(self, c, wait=0.6):
        self.ser.reset_input_buffer()
        self.ser.write((c + "\n").encode())
        return self.read_for(wait)


def serial_tests(dev: Dev):
    print("\n== Pruebas por Serial (USB CDC) ==")
    dev.open()
    before = _uptime(dev)
    dev.reset()
    # Con USB-Serial/JTAG nativo el puerto se re-enumera al reiniciar y el
    # banner de arranque se imprime antes de que Windows permita reabrirlo,
    # así que el reinicio se verifica por el uptime.
    boot = dev.read_for(2.0)
    after = _uptime(dev)
    record("Reset por RTS reinicia el firmware", after is not None and after <= 6 and (before is None or after < before + 1),
           f"uptime antes={before}s después={after}s")
    record("Arranque: sin panic/reboot", "Guru Meditation" not in boot and "abort()" not in boot)

    r = dev.cmd("PING")
    record("PING -> PONG", "PONG" in r)

    r = dev.cmd("INFO")
    info = next((l for l in r.splitlines() if l.startswith("INFO")), "")
    record("INFO reporta nombre y versión", f"name={DEVICE_NAME}" in info and "fw=" in info, info)

    r = dev.cmd("STATUS")
    st = next((l for l in r.splitlines() if l.startswith("STATUS")), "")
    record("STATUS sin conexión", "connected=0" in st, st)

    r = dev.cmd("nope")
    record("Comando desconocido -> ERR", "ERR unknown_cmd=NOPE" in r)

    for l in (info, st, "PONG"):
        if app_is_trigger(l):
            record("Respuestas no se confunden con pánico", False, l)
            break
    else:
        record("Respuestas no se confunden con pánico", True)

    # Uptime estable (no hay reinicios espontáneos)
    up1 = _uptime(dev)
    time.sleep(3)
    up2 = _uptime(dev)
    record("Estabilidad: sin reinicios en 3 s", up1 is not None and up2 is not None and up2 >= up1 + 2, f"uptime {up1}s -> {up2}s")


def _uptime(dev):
    r = dev.cmd("INFO")
    for tok in r.split():
        if tok.startswith("uptime_s="):
            return int(tok.split("=")[1])
    return None


# ─── BLE ─────────────────────────────────────────────────────────────────
async def find_device(timeout=APP_SCAN_TIMEOUT_S):
    t0 = time.time()
    found = await BleakScanner.find_device_by_filter(
        lambda d, adv: (adv.local_name or d.name) == DEVICE_NAME, timeout=timeout
    )
    return found, time.time() - t0


async def ble_tests(dev: Dev):
    print("\n== Pruebas BLE (compatibilidad con Esp32BleClient.kt) ==")
    d, dt = await find_device()
    record(f"Escaneo encuentra '{DEVICE_NAME}' en < {APP_SCAN_TIMEOUT_S:.0f} s", d is not None, f"{dt:.1f} s" + (f", {d.address}" if d else ""))
    if not d:
        return

    rx_lines = []
    rx_times = []

    def on_notify(_, data: bytearray):
        for line in app_parse(bytes(data)):
            rx_lines.append(line)
            rx_times.append(time.time())

    async def wait_line(pred, timeout=3.0):
        end = time.time() + timeout
        while time.time() < end:
            for i, l in enumerate(rx_lines):
                if pred(l):
                    return l, rx_times[i]
            await asyncio.sleep(0.02)
        return None, None

    async with BleakClient(d, timeout=15.0) as client:
        record("Conexión GATT", client.is_connected)
        svc = client.services.get_service(NUS_SERVICE)
        record("Servicio Nordic UART presente", svc is not None)
        if not svc:
            return
        tx = svc.get_characteristic(NUS_TX)
        rx = svc.get_characteristic(NUS_RX)
        record("TX (6E400003) con NOTIFY", tx is not None and "notify" in tx.properties, tx.properties if tx else "")
        record("TX tiene descriptor CCCD 0x2902", tx is not None and any(x.uuid.lower() == CCCD for x in tx.descriptors))
        record("RX (6E400002) con WRITE", rx is not None and ("write" in rx.properties or "write-without-response" in rx.properties), rx.properties if rx else "")

        await client.start_notify(NUS_TX, on_notify)
        await asyncio.sleep(0.5)
        # Pudo llegar un trigger diferido de pruebas previas: lo descartamos
        rx_lines.clear(); rx_times.clear()

        await client.write_gatt_char(NUS_RX, b"PING", response=True)
        l, _ = await wait_line(lambda x: x == "PONG")
        record("RX 'PING' -> TX 'PONG'", l is not None)

        await client.write_gatt_char(NUS_RX, b"STATUS", response=True)
        l, _ = await wait_line(lambda x: x.startswith("STATUS"))
        record("STATUS por BLE: connected=1 subscribed=1", l is not None and "connected=1" in l and "subscribed=1" in l, l)

        await client.write_gatt_char(NUS_RX, b"INFO", response=True)
        l, _ = await wait_line(lambda x: x.startswith("INFO"))
        record("INFO por BLE", l is not None, l)

        record("Ninguna respuesta dispara pánico en la app", not any(app_is_trigger(x) for x in rx_lines), rx_lines)

        # Trigger físico simulado (mismo código que el botón: firePanic)
        rx_lines.clear(); rx_times.clear()
        t0 = time.time()
        dev.ser.write(b"TRIGGER\n")
        l, t1 = await wait_line(app_is_trigger, timeout=3.0)
        record("Pánico llega a la app y el parser lo detecta", l is not None, l)
        if l:
            record("Latencia trigger -> notificación < 500 ms", (t1 - t0) < 0.5, f"{(t1 - t0) * 1000:.0f} ms")

        # Cooldown: segundo disparo inmediato no debe duplicar
        dev.ser.write(b"TRIGGER\n")
        await asyncio.sleep(0.8)
        n = sum(1 for x in rx_lines if app_is_trigger(x))
        record("Cooldown evita pánico duplicado (< 1.5 s)", n == 1, f"{n} triggers")

        # Pasado el cooldown, vuelve a disparar
        await asyncio.sleep(1.0)
        await client.write_gatt_char(NUS_RX, b"TRIGGER", response=True)
        await asyncio.sleep(0.8)
        n = sum(1 for x in rx_lines if app_is_trigger(x))
        record("Segundo pánico tras cooldown (vía RX)", n == 2, f"{n} triggers")

        # Clicks del botón (PRESS = misma ruta que una pulsación física)
        await asyncio.sleep(1.6)  # salir del cooldown
        rx_lines.clear(); rx_times.clear()
        t0 = time.time()
        dev.ser.write(b"PRESS\n")
        l, t1 = await wait_line(app_is_trigger, timeout=3.0)
        dt = (t1 - t0) if l else 0
        record("1 click -> pánico tras esperar ~0.8 s por más clicks", l is not None and 0.6 < dt < 1.5, f"{dt * 1000:.0f} ms, {l}")

        await asyncio.sleep(2.0)
        rx_lines.clear(); rx_times.clear()
        for _ in range(4):
            dev.ser.write(b"PRESS\n")
            await asyncio.sleep(0.2)
        l, _ = await wait_line(app_is_stop, timeout=2.0)
        await asyncio.sleep(1.5)  # una alerta tardía aparecería aquí
        trig = [x for x in rx_lines if app_is_trigger(x)]
        record("4 clicks rápidos -> GIBBOR_STOP_RECORDING", l is not None, l)
        record("4 clicks rápidos no disparan pánico", not trig, rx_lines)

        # Un 5.º click justo después se ignora (no abre una alerta nueva)
        rx_lines.clear(); rx_times.clear()
        dev.ser.write(b"PRESS\n")
        await asyncio.sleep(1.5)
        record("Click extra tras detener se ignora", not any(app_is_trigger(x) for x in rx_lines), rx_lines)
        await asyncio.sleep(2.2)  # fin del bloqueo antes de seguir

        # MTU 23 (Android < 14 sin requestMtu): cada notificación lleva <= 20 bytes
        await client.write_gatt_char(NUS_RX, b"STATUS", response=True)
        l, _ = await wait_line(lambda x: x.startswith("STATUS") and "mtu=" in x)
        print("    MTU negociado con esta PC:", l.split("mtu=")[-1] if l else "?")
        sizes = []

        def on_raw(_, data: bytearray):
            sizes.append(len(data))
            on_notify(_, data)

        await client.stop_notify(NUS_TX)
        await client.start_notify(NUS_TX, on_raw)
        dev.ser.write(b"SIMMTU 23\n")
        await asyncio.sleep(1.6)
        rx_lines.clear(); rx_times.clear()
        dev.ser.write(b"TRIGGER\n")
        await asyncio.sleep(0.8)
        trig = [x for x in rx_lines if app_is_trigger(x)]
        record("MTU 23: notificaciones <= 20 bytes", bool(sizes) and max(sizes) <= 20, f"tamaños={sizes}")
        record("MTU 23: la app detecta exactamente 1 pánico", len(trig) == 1, f"líneas={rx_lines}")
        dev.ser.write(b"SIMMTU 0\n")
        await asyncio.sleep(0.3)

    # Desconexión -> debe volver a anunciarse
    await asyncio.sleep(1.5)
    d2, dt2 = await find_device()
    record("Re-anuncio tras desconexión", d2 is not None, f"{dt2:.1f} s")

    # Pánico sin app conectada -> se entrega al reconectar
    await asyncio.sleep(1.6)
    dev.ser.write(b"TRIGGER\n")
    await asyncio.sleep(0.5)
    rx_lines.clear(); rx_times.clear()
    d3, _ = await find_device()
    if d3:
        async with BleakClient(d3, timeout=15.0) as client:
            await client.start_notify(NUS_TX, on_notify)
            l, _ = await wait_line(app_is_trigger, timeout=3.0)
            record("Pánico sin conexión se entrega al reconectar", l is not None and "diferido" in l, l)
    else:
        record("Pánico sin conexión se entrega al reconectar", False, "no se reencontró el dispositivo")

    # Estabilidad: varios ciclos conectar/desconectar
    ok_cycles = 0
    for _ in range(3):
        await asyncio.sleep(1.0)
        dx, _ = await find_device()
        if not dx:
            continue
        try:
            async with BleakClient(dx, timeout=15.0) as client:
                rx_lines.clear()
                await client.start_notify(NUS_TX, on_notify)
                await client.write_gatt_char(NUS_RX, b"PING", response=True)
                l, _ = await wait_line(lambda x: x == "PONG")
                ok_cycles += 1 if l else 0
        except Exception as e:  # noqa: BLE001
            print("    ciclo falló:", e)
    record("3 ciclos conectar/PING/desconectar", ok_cycles == 3, f"{ok_cycles}/3")


async def filtered_scan(timeout=12.0):
    """Escaneo filtrado por UUID de servicio, como el ScanFilter de la app (Esp32BleClient)."""
    found = asyncio.get_running_loop().create_future()

    def on_adv(d, adv):
        if NUS_SERVICE in [u.lower() for u in adv.service_uuids] and not found.done():
            found.set_result(d)

    scanner = BleakScanner(detection_callback=on_adv, service_uuids=[NUS_SERVICE])
    await scanner.start()
    try:
        return await asyncio.wait_for(found, timeout)
    except asyncio.TimeoutError:
        return None
    finally:
        await scanner.stop()


async def reconnect_chain_tests(dev: Dev):
    """
    Cadena completa de la app nueva:
    conectado -> se cae la conexión -> pánico sin conexión -> reconexión automática
    (escaneo filtrado + espera creciente 2/5/10/20/30 s) -> notificaciones -> pánico diferido.
    """
    print("\n== Reconexión automática (cadena de la app con protección en segundo plano) ==")
    rx_lines = []

    def on_notify(_, data: bytearray):
        rx_lines.extend(app_parse(bytes(data)))

    async def wait_line(pred, timeout=4.0):
        end = time.time() + timeout
        while time.time() < end:
            hit = next((l for l in rx_lines if pred(l)), None)
            if hit:
                return hit
            await asyncio.sleep(0.02)
        return None

    await asyncio.sleep(1.5)
    d = await filtered_scan()
    record("Anuncio incluye el UUID NUS (el ScanFilter de Android lo encuentra)", d is not None)
    if not d:
        return

    dropped = asyncio.Event()
    async with BleakClient(d, timeout=15.0, disconnected_callback=lambda _: dropped.set()) as client:
        await client.start_notify(NUS_TX, on_notify)
        await client.write_gatt_char(NUS_RX, b"STATUS", response=True)
        st = await wait_line(lambda x: x.startswith("STATUS"))
        record("STATUS completo en una sola notificación (MTU negociado)", st is not None and "mtu=" in st, st)

        # Te alejas: el enlace se corta
        dev.ser.write(b"DROP\n")
        try:
            await asyncio.wait_for(dropped.wait(), 6.0)
            record("Caída de conexión detectada", True)
        except asyncio.TimeoutError:
            record("Caída de conexión detectada", False)
            return

    # Presionas el botón mientras estás desconectado
    await asyncio.sleep(1.6)
    dev.ser.write(b"TRIGGER\n")
    t_drop = time.time()

    # Reconexión como Esp32BleClient: espera creciente + escaneo filtrado
    rx_lines.clear()
    delays = [2, 5, 10, 20, 30]
    line, attempts = None, 0
    for delay in delays:
        attempts += 1
        await asyncio.sleep(delay)
        dx = await filtered_scan()
        if not dx:
            continue
        try:
            async with BleakClient(dx, timeout=15.0) as client:
                await client.start_notify(NUS_TX, on_notify)  # reactiva notificaciones (CCCD)
                line = await wait_line(app_is_trigger)
                break
        except Exception as e:  # noqa: BLE001
            print("    intento falló:", e)
    elapsed = time.time() - t_drop
    record("Reconexión automática tras la caída", line is not None, f"{elapsed:.1f} s, {attempts} intento(s)")
    record("Pánico presionado sin conexión llega como diferido", line is not None and "diferido" in line, line)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", default="COM15")
    ap.add_argument("--skip-ble", action="store_true")
    ap.add_argument("--json", default=None, help="ruta para guardar resultados")
    args = ap.parse_args()

    dev = Dev(args.port)
    try:
        serial_tests(dev)
        if not args.skip_ble:
            asyncio.run(ble_tests(dev))
            asyncio.run(reconnect_chain_tests(dev))
        # El firmware no debe haberse reiniciado durante todo el proceso
        log = dev.buf
        record("Sin crash durante toda la sesión", "Guru Meditation" not in log and "abort()" not in log)
    finally:
        dev.close()

    passed = sum(r["ok"] for r in results)
    print(f"\nResultado: {passed}/{len(results)} pruebas OK")
    if args.json:
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump(results, f, ensure_ascii=False, indent=2)
    sys.exit(0 if passed == len(results) else 1)


if __name__ == "__main__":
    main()
