# CODEX BRIEF 2 — DEMO con flujo real + cámara frontal (selfie)

Eres el implementador; Claude (arquitecto) revisará. Implementa todo y compila al final. No preguntes:
ante ambigüedad elige lo más conservador. No hagas commits.

## Objetivo

1. En la pestaña **DEMO**, el control remoto TikTok/selfie (y el toque en el botón gigante) debe ejecutar
   **la misma lógica que el botón ESP32**: crear incidente real → Stellar → grabar audio y video.
2. Cuando el incidente nace en DEMO, el video se graba con la **cámara frontal (selfie)** y se muestra
   una **vista previa en vivo** de esa cámara en la pestaña DEMO.

## Reglas duras

- `createAndSendIncident(...)` en `AppScreen.kt` NO cambia su cuerpo, salvo lo indicado en §2 (pasar la
  cámara a `startAudioRecording`). Mismos `appendLog`, mismos estados.
- El flujo ESP32 y el botón MANUAL siguen idénticos y siguen usando la **cámara trasera**.
- No tocar backend, contrato, `domain/`, `data/remote`, `data/bluetooth`, `data/location`.
- Debe compilar: `android\gradlew.bat -p android assembleDebug`.

## 1. Disparo real desde DEMO (`AppScreen.kt`)

Extrae el cuerpo del `LaunchedEffect(controller.triggerCounter)` a una función local reutilizable,
sin cambiar su comportamiento para ESP32:

```kotlin
suspend fun handleHardwareTrigger(source: String, useFrontCamera: Boolean) {
    if (!controller.isAuthenticated) {
        controller.appendLog("$source -> authenticate first")
        return
    }
    busy = true
    try { createAndSendIncident(source, useFrontCamera) }
    catch (e: Exception) { controller.appendLog("$source -> error: ${e.message}") }
    finally { busy = false }
}
```

- ESP32: `LaunchedEffect(controller.triggerCounter)` conserva su guarda de `lastHandledTrigger` y llama
  `handleHardwareTrigger("ESP32", useFrontCamera = false)`. El log resultante debe ser idéntico al actual
  ("ESP32 -> authenticate first", "ESP32 -> error: …").
- Control remoto: nuevo `LaunchedEffect(controller.demoTriggerCounter)` en el **nivel superior** de
  `AppScreen` (no dentro de `DemoScreen`), con su propio `lastHandledDemoTrigger` (inicializado al valor
  actual para no disparar al componer) → `handleHardwareTrigger("REMOTE", useFrontCamera = true)`.
- Toque del botón gigante de DEMO: `CoroutineScope(Dispatchers.Main).launch { handleHardwareTrigger("DEMO", true) }`
  (mismo patrón que el botón MANUAL). Deshabilitado mientras `busy`.
- Si llega un disparo mientras `busy == true` (REMOTE o DEMO), ignóralo y registra
  `"$source -> busy, trigger ignored"`. (No cambies este aspecto para ESP32.)

## 2. Cámara frontal

- `MainScreenController.startAudioRecording(incidentId: String, useFrontCamera: Boolean = false)`.
  Ajusta `MainActivity` (override sin valor por defecto) y `createAndSendIncident(source, useFrontCamera)`
  que lo pase en el paso 5. Todas las llamadas existentes quedan con `false` → comportamiento actual.
- `MainActivity.startAudioRecording`: agrega `putExtra(RecordingService.EXTRA_USE_FRONT_CAMERA, useFrontCamera)`
  y loguea `"VIDEO: Starting video recording (front camera)..."` cuando sea frontal; si no, el texto actual.
- `RecordingService`:
  - nueva constante `EXTRA_USE_FRONT_CAMERA = "use_front_camera"`.
  - Selector: si `useFront` y `cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)` → frontal;
    si no → `DEFAULT_BACK_CAMERA` (fallback silencioso).
  - Resto idéntico (HD, archivo `video_${id}.mp4`, broadcast `BROADCAST_DONE` con SHA-256).
  - Si `QualitySelector.from(Quality.HD)` falla con la frontal, usa
    `QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))` para ambas.

## 3. Vista previa selfie en DEMO

