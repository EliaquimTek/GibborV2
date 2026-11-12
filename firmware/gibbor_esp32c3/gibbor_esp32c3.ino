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
#include <esp_system.h>

// ─── Configuración ────────────────────────────────────────────────────────
#define FW_VERSION "1.2.0"
#define DEVICE_NAME "GIBBOR_ESP32"

// Botones (activos en bajo, con pull-up interno). Cualquiera dispara el pánico:
//   BUTTON_PIN: botón rojo externo del prototipo, soldado al pad 4 de la SuperMini.
//               Debe ir entre GPIO4 y GND; la otra pata NUNCA a 3.3 V.
//   BOOT_PIN:   botón BOOT de la placa (útil para pruebas).
#ifndef BUTTON_PIN
#define BUTTON_PIN 4
#endif
#ifndef BOOT_PIN
#define BOOT_PIN 9
#endif
//   ALT_BUTTON_PIN: pad libre alternativo para resoldar el botón (GPIO10 + GND)
//                   si el pad 4 queda dañado.
#ifndef ALT_BUTTON_PIN
#define ALT_BUTTON_PIN 10
#endif
//   BUTTON2_PIN: placa B (GibborV2 en protoboard): botón rojo entre el riel de 3.3 V
//                y GPIO3, con resistencia pull-down a GND → activo en ALTO.
#ifndef BUTTON2_PIN
#define BUTTON2_PIN 3
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
static const uint32_t PENDING_TRIGGER_TTL_MS = 5UL * 60UL * 1000UL; // pánico sin conexión: se entrega al reconectar (5 min)

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

// Polaridad de los botones externos:
//   -1 = automática (al arrancar): si el pin lee GND aun con el pull-up interno
//        activo, hay una resistencia pull-down externa → botón a 3.3 V, activo en ALTO.
//        En otro caso → botón a GND, activo en BAJO.
//    0 = forzar activo en BAJO (botón entre GPIO y GND)
//    1 = forzar activo en ALTO (botón entre GPIO y 3.3 V, con pull-down)
#ifndef BUTTON_ACTIVE_HIGH
#define BUTTON_ACTIVE_HIGH -1
#endif

struct Button {
  int pin;
  const char *name;
  bool external;    // BOOT siempre es activo en bajo; los externos pueden ir a 3.3 V
  int pressedLevel; // LOW o HIGH, se decide en configureButtons()
  int stable;
  int lastRead;
  uint32_t changedMs;
};
static Button gButtons[] = {
    {BUTTON_PIN, "BOTON", true, LOW, HIGH, HIGH, 0},
    {BUTTON2_PIN, "BOTON_B", true, LOW, HIGH, HIGH, 0},
    {BOOT_PIN, "BOOT", false, LOW, HIGH, HIGH, 0},
    {ALT_BUTTON_PIN, "BOTON_ALT", true, LOW, HIGH, HIGH, 0},
};
static const int BUTTON_COUNT = sizeof(gButtons) / sizeof(gButtons[0]);

static String gBleCmd;          // comando recibido por BLE (procesado en loop)
static volatile bool gBleCmdReady = false;
static String gSerialLine;

static uint32_t gLedFlashUntil = 0;

static volatile uint16_t gMtu = 23; // MTU ATT negociado con la app
static size_t gSimChunk = 0;        // SIMMTU (solo pruebas): limita el trozo

// ─── Utilidades ───────────────────────────────────────────────────────────
// Motivo del último reinicio: distingue fallas de alimentación (hardware) de
// crashes o watchdogs (software).
static const char *resetReasonName() {
  switch (esp_reset_reason()) {
    case ESP_RST_POWERON:   return "POWERON";   // se conectó/cortó la alimentación
    case ESP_RST_BROWNOUT:  return "BROWNOUT";  // voltaje insuficiente
    case ESP_RST_PANIC:     return "PANIC";     // crash del firmware
    case ESP_RST_INT_WDT:   return "INT_WDT";   // watchdog de interrupciones
    case ESP_RST_TASK_WDT:  return "TASK_WDT";  // watchdog de tareas
    case ESP_RST_WDT:       return "WDT";
    case ESP_RST_SW:        return "SW";        // esp_restart()
    case ESP_RST_USB:       return "USB";       // reset pedido por el host USB (DTR/RTS)
    case ESP_RST_DEEPSLEEP: return "DEEPSLEEP";
    case ESP_RST_EXT:       return "EXT";       // pin de reset
    default:                return "OTHER";
  }
}

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

