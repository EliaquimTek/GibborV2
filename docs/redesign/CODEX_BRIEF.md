# CODEX BRIEF — Rediseño UI de GIBBOR (Android / Jetpack Compose)

Eres el implementador. El arquitecto (Claude) revisará tu trabajo. Implementa TODO lo de este documento
y al final ejecuta la compilación. No preguntes; si algo es ambiguo, elige la opción más conservadora.

## 0. Contexto

GIBBOR es un **botón de pánico**: al pulsar un botón físico (ESP32-C3 por BLE) la app toma GPS+hora,
genera un hash, lo registra on-chain en Stellar/Soroban (vía backend Crossmint), y empieza a grabar
audio y video; el SHA-256 de cada archivo se ancla on-chain. Hackatón UTEZ (México). Público: personas
en riesgo. La UI debe transmitir **seguridad, calma y confianza**, no "app de juegos".

Proyecto: `android/` (paquete `mx.edu.utez.gibbor`, Compose + Material3, minSdk 24, compileSdk 36).
Capa UI: `android/app/src/main/java/mx/edu/utez/gibbor/presentation/`.

Logo oficial: `docs/redesign/gibbor_logo.webp` (alebrije-gato azul turquesa con flores moradas/rosas
pulsando un botón rojo, fondo azul marino `#0B1046` aprox.).

## 1. REGLAS DURAS (no negociables)

1. **NO cambies lógica de negocio.** No toques `data/`, `domain/`, `core/`, el backend, ni el contrato.
2. En `AppScreen.kt`, la función `createAndSendIncident(...)` y el `LaunchedEffect(controller.triggerCounter)`
   deben conservar **exactamente** su comportamiento (mismos pasos, mismos `appendLog`, mismas condiciones,
   mismo uso de `busy`, `authStatus`, `onChainStatus`, `incidentPreview`, `lastHandledTrigger`).
   Puedes moverlas de sitio, pero el `LaunchedEffect` del ESP32 debe seguir activo **sin importar la
   pestaña visible** (vive en el nivel superior de `AppScreen`, no dentro de una pestaña).
3. Los textos que se escriben al log (`appendLog`) NO cambian.
4. El login (correo → "código" local → iniciar sesión → `controller.startSession(norm)`) conserva la misma
   lógica (incluida la normalización del correo). Solo cambia su apariencia.
5. Mantén `MainScreenController` como único puente UI↔Activity. Solo **agregas** miembros (ver §4).
6. **No agregues dependencias nuevas** a Gradle (todo con Compose/Material3/animation ya presentes).
   Si de verdad necesitas `material-icons-extended`, NO lo agregues: dibuja los íconos con Canvas o texto.
7. No hagas commits de git.
8. Debe compilar: `android\gradlew.bat -p android assembleDebug` (Windows) sin errores.

## 2. Identidad visual (design system)

Tema **oscuro** derivado del logo. Reemplaza `ui/theme/Color.kt`, `Theme.kt` (darkColorScheme) y ajusta
`Type.kt`. Paleta (tokens):

| Token | Hex | Uso |
|---|---|---|
| `Ink` | `#070A24` | fondo base |
| `Night` | `#0B1046` | fondo secundario / gradiente |
| `Surface` | `#121845` | tarjetas |
| `SurfaceHigh` | `#1A2160` | tarjetas elevadas, campos |
| `Stroke` | `#2A3275` | bordes 1dp |
| `TextHi` | `#F2F4FF` | texto principal |
| `TextMid` | `#A9B0D6` | texto secundario |
| `TextLow` | `#6E76A8` | etiquetas |
| `Cyan` | `#2EC4E6` | acento de marca, estados "conectado/ok" |
| `Panic` | `#FF2D6F` | botón de pánico, grabación |
| `PanicDeep` | `#C8124F` | gradiente del botón |
| `Violet` | `#8B3FD9` | acento secundario |
| `Lime` | `#7ED957` | éxito/confirmado on-chain |
| `Amber` | `#FF9F1C` | pendiente/advertencia |

