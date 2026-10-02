# CODEX BRIEF 3 — Rediseño profesional de GIBBOR (nivel Google / Apple)


## 0. Visión

GIBBOR es un botón de pánico que registra la evidencia de forma inmutable en blockchain.
La persona que lo usa puede estar **asustada, con prisa y con una sola mano libre**. La interfaz debe
sentirse como un producto de seguridad de primer nivel (piensa en *Emergency SOS* de Apple, *Personal Safety*
de Google Pixel, *Find My*): **calma, claridad, confianza**. Nada de estética "gamer" o de neón.

Tres palabras guía: **Sereno · Inmediato · Confiable.**

El logo (alebrije) es el único elemento colorido y expresivo: la UI es neutra y precisa para que el logo
destaque. Los colores del logo **no** son la paleta de la app.

---

## 1. Reglas duras (no negociables)

1. **Cero cambios de lógica.** No tocar `data/`, `domain/`, `core/`, backend ni contrato.
2. En `AppScreen.kt` NO cambian: `createAndSendIncident(...)`, `handleHardwareTrigger(...)` (incluida la guarda
   `busy, trigger ignored`, que se aplica también a ESP32 y queda aceptada), los `LaunchedEffect` de
   `triggerCounter` y `demoTriggerCounter` (en el nivel superior, activos en cualquier pantalla), el login
   (normalización + `startSession`), el lambda de detener grabación y el envío de evidencia.
3. Los textos de `appendLog(...)` no cambian (son el registro técnico).
4. DEMO conserva lo de BRIEF 2: flujo real (REMOTE/DEMO), cámara frontal, vista previa selfie
   (`CameraPreviewHolder`), pasos derivados del estado real, antirrebote 1500 ms.
5. `MainScreenController` solo puede **ganar** miembros, nunca perderlos ni cambiar firmas.
6. Dependencias nuevas permitidas (y solo estas):
   - `androidx.core:core-splashscreen:1.0.1`
   - `androidx.compose.material:material-icons-extended` (vía BOM, sin versión)
   - Fuente **Inter** (licencia OFL) empaquetada en `res/font/` (`inter_regular.ttf`, `inter_medium.ttf`,
     `inter_semibold.ttf`, `inter_bold.ttf`) **si puedes descargarla** de
     `https://github.com/rsms/inter` (releases). Si no tienes red, usa `FontFamily.Default` con la escala
     tipográfica de §3.3 y repórtalo como desviación.
7. Compila: `android\gradlew.bat -p android assembleDebug` sin errores ni warnings nuevos de Compose.

---

## 2. Principios UX/UI que deben verse aplicados