// ─── Diagnóstico de cableado ─────────────────────────────────────────────
// SCAN <segundos>: lee los GPIO libres alternando pull-up y pull-down y reporta
// cambios. Así se averigua a qué pin está soldado un botón externo y si es
// activo en bajo (a GND) o en alto (a 3.3 V), o si un pin está en corto.
// No toca GPIO11-17 (flash) ni GPIO18/19 (USB).
static const int SCAN_PINS[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 21};
static const int SCAN_N = sizeof(SCAN_PINS) / sizeof(SCAN_PINS[0]);

static void applyButtonPull(const Button &b) {
  pinMode(b.pin, b.pressedLevel == HIGH ? INPUT_PULLDOWN : INPUT_PULLUP);
}

static const char *polarityName(const Button &b) {
  return b.pressedLevel == HIGH ? "alto" : "bajo";
}

// Decide la polaridad de cada botón y deja el pull interno correcto.
static void configureButtons() {
  for (int i = 0; i < BUTTON_COUNT; i++) {
    Button &b = gButtons[i];
    int level = LOW;
    if (b.external && BUTTON_ACTIVE_HIGH == 1) {
      level = HIGH;
    } else if (b.external && BUTTON_ACTIVE_HIGH == -1) {
      pinMode(b.pin, INPUT_PULLUP);
      delay(3);
      int up = digitalRead(b.pin);
      pinMode(b.pin, INPUT_PULLDOWN);
      delay(3);
      int down = digitalRead(b.pin);
      // up=0 y down=0: algo externo jala el pin a GND más fuerte que el pull-up
      // interno (~45 kΩ): una pull-down de un botón a 3.3 V
      if (up == LOW && down == LOW) level = HIGH;
      Serial.printf("[GIBBOR] %s (GPIO%d) deteccion up=%d down=%d -> activo en %s\n", b.name, b.pin, up,
                    down, level == HIGH ? "ALTO (boton a 3.3V + pull-down)" : "BAJO (boton a GND)");
    }
    b.pressedLevel = level;
    applyButtonPull(b);
  }
  delay(3);
  for (int i = 0; i < BUTTON_COUNT; i++) {
    Button &b = gButtons[i];
    b.stable = b.lastRead = digitalRead(b.pin);
    b.changedMs = millis();
    if (b.stable == b.pressedLevel) {
      Serial.printf("[GIBBOR] AVISO: %s (GPIO%d) se lee presionado al arrancar: atorado o en corto\n", b.name,
                    b.pin);
    }
  }
}

static String buttonPinsInfo() {
  String s;
  for (int i = 0; i < BUTTON_COUNT; i++) {
    if (i) s += "+";
    s += String("GPIO") + gButtons[i].pin + "(" + polarityName(gButtons[i]) + ")";
  }
  return s;
}

static String buttonStates() {
  String s;
  for (int i = 0; i < BUTTON_COUNT; i++) {
    const Button &b = gButtons[i];
    s += String(" btn") + b.pin + "=" + (digitalRead(b.pin) == b.pressedLevel ? "ON" : "off");
  }
  return s;
}

static void scanSample(int up[], int down[]) {
  for (int i = 0; i < SCAN_N; i++) pinMode(SCAN_PINS[i], INPUT_PULLUP);
  delay(2);
  for (int i = 0; i < SCAN_N; i++) up[i] = digitalRead(SCAN_PINS[i]);
  for (int i = 0; i < SCAN_N; i++) pinMode(SCAN_PINS[i], INPUT_PULLDOWN);
  delay(2);
  for (int i = 0; i < SCAN_N; i++) down[i] = digitalRead(SCAN_PINS[i]);
}

