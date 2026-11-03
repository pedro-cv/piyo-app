package bo.piyo.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import bo.piyo.app.domain.model.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** The user's position for "Mi ubicación", "pasan cerca" and trip guidance. It never leaves the phone. */
interface LocationProvider {
    /** The real position, or null while unknown (no permission, no fix yet). Never a made-up point. */
    val position: StateFlow<LatLng?>
    val label: Flow<String>
    /** True while [startTracking] is active (live GPS updates for an ongoing trip). */
    val tracking: StateFlow<Boolean>
    fun hasPermission(): Boolean
    /** Publishes the last known fix at once, then asks for a fresh one in the background. */
    fun refresh()
    /** A fresh fix (up to [timeoutMs]), also published to [position]; the last known one if none comes. */
    suspend fun locate(timeoutMs: Long = 8_000): LatLng?
    /** Continuous updates every few seconds / metres while a trip is active. No-op without permission. */
    fun startTracking()
    fun stopTracking()
}

/**
 * Framework LocationManager only (no Google Play Services dependency). [refresh] shows the last known fix
 * immediately and then replaces it with a fresh one (the last known fix can be hours old, which is what
 * made "Centrar en mi ubicación" land in the wrong place). Without permission the position stays null:
 * callers that need a point fall back to the city centre themselves, and no "you are here" dot is drawn.
 * An active trip turns on live updates from GPS and network (2 s / 3 m) until "Terminar viaje".
 */
class AndroidLocationProvider(private val context: Context) : LocationProvider {
    private val _position = MutableStateFlow<LatLng?>(null)
    override val position: StateFlow<LatLng?> = _position
    private val _label = MutableStateFlow("Mi ubicación")
    override val label: Flow<String> = _label
    private val _tracking = MutableStateFlow(false)
    override val tracking: StateFlow<Boolean> = _tracking
    private var listener: LocationListenerCompat? = null

    private val lm get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun lastKnown(): LatLng? = runCatching {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
    }.getOrNull()?.let { LatLng(it.latitude, it.longitude) }

    override fun refresh() {
        if (!hasPermission()) { _position.value = null; return }
        lastKnown()?.let { _position.value = it }
        requestFresh(null) { }
    }

    override suspend fun locate(timeoutMs: Long): LatLng? {
        if (!hasPermission()) { _position.value = null; return null }
        lastKnown()?.let { if (_position.value == null) _position.value = it }
        val signal = CancellationSignal()
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { signal.cancel() }
                requestFresh(signal) { if (cont.isActive) cont.resume(it) }
            }
        }
        if (fresh == null) signal.cancel()
        return fresh ?: _position.value
    }

    /** One fresh fix from the best enabled provider; published to [position] and passed to [onFix] (null on failure). */
    @SuppressLint("MissingPermission")
    private fun requestFresh(signal: CancellationSignal?, onFix: (LatLng?) -> Unit) {
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) { onFix(null); return }
        runCatching {
            LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) { loc: Location? ->
                val p = loc?.let { LatLng(it.latitude, it.longitude) }
                if (p != null) _position.value = p
                onFix(p)
            }
        }.onFailure { onFix(null) }
    }

    @SuppressLint("MissingPermission")
    override fun startTracking() {
        if (!hasPermission() || listener != null) return
        val l = LocationListenerCompat { loc: Location -> _position.value = LatLng(loc.latitude, loc.longitude) }
        listener = l
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            runCatching { if (lm.isProviderEnabled(p)) lm.requestLocationUpdates(p, 2_000L, 3f, l, Looper.getMainLooper()) }
        }
        _tracking.value = true
        refresh()
    }

    override fun stopTracking() {
        listener?.let { runCatching { lm.removeUpdates(it) } }
        listener = null
        _tracking.value = false
    }
}