- Dependencia nueva permitida: `implementation("androidx.camera:camera-view:$cameraxVersion")`.
- Crea `data/recording/CameraPreviewHolder.kt`:
  ```kotlin
  object CameraPreviewHolder {
      @Volatile var surfaceProvider: Preview.SurfaceProvider? = null
  }
  ```
- En `RecordingService.startRecording`, si la cámara es la frontal **y** `CameraPreviewHolder.surfaceProvider != null`,
  agrega un `Preview` use case (`Preview.Builder().build().also { it.setSurfaceProvider(provider) }`) al mismo
  `bindToLifecycle(...)` junto con `videoCapture`. Si el bind con Preview lanza excepción, reintenta solo con
  `videoCapture` (la grabación es prioritaria sobre la vista previa).
- En `DemoScreen`: cuando `controller.isRecording` sea true, el botón gigante muestra dentro (recortado circular,
  mismo tamaño) un `AndroidView { PreviewView(it).apply { scaleType = FILL_CENTER; implementationMode = COMPATIBLE } }`
  que en `factory` asigna `CameraPreviewHolder.surfaceProvider = view.surfaceProvider`, y en `onRelease`/
  `DisposableEffect` lo pone en `null`. Encima, un badge "● REC mm:ss" (Panic). Cuando no graba, el logo como hoy.
  - La vista previa debe registrarse antes de que arranque el servicio: monta el `PreviewView` (invisible, alpha 0)
    siempre que la pestaña DEMO esté visible, y hazlo visible (alpha 1 animado) cuando `isRecording`.
  - Si no llega imagen (p. ej. ESP32 disparó con cámara trasera), se ve el fondo del botón; está bien.

## 4. DemoScreen: progreso REAL, no simulado

Quita los temporizadores falsos y el hash aleatorio. `DemoScreen` recibe desde `AppScreen`:
`busy`, `onChainStatus`, `incidentPreview`, `recordingElapsedSeconds`, `onTrigger`, `onStopRecording`
(el mismo lambda de detener grabación que usa la pestaña GIBBOR).

Pasos (derivados del estado real, se marcan con la animación actual de check/spinner):
1. "Ubicación GPS capturada" — completo cuando `incidentPreview` cambió en esta ejecución.
2. "Huella SHA-256 generada" — mismo momento que 1.
3. "Incidente anclado en Stellar" — spinner mientras `busy`; completo cuando `onChainStatus` empieza con "✅";
   rojo con "✕" si contiene "Error"/"❌" (mostrar el mensaje corto debajo).
4. "Grabando audio y video (selfie)" — completo/activo cuando `controller.isRecording`.
5. "Evidencia anclada" — se completa cuando, tras detener, el log recibe "AUDIO: Hash anchored on blockchain"
   (puedes observar `controller.logText.contains(...)` desde el inicio de la ejecución; guarda el largo del log
   al iniciar para buscar solo en lo nuevo).

- La onda expansiva + haptic + flash se disparan al iniciar cada ejecución (REMOTE o toque).
- Mientras graba: botón "Detener grabación" (estilo Panic) debajo del botón gigante → `onStopRecording`.
- Tarjeta final "Alerta registrada": muestra el TX hash real (línea `TX:` de `onChainStatus`) en monospace;
  botón "Nueva demo" que solo resetea la UI.
- Si no hay sesión: el botón gigante muestra "Inicia sesión en GIBBOR" y el chip lo indica; el disparo
  queda registrado en el log por la propia lógica ("REMOTE -> authenticate first").
- Textos de pie: "Modo en vivo · registra un incidente real en Stellar testnet" y
  "Empareja el control en Ajustes › Bluetooth. Cualquier botón del control lo activa."

## 5. Antirrebote del control remoto (`MainActivity.dispatchKeyEvent`)

Algunos controles envían 2 teclas por pulsación (p. ej. VOLUME_UP + ENTER). Ignora disparos a menos de
**1500 ms** del anterior (sigue consumiendo el evento y actualizando `lastDemoKey`).

## 6. Entrega

Compila y resume archivos tocados y cualquier desviación con su motivo.
