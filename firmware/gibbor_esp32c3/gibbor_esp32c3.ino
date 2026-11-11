/*
 * GIBBOR — Firmware del botón físico de pánico (ESP32-C3)
 * -------------------------------------------------------
 * Contrato con la app Android (Esp32BleClient.kt):
 *   - Nombre BLE anunciado:  "GIBBOR_ESP32"
 *   - Servicio Nordic UART:  6E400001-B5A3-F393-E0A9-E50E24DCCA9E
 *       TX (notify, ESP→app): 6E400003-B5A3-F393-E0A9-E50E24DCCA9E  (+ CCCD 0x2902)
 *       RX (write,  app→ESP): 6E400002-B5A3-F393-E0A9-E50E24DCCA9E
 *   - Al presionar el botón se notifica por TX la línea
 *       "GIBBOR_PANIC_TRIGGER #<n>\n"
 *     La app dispara el flujo de pánico si la línea contiene
 *     "GIBBOR_PANIC_TRIGGER" o "Boton".
 *
 * Comandos que acepta por RX (BLE) y por Serial (USB, 115200):
 *   PING     -> PONG
 *   INFO     -> nombre, versión, MAC, uptime
 *   STATUS   -> conexión, suscripción, triggers enviados
 *   TRIGGER  -> simula una pulsación del botón (para pruebas)
 *   HELP     -> lista de comandos (solo Serial)
 *
 * Ninguna respuesta (salvo un trigger real) contiene "GIBBOR_PANIC_TRIGGER"
 * ni "Boton", para que la app no confunda una respuesta con un pánico.
 *
 * Placa: ESP32C3 Dev Module, "USB CDC On Boot: Enabled".
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>

// ─── Configuración ────────────────────────────────────────────────────────
#define FW_VERSION "1.0.0"
#define DEVICE_NAME "GIBBOR_ESP32"

// Botón: GPIO9 es el botón BOOT de la mayoría de placas ESP32-C3
// (DevKitM-1, SuperMini). Para un botón externo, conéctalo entre el pin y GND.
#ifndef BUTTON_PIN
#define BUTTON_PIN 9
#endif

// LED de estado: GPIO8 en la SuperMini (activo en bajo).
#ifndef LED_PIN
#define LED_PIN 8
#endif
#ifndef LED_ACTIVE_LOW
#define LED_ACTIVE_LOW 1
#endif

static const uint32_t DEBOUNCE_MS = 50;           // filtro de rebote
static const uint32_t TRIGGER_COOLDOWN_MS = 1500; // evita disparos dobles
static const uint32_t PENDING_TRIGGER_TTL_MS = 60000; // pánico sin conexión: se entrega al reconectar si es reciente

#define NUS_SERVICE_UUID "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define NUS_RX_UUID      "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
#define NUS_TX_UUID      "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

// ─── Estado ───────────────────────────────────────────────────────────────
static BLEServer *gServer = nullptr;
static BLECharacteristic *gTx = nullptr;

static volatile bool gConnected = false;
static volatile bool gSubscribed = false;
static volatile bool gNeedAdvertise = false;

static uint32_t gTriggerCount = 0;
static uint32_t gLastTriggerMs = 0;
static bool gPendingTrigger = false;
static uint32_t gPendingSinceMs = 0;

static int gButtonStable = HIGH;
static int gButtonLastRead = HIGH;
static uint32_t gButtonChangedMs = 0;

static String gBleCmd;          // comando recibido por BLE (procesado en loop)
static volatile bool gBleCmdReady = false;
static String gSerialLine;

static uint32_t gLedFlashUntil = 0;

static volatile uint16_t gMtu = 23; // MTU ATT negociado con la app
static size_t gSimChunk = 0;        // SIMMTU (solo pruebas): limita el trozo

// ─── Utilidades ───────────────────────────────────────────────────────────
static void ledWrite(bool on) {
  digitalWrite(LED_PIN, (on ^ LED_ACTIVE_LOW) ? HIGH : LOW);
}

static String macString() {
  return BLEDevice::getAddress().toString().c_str();
}

// Bytes útiles por notificación. La app no pide un MTU mayor, así que en
// Android < 14 queda el MTU por defecto (23 -> 20 bytes). Las notificaciones
// más largas se trocean; el trigger siempre empieza con
// "GIBBOR_PANIC_TRIGGER" (20 bytes), así que el primer trozo basta para que
// la app lo detecte.
static size_t chunkSize() {
  // Se consulta a la pila en cada envío: el callback onMtuChanged puede llegar
  // antes que onConnect y dejar un valor viejo.
  uint16_t mtu = gServer ? gServer->getPeerMTU(gServer->getConnId()) : 0;
  if (mtu >= 23) gMtu = mtu;
  size_t n = (gMtu > 3 ? gMtu : 23) - 3;
  if (gSimChunk > 0 && gSimChunk < n) n = gSimChunk;
  return n;
}

static bool sendLine(const String &line) {
  if (!gConnected || !gSubscribed || gTx == nullptr) {
    Serial.printf("[BLE] Sin suscriptor, no se envio: %s\n", line.c_str());
    return false;
  }
  String payload = line + "\n";
  const size_t step = chunkSize();
  for (size_t off = 0; off < payload.length(); off += step) {
    size_t len = min(step, payload.length() - off);
    gTx->setValue((uint8_t *)payload.c_str() + off, len);
    gTx->notify();
    if (off + len < payload.length()) delay(3);
  }
  Serial.printf("[BLE] TX -> %s (mtu=%u, trozo=%u)\n", line.c_str(), gMtu, (unsigned)step);
  return true;
}

static void reply(const String &line, bool viaBle) {
  Serial.println(line);
  if (viaBle) sendLine(line);
}

// ─── Pánico ───────────────────────────────────────────────────────────────
static void firePanic(const char *source) {
  uint32_t now = millis();
  if (gLastTriggerMs != 0 && now - gLastTriggerMs < TRIGGER_COOLDOWN_MS) {
    Serial.printf("[GIBBOR] Trigger ignorado (cooldown) desde %s\n", source);
    return;
  }
  gLastTriggerMs = now;
  gTriggerCount++;
  gLedFlashUntil = now + 1000;

  String msg = String("GIBBOR_PANIC_TRIGGER #") + gTriggerCount;
  Serial.printf("[GIBBOR] PANICO #%lu (origen: %s)\n", (unsigned long)gTriggerCount, source);
  if (!sendLine(msg)) {
    gPendingTrigger = true;
    gPendingSinceMs = now;
    Serial.println("[GIBBOR] Trigger pendiente: se enviara al reconectar la app");
  }
}

static void flushPendingTrigger() {
  if (!gPendingTrigger || !gSubscribed) return;
  gPendingTrigger = false;
  if (millis() - gPendingSinceMs > PENDING_TRIGGER_TTL_MS) {
    Serial.println("[GIBBOR] Trigger pendiente expirado, descartado");
    return;
  }
  sendLine(String("GIBBOR_PANIC_TRIGGER #") + gTriggerCount + " (diferido)");
}

// ─── Comandos ─────────────────────────────────────────────────────────────
static void handleCommand(String cmd, bool viaBle) {
  cmd.trim();
  cmd.toUpperCase();
  if (cmd.length() == 0) return;

  if (cmd == "PING") {
    reply("PONG", viaBle);
  } else if (cmd == "INFO") {
    reply(String("INFO name=" DEVICE_NAME " fw=" FW_VERSION " mac=") + macString() +
              " btn=GPIO" + BUTTON_PIN + " uptime_s=" + (millis() / 1000),
          viaBle);
  } else if (cmd == "STATUS") {
    reply(String("STATUS connected=") + (gConnected ? 1 : 0) +
              " subscribed=" + (gSubscribed ? 1 : 0) +
              " triggers=" + gTriggerCount +
              " pending=" + (gPendingTrigger ? 1 : 0) +
              " mtu=" + gMtu,
          viaBle);
  } else if (cmd == "TRIGGER") {
    firePanic(viaBle ? "BLE" : "SERIAL");
  } else if (!viaBle && cmd.startsWith("SIMMTU")) {
    // Solo por Serial: simula un MTU pequeño (p. ej. SIMMTU 23); SIMMTU 0 lo desactiva
    int mtu = cmd.substring(6).toInt();
    gSimChunk = mtu > 3 ? (size_t)(mtu - 3) : 0;
    reply(String("SIMMTU chunk=") + gSimChunk, false);
  } else if (cmd == "HELP") {
    reply("CMDS: PING INFO STATUS TRIGGER SIMMTU HELP", viaBle);
  } else {
    reply(String("ERR unknown_cmd=") + cmd, viaBle);
  }
}

// ─── Callbacks BLE ────────────────────────────────────────────────────────
class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *server) override {
    gConnected = true;
    gMtu = 23;
    Serial.println("[BLE] Celular conectado");
  }
  void onMtuChanged(BLEServer *server, ble_gap_conn_desc *desc, uint16_t mtu) override {
    gMtu = mtu;
    Serial.printf("[BLE] MTU negociado: %u\n", mtu);
  }
  void onDisconnect(BLEServer *server) override {
    gConnected = false;
    gSubscribed = false;
    gMtu = 23;
    gNeedAdvertise = true;
    Serial.println("[BLE] Celular desconectado");
  }
};

class TxCallbacks : public BLECharacteristicCallbacks {
  void onSubscribe(BLECharacteristic *c, ble_gap_conn_desc *desc, uint16_t subValue) override {
    gSubscribed = (subValue & 0x0001) != 0;
    Serial.printf("[BLE] Notificaciones %s\n", gSubscribed ? "activadas" : "desactivadas");
  }
};

class RxCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *c) override {
    String v = c->getValue();
    Serial.printf("[BLE] RX <- %s\n", v.c_str());
    if (!gBleCmdReady) {
      gBleCmd = v;
      gBleCmdReady = true;
    }
  }
};

static void startAdvertising() {
  BLEAdvertising *adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(NUS_SERVICE_UUID);
  adv->setScanResponse(true); // el nombre va también en la respuesta de escaneo
  BLEDevice::startAdvertising();
  Serial.println("[BLE] Anunciando como \"" DEVICE_NAME "\". Esperando conexion...");
}

static void setupBle() {
  BLEDevice::init(DEVICE_NAME);
  gServer = BLEDevice::createServer();
  gServer->setCallbacks(new ServerCallbacks());

  BLEService *svc = gServer->createService(NUS_SERVICE_UUID);

  gTx = svc->createCharacteristic(NUS_TX_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  gTx->setCallbacks(new TxCallbacks()); // NimBLE crea el CCCD (0x2902) automáticamente

  BLECharacteristic *rx = svc->createCharacteristic(
      NUS_RX_UUID, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  rx->setCallbacks(new RxCallbacks());

  svc->start();
  startAdvertising();
}

// ─── Entradas ─────────────────────────────────────────────────────────────
static void pollButton() {
  int r = digitalRead(BUTTON_PIN);
  uint32_t now = millis();
  if (r != gButtonLastRead) {
    gButtonLastRead = r;
    gButtonChangedMs = now;
  }
  if (now - gButtonChangedMs >= DEBOUNCE_MS && r != gButtonStable) {
    gButtonStable = r;
    if (gButtonStable == LOW) firePanic("BOTON");
  }
}

static void pollSerial() {
  while (Serial.available()) {
    char ch = (char)Serial.read();
    if (ch == '\n' || ch == '\r') {
      if (gSerialLine.length()) handleCommand(gSerialLine, false);
      gSerialLine = "";
    } else if (gSerialLine.length() < 64) {
      gSerialLine += ch;
    }
  }
}

static void updateLed() {
  uint32_t now = millis();
  if (now < gLedFlashUntil) {
    ledWrite((now / 80) % 2);          // parpadeo rápido: pánico enviado
  } else if (gSubscribed) {
    ledWrite(true);                    // fijo: app conectada y escuchando
  } else {
    ledWrite((now % 2000) < 100);      // destello cada 2 s: anunciando
  }
}

// ─── Arduino ──────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  pinMode(LED_PIN, OUTPUT);
  ledWrite(false);

  uint32_t t0 = millis();
  while (!Serial && millis() - t0 < 1500) delay(10);

  Serial.println();
  Serial.println("[GIBBOR] Boot fw " FW_VERSION " (" __DATE__ " " __TIME__ ")");

  gButtonStable = gButtonLastRead = digitalRead(BUTTON_PIN);
  setupBle();
  Serial.printf("[GIBBOR] MAC %s, boton en GPIO%d, LED en GPIO%d\n",
                macString().c_str(), BUTTON_PIN, LED_PIN);
}

void loop() {
  pollButton();
  pollSerial();

  if (gBleCmdReady) {
    String cmd = gBleCmd;
    gBleCmdReady = false;
    handleCommand(cmd, true);
  }

  if (gNeedAdvertise) {
    gNeedAdvertise = false;
    delay(300); // deja que la pila BLE termine la desconexión
    startAdvertising();
  }

  flushPendingTrigger();
  updateLed();
  delay(5);
}
