package mx.edu.utez.gibbor.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Ubicación actual del teléfono vía Fused Location Provider.
 */
class LocationDataSource(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCurrentLocation(): Location = suspendCancellableCoroutine { cont ->
        if (!hasLocationPermission()) {
            cont.resumeWithException(SecurityException("Falta ACCESS_FINE_LOCATION"))
            return@suspendCancellableCoroutine
        }

        @Suppress("MissingPermission")
        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location == null) {
                    cont.resumeWithException(
                        IllegalStateException("Could not get location. Enable GPS and try again.")
                    )
                } else {
                    cont.resume(location)
                }
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }
}
