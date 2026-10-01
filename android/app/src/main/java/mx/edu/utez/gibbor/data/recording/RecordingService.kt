package mx.edu.utez.gibbor.data.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import mx.edu.utez.gibbor.core.util.HashUtils
import java.io.File

class RecordingService : Service() {

    companion object {
        const val ACTION_START      = "mx.edu.utez.gibbor.START_RECORDING"
        const val ACTION_STOP       = "mx.edu.utez.gibbor.STOP_RECORDING"
        const val EXTRA_INCIDENT_ID = "incident_id"
        const val BROADCAST_DONE    = "mx.edu.utez.gibbor.RECORDING_DONE"
        const val EXTRA_FILE_PATH   = "file_path"
        const val EXTRA_SHA256      = "sha256"

        private const val CHANNEL_ID      = "gibbor_recording"
        private const val NOTIFICATION_ID = 1001
    }

    private var activeRecording: Recording? = null
    private var currentIncidentId: String?  = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_INCIDENT_ID)
                    ?: run { stopSelf(); return START_NOT_STICKY }
                currentIncidentId = id
                startForeground(NOTIFICATION_ID, buildNotification())
                startRecording(id)
            }
            ACTION_STOP -> {
                activeRecording?.stop()
                activeRecording = null
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(id: String) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val cameraProvider = future.get()

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()

                val videoCapture = VideoCapture.withOutput(recorder)

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    ProcessLifecycleOwner.get(),
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    videoCapture
                )

                val videoDir = File(filesDir, "evidence")
                videoDir.mkdirs()
                val videoFile = File(videoDir, "video_${id}.mp4")

                activeRecording = videoCapture.output
                    .prepareRecording(this, FileOutputOptions.Builder(videoFile).build())
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(this)) { event ->
                        if (event is VideoRecordEvent.Finalize && !event.hasError()) {
                            val hash = HashUtils.sha256File(videoFile)
                            sendBroadcast(Intent(BROADCAST_DONE).apply {
                                putExtra(EXTRA_INCIDENT_ID, id)
                                putExtra(EXTRA_FILE_PATH, videoFile.absolutePath)
                                putExtra(EXTRA_SHA256, hash)
                            })
                        }
                    }
            } catch (e: Exception) {
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        activeRecording?.stop()
        activeRecording = null
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GIBBOR")
            .setContentText("Recording evidence...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Evidence recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "GIBBOR emergency evidence recording" }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }
}