static void pinScan(uint32_t seconds) {
  int up[SCAN_N], down[SCAN_N], up0[SCAN_N], down0[SCAN_N];
  scanSample(up0, down0);
  Serial.println("SCAN inicio (up/down: 1/0=libre, 0/0=a GND, 1/1=a 3.3V)");
  for (int i = 0; i < SCAN_N; i++) {
    Serial.printf("SCAN base GPIO%d up=%d down=%d\n", SCAN_PINS[i], up0[i], down0[i]);
  }
  uint32_t end = millis() + seconds * 1000UL;
  while (millis() < end) {
    scanSample(up, down);
    for (int i = 0; i < SCAN_N; i++) {
      if (up[i] != up0[i] || down[i] != down0[i]) {
        Serial.printf("SCAN t=%lu GPIO%d up=%d down=%d (antes up=%d down=%d)\n",
                      (unsigned long)millis(), SCAN_PINS[i], up[i], down[i], up0[i], down0[i]);
        up0[i] = up[i];
        down0[i] = down[i];
      }
    }
    delay(10);
  }
  // Restaurar la configuración normal
  for (int i = 0; i < SCAN_N; i++) pinMode(SCAN_PINS[i], INPUT);
  for (int b = 0; b < BUTTON_COUNT; b++) applyButtonPull(gButtons[b]);
  pinMode(LED_PIN, OUTPUT);
  Serial.println("SCAN fin");
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
              " btn=" + buttonPinsInfo() + " uptime_s=" + (millis() / 1000) + " reset=" + resetReasonName(),
          viaBle);
  } else if (cmd == "STATUS") {
    if (gConnected) chunkSize(); // refresca gMtu con el MTU real del enlace
    reply(String("STATUS connected=") + (gConnected ? 1 : 0) +
              " subscribed=" + (gSubscribed ? 1 : 0) +
              " triggers=" + gTriggerCount +
              " pending=" + (gPendingTrigger ? 1 : 0) +
              " mtu=" + gMtu + buttonStates(),
          viaBle);
  } else if (cmd == "TRIGGER") {
    firePanic(viaBle ? "BLE" : "SERIAL");
  } else if (!viaBle && cmd.startsWith("SIMMTU")) {
    // Solo por Serial: simula un MTU pequeño (p. ej. SIMMTU 23); SIMMTU 0 lo desactiva
    int mtu = cmd.substring(6).toInt();
    gSimChunk = mtu > 3 ? (size_t)(mtu - 3) : 0;
    reply(String("SIMMTU chunk=") + gSimChunk, false);
  } else if (!viaBle && cmd == "DROP") {
    // Solo por Serial: corta la conexión como si el teléfono se alejara (pruebas de reconexión)
    if (gConnected && gServer) {
      gServer->disconnect(gServer->getConnId());
      reply("DROP ok", false);
    } else {
      reply("DROP no_connection", false);
    }
  } else if (!viaBle && cmd.startsWith("SCAN")) {
    int secs = cmd.substring(4).toInt();
    pinScan(secs > 0 && secs <= 120 ? secs : 30);
  } else if (cmd == "HELP") {
    reply("CMDS: PING INFO STATUS TRIGGER SIMMTU DROP HELP", viaBle);
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
static void pollButtons() {
  uint32_t now = millis();
  for (int i = 0; i < BUTTON_COUNT; i++) {
    Button &b = gButtons[i];
    int r = digitalRead(b.pin);
    if (r != b.lastRead) {
      b.lastRead = r;
      b.changedMs = now;
    }
    if (now - b.changedMs >= DEBOUNCE_MS && r != b.stable) {
      b.stable = r;
      bool pressed = r == b.pressedLevel;
      Serial.printf("[GIBBOR] %s (GPIO%d) %s\n", b.name, b.pin, pressed ? "presionado" : "suelto");
      if (pressed) firePanic(b.name);
    }
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
  pinMode(LED_PIN, OUTPUT);
  ledWrite(false);

  uint32_t t0 = millis();
  while (!Serial && millis() - t0 < 1500) delay(10);

  Serial.println();
  Serial.println("[GIBBOR] Boot fw " FW_VERSION " (" __DATE__ " " __TIME__ ")");
  Serial.printf("[GIBBOR] Motivo del reinicio: %s\n", resetReasonName());

  configureButtons();
  setupBle();
  Serial.printf("[GIBBOR] MAC %s, botones %s, LED en GPIO%d\n", macString().c_str(), buttonPinsInfo().c_str(),
                LED_PIN);
}

void loop() {
  pollButtons();
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
