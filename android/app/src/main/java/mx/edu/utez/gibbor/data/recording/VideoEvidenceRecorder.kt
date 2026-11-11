package mx.edu.utez.gibbor.data.recording

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import mx.edu.utez.gibbor.core.util.HashUtils
import java.io.File

/**
 * Graba video de evidencia con CameraX (archivos en filesDir/evidence).
 *
 * La cámara se ata al [lifecycleOwner] del servicio de protección, no al de la
 * Activity ni al del proceso: así la grabación continúa con la app cerrada y la
 * pantalla apagada.
 */
class VideoEvidenceRecorder(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {

    private var activeRecording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null

    val isActive: Boolean get() = activeRecording != null

    /**
     * @param onFinalized se llama con el archivo y su SHA-256 cuando el video se cierra
     *   correctamente, o con `null` si falló.
     */
    @SuppressLint("MissingPermission") // se comprueba RECORD_AUDIO antes de withAudioEnabled
    fun start(
        incidentId: String,
        useFrontCamera: Boolean,
        onLog: (String) -> Unit,
        onFinalized: (file: File, sha256: String?) -> Unit,
    ) {
        val dir = File(context.filesDir, "evidence").apply { mkdirs() }
        val file = File(dir, "video_${incidentId}.mp4")
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
                    )
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)

                val front = useFrontCamera && provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

                provider.unbindAll()
                // La vista previa solo existe si la pantalla de emergencia está visible (DEMO/REMOTE)
                val previewProvider = if (front) CameraPreviewHolder.surfaceProvider else null
                if (previewProvider != null) {
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewProvider) }
                    try {
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
                    } catch (_: Exception) {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, selector, videoCapture)
                    }
                } else {
                    provider.bindToLifecycle(lifecycleOwner, selector, videoCapture)
                }

                val pending = videoCapture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
                val withAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                activeRecording = (if (withAudio) pending.withAudioEnabled() else pending)
                    .start(ContextCompat.getMainExecutor(context)) { event ->
                        when (event) {
                            is VideoRecordEvent.Start ->
                                onLog(if (front) "VIDEO: Recording (front camera)..." else "VIDEO: Recording...")
                            is VideoRecordEvent.Finalize -> {
                                activeRecording = null
                                provider.unbindAll()
                                if (event.hasError()) {
                                    onLog("VIDEO ERROR: finalize code=${event.error}")
                                    onFinalized(file, if (file.exists() && file.length() > 0) HashUtils.sha256File(file) else null)
                                } else {
                                    onFinalized(file, HashUtils.sha256File(file))
                                }
                            }
                            else -> Unit
                        }
                    }
            } catch (e: Exception) {
                activeRecording = null
                onLog("VIDEO ERROR: ${e.message}")
                onFinalized(file, null)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        activeRecording?.stop()
    }

    fun release() {
        activeRecording?.stop()
        activeRecording = null
        cameraProvider?.unbindAll()
    }
}
