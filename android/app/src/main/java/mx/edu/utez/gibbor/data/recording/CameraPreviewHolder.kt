package mx.edu.utez.gibbor.data.recording

import androidx.camera.core.Preview

object CameraPreviewHolder {
    @Volatile
    var surfaceProvider: Preview.SurfaceProvider? = null
}