- Fondo de pantalla: gradiente vertical `Ink → Night` con 2–3 "glows" radiales muy sutiles (cyan y violet,
  alpha ≤ 0.12) dibujados con `drawBehind`.
- Tarjetas: `Surface`, borde 1dp `Stroke`, radio 20dp, padding 20dp.
- Tipografía: sistema. Títulos `FontWeight.SemiBold`, etiquetas en MAYÚSCULAS con `letterSpacing` 1.5sp,
  datos técnicos (hashes, tx) en `FontFamily.Monospace` 11–12sp con `TextMid`.
- Textos de UI en **español** (México). Ej.: "Sesión activa", "Crear incidente", "Detener grabación".
- Status bar / nav bar transparentes u oscuras acordes (`enableEdgeToEdge` si ya está disponible en
  activity-compose; aplica `WindowInsets` paddings correctos).

## 3. Logo

1. Copia `docs/redesign/gibbor_logo.webp` a `android/app/src/main/res/drawable-nodpi/gibbor_logo.webp`.
2. Úsalo en: splash animado, cabecera (avatar circular 40dp con borde `Cyan` 1.5dp) y en la pestaña DEMO.
3. Ícono del launcher: actualiza el adaptive icon (`mipmap-anydpi-v26/ic_launcher*.xml`) para usar
   `@drawable/gibbor_logo` como foreground con un `<inset android:inset="16%">` (archivo
   `drawable/ic_launcher_foreground_logo.xml`) y fondo color `#0B1046`
   (`values/colors.xml` → `ic_launcher_bg`). Deja los `.webp` legacy existentes.
4. `strings.xml`: `app_name` = `GIBBOR`.

## 4. Cambios en el controller (únicos cambios permitidos fuera de `ui/`)

`MainScreenController.kt` — agrega:

```kotlin
val demoTriggerCounter: Int
val lastDemoKey: String          // nombre del último keycode recibido en modo DEMO (p.ej. "KEYCODE_VOLUME_UP")
var isDemoActive: Boolean        // true cuando la pestaña DEMO está visible
fun clearLog()                   // CLS: borra SOLO el texto del log en la interfaz
```

`MainActivity.kt` — implementa:

- `override var demoTriggerCounter by mutableIntStateOf(0); private set`
- `override var lastDemoKey by mutableStateOf(""); private set`
- `override var isDemoActive by mutableStateOf(false)`
- `override fun clearLog() { logText = "" }` (no toca archivos, ni backend, ni grabaciones).
- `override fun dispatchKeyEvent(event: KeyEvent): Boolean`:
  - Si `isDemoActive` y el keycode está en el set del control TikTok/selfie
    (`VOLUME_UP, VOLUME_DOWN, ENTER, NUMPAD_ENTER, DPAD_CENTER, SPACE, MEDIA_PLAY_PAUSE, MEDIA_PLAY,
    MEDIA_PAUSE, MEDIA_NEXT, MEDIA_PREVIOUS, CAMERA, PAGE_UP, PAGE_DOWN, DPAD_UP, DPAD_DOWN, DPAD_LEFT,
    DPAD_RIGHT`):
    - en `ACTION_DOWN` con `repeatCount == 0`: `lastDemoKey = KeyEvent.keyCodeToString(keyCode)`,
      `demoTriggerCounter++`;
    - consume el evento (devuelve `true`) tanto en DOWN como en UP, para que el volumen no cambie.
  - En cualquier otro caso: `return super.dispatchKeyEvent(event)` (fuera de DEMO, todo igual que hoy).
- Nada más cambia en `MainActivity`.

## 5. Estructura de pantallas

Nuevos archivos sugeridos (en `presentation/ui/`):

- `screen/AppScreen.kt` — contenedor: estado compartido + flujo real (sin cambios de lógica) + `Scaffold`
  con barra inferior de 2 pestañas + `Crossfade`/`AnimatedContent` entre pestañas.
- `screen/OperationalScreen.kt` — pestaña "GIBBOR" (operativa).
- `screen/DemoScreen.kt` — pestaña "DEMO".
- `screen/SplashOverlay.kt` — splash animado.
- `components/` — `PanicButton.kt`, `StatusChip.kt`, `LogConsole.kt`, `GibborBottomBar.kt`,
  `GibborBackground.kt`, y actualiza `GibborComponents.kt` al nuevo estilo (mantén las firmas públicas
  existentes o elimina las que queden sin uso, p. ej. `OTPDialog`).

