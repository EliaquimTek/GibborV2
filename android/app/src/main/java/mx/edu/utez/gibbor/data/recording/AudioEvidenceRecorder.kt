package mx.edu.utez.gibbor.data.recording

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Grabación de audio de evidencia con MediaRecorder (archivos en filesDir/evidence).
 */
class AudioEvidenceRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    var currentAudioFile: File? = null
        private set
    var lastIncidentId: String? = null
        private set

    val isActive: Boolean
        get() = mediaRecorder != null

    /**
     * Prepara y arranca la grabación. Lanza excepción si MediaRecorder falla.
     */
    fun start(incidentId: String): File {
        val audioDir = File(context.filesDir, "evidence")
        audioDir.mkdirs()
        val audioFile = File(audioDir, "audio_${incidentId}.m4a")
        currentAudioFile = audioFile
        lastIncidentId = incidentId

        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            return audioFile
        } catch (e: Exception) {
            mediaRecorder = null
            throw e
        }
    }

    /**
     * Detiene la grabación activa. Devuelve el archivo grabado (puede no existir o estar vacío).
     */
    fun stop(onError: (String) -> Unit): File? {
        val recorder = mediaRecorder ?: return null

        try {
            recorder.stop()
            recorder.release()
        } catch (e: Exception) {
            onError("AUDIO: Error stopping: ${e.message}")
        }

        mediaRecorder = null
        return currentAudioFile
    }
}