| Principio | Aplicación concreta en GIBBOR |
|---|---|
| **Visibilidad del estado del sistema** (Nielsen #1) | Tarjeta "Estado de protección" siempre visible: botón GIBBOR, ubicación, cámara/micrófono, red. Cada ítem con ícono + texto + color semántico. |
| **Ley de Fitts / zona del pulgar** | El botón SOS es el objetivo más grande y está en el tercio inferior-central de la pantalla. |
| **Ley de Hick** | Una sola acción primaria por pantalla. Lo técnico (URL, log, hashes) vive en Ajustes o en "Detalles técnicos" plegable. |
| **Prevención de errores** (Nielsen #5) | SOS en pantalla = **mantener presionado 1.2 s** con anillo de progreso (soltar antes cancela). Detener grabación = mantener 1.5 s. Los botones físicos (ESP32/remoto) disparan al instante como hoy. |
| **Divulgación progresiva** | Hash, TX, explorer y lat/lon detrás de "Detalles técnicos". La persona ve primero "Incidente registrado ✓". |
| **Reconocer antes que recordar** | Íconos Material Symbols Rounded + etiqueta siempre (nunca ícono solo). |
| **Feedback inmediato** | Haptic en: inicio de hold, disparo, éxito, error. Transición visible < 100 ms tras cada toque. |
| **Consistencia y estándares** | Material 3, patrones nativos Android (TopAppBar, NavigationBar, ModalBottomSheet, Snackbar). |
| **Ley de Jakob** | Flujos como en apps conocidas: onboarding → permisos → inicio. |
| **Estética minimalista** | Mucho aire (múltiplos de 8dp), máx. 2 pesos tipográficos por pantalla, sin bordes innecesarios. |
| **Accesibilidad** | Contraste ≥ 4.5:1, objetivos ≥ 48dp, TalkBack con descripciones y orden lógico, soporta escala de fuente 200 % sin cortar texto, respeta "Quitar animaciones" del sistema. |

---

## 3. Sistema de diseño (reemplaza `ui/theme/*`)

### 3.1 Color — neutro, claro y oscuro (sigue al sistema)

| Token | Claro | Oscuro | Uso |
|---|---|---|---|
| `background` | `#F5F5F7` | `#000000` | fondo |
| `surface` | `#FFFFFF` | `#1C1C1E` | tarjetas |
| `surfaceVariant` | `#EDEDF0` | `#2C2C2E` | campos, chips |
| `outline` | `#D2D2D7` | `#3A3A3C` | divisores 0.5–1dp |
| `onBackground` | `#1D1D1F` | `#F5F5F7` | texto principal |
| `onSurfaceVariant` | `#6E6E73` | `#98989D` | texto secundario |
| `primary` | `#0A63F5` | `#4D8DFF` | acciones normales, enlaces, foco |
| `sos` | `#E5243B` | `#FF453A` | SOS, grabación, peligro |
| `sosContainer` | `#FDE7EA` | `#3A1215` | fondo de alerta |
| `success` | `#1E9E5A` | `#30D158` | confirmado on-chain |
| `warning` | `#C77700` | `#FF9F0A` | pendiente |

- Rojo **solo** para SOS/grabación/error. Azul solo para acciones secundarias. Nada más tiene color.
- `GibborTheme` usa `lightColorScheme`/`darkColorScheme` según `isSystemInDarkTheme()`; los tokens extra
  (`sos`, `success`, `warning`…) via `staticCompositionLocalOf<GibborExtendedColors>` + `GibborTheme.colors`.
- Status/navigation bars con íconos claros/oscuros según el tema (`enableEdgeToEdge`).

### 3.2 Forma, espaciado y elevación
- Radios: 12dp (campos/chips), 20dp (tarjetas), 28dp (sheets), círculo (SOS).
- Espaciado base 8dp; márgenes de pantalla 20dp; separación entre secciones 24–32dp.
- Elevación: tarjetas planas (0dp) con `surface` sobre `background`; sombras solo en el botón SOS
  (sombra difusa del color `sos` al 25 %).

### 3.3 Tipografía (Inter, o Default si no hay red)
| Estilo | Tamaño/alto | Peso | Tracking |
|---|---|---|---|
| displaySmall | 34/40 | Bold | -0.5sp |
| headlineSmall | 24/30 | SemiBold | -0.2sp |
| titleMedium | 17/22 | SemiBold | 0 |
| bodyLarge | 17/24 | Regular | 0 |
| bodyMedium | 15/20 | Regular | 0 |
| labelLarge | 15/20 | Medium | 0 |
| labelSmall | 12/16 | Medium | 0.4sp, MAYÚSCULAS solo en "eyebrows" |
| mono (hashes) | 13/18 | `FontFamily.Monospace` | 0 |

### 3.4 Iconografía
`Icons.Rounded.*` (material-icons-extended). Tamaños 20/24dp. Ej.: `Shield`, `Bluetooth`,
`LocationOn`, `Videocam`, `Mic`, `CloudDone`, `Settings`, `PlayCircle`, `CheckCircle`, `ErrorOutline`.

### 3.5 Movimiento (Material motion)
- Duraciones: 150 ms (micro), 300 ms (componentes), 450 ms (pantallas). Easing *emphasized*
  (`CubicBezierEasing(0.2f, 0f, 0f, 1f)`); salidas *emphasized accelerate* (`0.3f,0f,0.8f,0.15f`).
- Transiciones entre pantallas: fade-through (fade + scale 0.96→1).
- Animaciones continuas (pulso del SOS, REC) sutiles: amplitud pequeña, período ≥ 2 s.
- **Reducir movimiento**: si `Settings.Global.ANIMATOR_DURATION_SCALE == 0`, desactiva animaciones
  infinitas (helper `rememberReduceMotion()`).

### 3.6 Voz y microcopy (español de México, tuteo, frases cortas)
- Calma y acción: "Mantén presionado para pedir ayuda", "Tu ubicación y video se están protegiendo".
- Nada técnico en primer nivel: "Registrado en blockchain" en vez de "TX success".
- Errores con causa + solución: "No hay conexión con el servidor. Revisa tu Wi-Fi en Ajustes."

---

## 4. Logo

`res/drawable-nodpi/gibbor_logo.webp` ya existe. Presencia obligatoria en:
1. **Splash nativo Android 12+** (`core-splashscreen`): fondo `background`, ícono = logo dentro de círculo;
   `installSplashScreen()` en `MainActivity.onCreate` antes de `super.onCreate`. Elimina el `SplashOverlay`
   anterior o conviértelo en la animación de salida (logo escala 1→1.08 y fade 300 ms).
2. **Onboarding**: logo 120dp, circular, con sombra suave, como héroe.
3. **TopAppBar de Inicio**: logo 32dp circular + wordmark "GIBBOR" (titleMedium, tracking 2sp).
4. **Centro del botón de DEMO** (como hoy, cuando no hay vista previa selfie).
5. Ícono del launcher (ya configurado; verifica que siga).

---

## 5. Arquitectura de pantallas (sigue habiendo **2 partes**: GIBBOR y DEMO)

Archivos en `presentation/ui/`: `screen/onboarding/OnboardingScreen.kt`, `screen/home/HomeScreen.kt`,
`screen/home/EmergencyActiveScreen.kt`, `screen/demo/DemoScreen.kt`, `screen/settings/SettingsSheet.kt`,
`components/` (SosButton, HoldToConfirmButton, StatusRow, ProtectionStatusCard, TechnicalDetails,
LogConsole, GibborTopBar, GibborNavigationBar), `theme/`. Borra los componentes viejos sin uso.

### 5.1 Onboarding / acceso (si `!controller.isAuthenticated`) — sin barra inferior
1. Héroe: logo + "GIBBOR" (displaySmall) + "Ayuda inmediata. Evidencia que nadie puede borrar." (bodyLarge, secundario).
2. Tres beneficios en fila vertical con ícono: "Un botón, sin desbloquear", "Ubicación y video protegidos",
   "Registro inmutable en blockchain".
3. Campo de correo (OutlinedTextField M3 con radio 12dp, `ImeAction.Done`, autofill email).
   Botón primario lleno ancho completo "Continuar" (= "Obtener wallet" actual).
4. Paso de código: `AnimatedContent` desliza al paso 2: "Tu código de acceso" mostrando el código en 6 cajas
   (solo lectura, como hoy) + "Iniciar sesión". Enlace "Cambiar correo" vuelve al paso 1.
5. Pie en `labelSmall`: "Al continuar se crea tu wallet segura en Stellar."

### 5.2 Inicio (pestaña **GIBBOR**)
- **TopAppBar** (pequeña, transparente, se colorea al hacer scroll): logo + GIBBOR a la izquierda;
  a la derecha `IconButton(Settings)` que abre **SettingsSheet**.
- Saludo: "Hola" + correo en secundario (una línea, ellipsis).
- **ProtectionStatusCard** — título "Protección activa" (si todo OK) o "Revisa tu protección" (si falta algo):
  - Botón GIBBOR: Conectado / Conectando… / Desconectado → acción "Conectar" inline.
  - Ubicación: Permitida / "Permitir".
  - Cámara y micrófono: Permitidos / "Permitir".
  - Servidor: último resultado conocido (OK / pendiente / error) derivado de `onChainStatus`.
  Los permisos se reevalúan en `ON_RESUME` (`LifecycleEventEffect` o `DisposableEffect` con observer).
  Las acciones llaman a funciones existentes del controller (`connectToEsp32`, `requestLocationPermission`,
  `requestBluetoothPermissions`).
- **SOS** centrado en el tercio inferior: círculo 208dp, relleno `sos`, texto "SOS" (displaySmall, blanco)
  y debajo "Mantén presionado". Hold 1.2 s con anillo de progreso blanco alrededor; al completar →
  haptic + ejecuta el mismo `onCreateIncident` actual. Halo de pulso muy sutil (scale 1→1.06, alpha .18→0, 2.4 s).
  Estado `busy`: anillo indeterminado + "Enviando alerta…". Sin sesión no aplica (no se ve Inicio).
- **Último incidente** (si existe): tarjeta con estado ("Registrado en blockchain" ✓ / "Pendiente" / "Error"),
  hora, y `TechnicalDetails` plegable (incidentPreview + onChainStatus en mono, con botón "Copiar" por línea
  usando `LocalClipboardManager` y Snackbar "Copiado").
- Pie: "También puedes usar tu botón GIBBOR" con ícono.

### 5.3 Emergencia activa (cuando `controller.isRecording`) — overlay a pantalla completa sobre cualquier pestaña
- Fondo `sosContainer`; arriba "Emergencia activa" (headlineSmall) + cronómetro grande mm:ss (displaySmall, mono, tabular).
- Indicadores: ● Grabando video · ● Grabando audio · ✓ Ubicación registrada · ✓ Incidente en blockchain.
- Si el disparo vino de DEMO/REMOTE y hay vista previa: tarjeta 3:4 con la selfie en vivo (PreviewView del BRIEF 2),
  radio 20dp.
- Botón "Mantén presionado para detener" (HoldToConfirmButton 1.5 s, contorno `sos`) → lambda actual de detener.
- Al detener: el overlay sale con fade-through y Snackbar "Evidencia guardada. Anclando en blockchain…".
- Bloquea el botón atrás mientras graba (`BackHandler`) para evitar cierres accidentales.

### 5.4 DEMO (pestaña **DEMO**)
Misma lógica que hoy, presentación de keynote:
- Eyebrow "DEMOSTRACIÓN EN VIVO", título "Pide ayuda sin tocar tu teléfono" (headlineSmall).
- Chip de estado del control remoto (ícono `SettingsRemote`): "Esperando el control…" / "Control: KEYCODE_…".
- Botón grande (≈ 70 % ancho, máx 300dp) con el logo; hold 1.2 s al tocar (el control remoto dispara al instante).
- Línea de tiempo vertical (stepper estilo iOS) con los 5 pasos reales: conector vertical que se "llena"
  animado de `success` conforme avanzan; paso activo con spinner; error en `sos` con mensaje.
- Tarjeta final "Alerta registrada" con TX hash (mono, ellipsis medio) + "Nueva demo".
- Pie discreto: "Modo en vivo · Stellar testnet".

### 5.5 SettingsSheet (ModalBottomSheet, desde el engrane)
Secciones con listas estilo Ajustes (ListItem M3, divisores insetados):
- **Botón GIBBOR**: estado (`statusText`), "Activar Bluetooth", "Conectar" / "Conectando…", "Desconectar".
- **Servidor**: campo Backend URL.
- **Cuenta**: correo de la sesión (solo lectura).
- **Registro técnico**: `LogConsole` (alto máx. 320dp, mono 12sp, auto-scroll) con botón **CLS**
  (TextButton "CLS" con ícono `DeleteSweep`) → `controller.clearLog()`; estado vacío "Sin registros".
- **Acerca de**: logo pequeño, "GIBBOR · Hackatón UTEZ", versión (`BuildConfig.VERSION_NAME`).

### 5.6 Navegación
`NavigationBar` M3 con 2 ítems: **GIBBOR** (`Shield`) y **DEMO** (`PlayCircle`), indicador M3 estándar.
Oculta la barra en onboarding y en Emergencia activa. `isDemoActive` sigue sincronizado con la pestaña.

---

## 6. Calidad

- `@Preview` (claro y oscuro, 360×800) para: Onboarding, Home, EmergencyActive, Demo, SettingsSheet,
  usando un `FakeMainScreenController` en `src/debug` o dentro del archivo de previews.
- Sin texto cortado a 360dp ni con escala de fuente 1.5×; todo scrollable donde haga falta.
- Animaciones con `graphicsLayer`/`drawBehind` (no relayout por frame).
- `contentDescription`/`semantics` en SOS ("Botón de emergencia. Mantén presionado para pedir ayuda"),
  logo, íconos de estado; `stateDescription` en las filas de estado.
- Borra código muerto e imports sin uso.

## 7. Entrega
1. `android\gradlew.bat -p android assembleDebug` OK.
2. Resumen: archivos creados/modificados/borrados, dependencias añadidas, y cada desviación con motivo.