Pasa a las pestañas el estado/lambdas que necesiten (state hoisting). El estado del flujo
(`busy`, `authStatus`, `onChainStatus`, `incidentPreview`, `backendUrl`, `email`, `otpCode`, `otpRequested`)
sigue declarado en `AppScreen`, para que no se pierda al cambiar de pestaña.

`isDemoActive`: en `AppScreen`, `LaunchedEffect(selectedTab) { controller.isDemoActive = selectedTab == DEMO }`.

### 5.1 Splash (≈1.6 s, solo al abrir)

Fondo `Ink`; el logo aparece con scale 0.7→1.0 (spring, dampingRatio 0.6) + fade-in, un anillo `Cyan`
que se expande y desvanece detrás, y debajo "GIBBOR" (letterSpacing 10sp) + "Tu evidencia, inmutable"
con fade escalonado. Luego fade-out del overlay. Implementado como overlay dentro de `AppScreen`.

### 5.2 Barra inferior

2 pestañas: **GIBBOR** (ícono: escudo dibujado con Canvas) y **DEMO** (ícono: círculo/botón). Fondo
`Night` con borde superior `Stroke`; indicador animado (pastilla `Cyan` alpha 0.15 que se desliza con
`animateDpAsState`). Pestaña seleccionada en `TextHi`, otra en `TextLow`.

### 5.3 Pestaña GIBBOR (operativa) — orden vertical, con scroll

1. **Header**: logo circular 40dp + "GIBBOR" / "Sistema de emergencia". A la derecha, si hay sesión, un
   punto `Lime` con pulso suave.
2. **Login** (si `!controller.isAuthenticated`): tarjeta "Acceso" con el mismo flujo: campo correo
   (`OutlinedTextField` estilizado oscuro), botón "Obtener wallet", campo de código (readOnly, como hoy),
   botón "Iniciar sesión". Transición con `AnimatedVisibility` cuando aparece el código.
3. **Sesión activa** (si autenticado): tarjeta compacta: "SESIÓN ACTIVA" + correo.
4. **Fila de chips de estado** (`StatusChip`): "Botón" (texto derivado de `controller.statusText`:
   Cyan si contiene "Connected"/"Conectado" y no "Disconnected", Amber si `isConnecting`, TextLow si no),
   "Grabando" (Panic, solo si `isRecording`), "Red" (Lime si el último `onChainStatus` empieza con ✅,
   Amber si contiene "pending"/"Sending", Panic si contiene "Error"/❌).
5. **Botón de pánico principal** (`PanicButton`, 200dp, centrado) — reemplaza a "CREATE INCIDENT" y
   ejecuta **el mismo onClick** (`busy=true; createAndSendIncident("MANUAL"); …`), habilitado solo si
   autenticado y `!busy`.
   - Reposo: círculo con gradiente radial `Panic → PanicDeep`, sombra/glow `Panic` alpha 0.35,
     2 anillos concéntricos que "respiran" (infiniteTransition, 2.4 s, scale 1.0→1.25, alpha 0.35→0).
   - Pulsado: scale 0.94 (spring) + `HapticFeedbackType.LongPress`.
   - `busy`: arco `Cyan` girando alrededor (Canvas, 1.1 s/vuelta) y texto "Procesando…".
   - Grabando: el centro muestra "REC" + cronómetro mm:ss (cuenta desde que `isRecording` pasó a true).
   - No autenticado: desaturado (alpha 0.4) y texto "Inicia sesión".
   - Debajo: "Pulsa aquí o el botón físico GIBBOR".
6. **Tarjeta de grabación** (si `isRecording`): borde `Panic`, punto rojo parpadeante, "GRABANDO EVIDENCIA",
   cronómetro, mini-visualizador de 5 barras animadas, y botón "Detener grabación" con **exactamente** el
   mismo onClick que hoy (`stopAudioRecording()` + `sendEvidenceToBackend(..., "audio", hash)` + logs).
7. **Estado de transacción** (si `onChainStatus` no vacío): tarjeta con ícono de estado coloreado
   (Lime/Amber/Panic según el emoji, como hoy se filtran los emojis del texto) + texto monospace.
8. **Último incidente** (igual condición que hoy): monospace.
9. **Desplegables** (`GibborCollapsible` rediseñado: chevron que rota 180° animado):
   - "Configuración": campo Backend URL.
   - "Bluetooth": `statusText`, "Activar Bluetooth", "Conectar botón GIBBOR" (texto "Conectando…" si
     `isConnecting`), "Desconectar". Mismos onClick.
   - "Registro de actividad": `LogConsole` — consola estilo terminal (fondo `#05071A`, texto monospace
     11sp `#B8F5C8`/`TextMid`, altura máx 280dp con scroll propio que hace auto-scroll al final cuando
     cambia `logText`). En su cabecera un botón **`CLS`** (pill, borde `Stroke`, texto monospace) que
     llama `controller.clearLog()` con una animación breve de "barrido" (fade del texto). Si el log está
     vacío, mostrar "— registro vacío —" en `TextLow`.

### 5.4 Pestaña DEMO

Propósito: demostración en vivo con el control remoto Bluetooth de TikTok/selfie (Beauty-R1), que el
teléfono ve como teclado. **Es 100 % simulada**: NO llama al backend, NO graba, NO toca `triggerCounter`
ni `createAndSendIncident`. Debe decirlo discretamente ("Modo demostración · sin envío real").

Layout (sin scroll, centrado, usa toda la altura):
1. Título "DEMO" + subtítulo "Así funcionará GIBBOR".
2. Indicador de control: chip "Control remoto: esperando pulsación" → al recibir una tecla muestra
   "Control remoto: <controller.lastDemoKey>" en Cyan.
3. **Botón gigante** (≈ 68 % del ancho, máx 300dp) con el logo dentro (recortado circular, sobre gradiente
   Panic), mismos anillos de respiración pero más amplios. Se activa por toque **o** cuando
   `controller.demoTriggerCounter` cambia (usar `LaunchedEffect(controller.demoTriggerCounter)` con un
   `lastHandledDemo` local para ignorar el valor inicial).
4. Al activarse, secuencia animada (si ya hay una corriendo, reiníciala):
   - onda expansiva (3 anillos Panic que salen del botón, 900 ms) + haptic;
   - fondo hace un flash rojo sutil (alpha 0.15, 300 ms);
   - lista de 5 pasos que se van marcando uno a uno cada ~700 ms con check animado (Lime) y spinner
     mientras está en curso:
     1. "Ubicación GPS capturada"
     2. "Huella SHA-256 generada"
     3. "Incidente anclado en Stellar"
     4. "Grabando audio y video"
     5. "Contactos de confianza alertados"
   - al terminar: tarjeta "Alerta enviada" con un hash de ejemplo (genera 64 hex aleatorios en la UI) y
     botón "Reiniciar demo".
5. Pie: "Empareja el control en Ajustes › Bluetooth. Cualquier botón del control activa la demo."

## 6. Calidad / animación

- Animaciones con `animateFloatAsState`, `rememberInfiniteTransition`, `AnimatedVisibility`,
  `AnimatedContent`, `Crossfade`, `spring`. 60 fps: nada pesado en recomposición (usa `graphicsLayer`
  y `drawBehind` para transformaciones animadas).
- Accesibilidad: `contentDescription` en logo e íconos; el botón de pánico con
  `Modifier.semantics { role = Role.Button; contentDescription = "Botón de pánico" }`; contraste AA.
- Tamaño táctil mínimo 48dp. Nada debe desbordar en pantallas de 360dp de ancho.
- Sin `@Preview` obligatorios, pero si agregas previews deben compilar.
- Borra imports sin uso.

## 7. Entrega

1. Ejecuta `android\gradlew.bat -p android assembleDebug` y corrige hasta que compile.
2. Al final imprime un resumen: archivos creados/modificados y cualquier desviación de este brief con su
   motivo.
