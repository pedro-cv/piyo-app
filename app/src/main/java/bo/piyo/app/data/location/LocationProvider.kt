package bo.piyo.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
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
    /** Accuracy radius of [position] in metres (68 % confidence), null when unknown. Drawn as a halo. */
    val accuracyM: StateFlow<Float?>
    val label: Flow<String>
    /** True while [startTracking] is active (live GPS updates for an ongoing trip). */
    val tracking: StateFlow<Boolean>
    fun hasPermission(): Boolean
    /** True when the user granted precise location; with approximate only, fixes are off by up to ~3 km. */
    fun hasPrecisePermission(): Boolean
    /** Publishes the best recent fix at once, then asks for a fresh one in the background. */
    fun refresh()
    /** A fresh fix (up to [timeoutMs]), also published to [position]; the best known one if none comes. */
    suspend fun locate(timeoutMs: Long = 8_000): LatLng?
    /** Continuous updates every few seconds / metres while a trip is active. No-op without permission. */
    fun startTracking()
    fun stopTracking()
    /**
     * The app went to the background ([visible] = false) or came back. Live updates pause while piyo is not on
     * screen, also during a trip, so location is only ever read while the app is in use.
     */
    fun onAppVisible(visible: Boolean)
}

/**
 * Framework LocationManager only (no Google Play Services dependency). Every fix, from any provider, goes
 * through [FixSelector]: a coarse network fix no longer replaces a recent GPS one, and a last known fix older
 * than 30 minutes is not shown. [refresh] shows the best recent fix at once and asks every enabled provider
 * (fused on Android 12+, GPS, network) for a fresh one; the most accurate answer wins. Without permission the
 * position stays null: callers fall back to the city centre themselves and no "you are here" dot is drawn.
 * An active trip turns on live updates (2 s / 3 m) until "Terminar viaje"; they pause while the app is in
 * the background. Positions are kept in memory only.
 */
class AndroidLocationProvider(private val context: Context) : LocationProvider {
    private val _position = MutableStateFlow<LatLng?>(null)
    override val position: StateFlow<LatLng?> = _position
    private val _accuracy = MutableStateFlow<Float?>(null)
    override val accuracyM: StateFlow<Float?> = _accuracy
    private val _label = MutableStateFlow("Mi ubicación")
    override val label: Flow<String> = _label
    private val _tracking = MutableStateFlow(false)
    override val tracking: StateFlow<Boolean> = _tracking
    private var listener: LocationListenerCompat? = null
    private var current: Fix? = null
    private var visible = true

    private val lm get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun hasPermission(): Boolean = hasPrecisePermission() ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun hasPrecisePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Providers to ask, best first. FUSED combines GPS, Wi-Fi and cell on Android 12+ without Play Services. */
    private fun providers(): List<String> {
        val all = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        return all.filter { p -> runCatching { lm.allProviders.contains(p) && lm.isProviderEnabled(p) }.getOrDefault(false) }
    }

    private fun Location.toFix() = Fix(
        LatLng(latitude, longitude), if (hasAccuracy()) accuracy else null,
        elapsedRealtimeNanos / 1_000_000L, provider ?: "",
    )

    /** Publishes [loc] when it is better than what is on screen; returns the fix either way. */
    private fun offer(loc: Location): Fix {
        val f = loc.toFix()
        synchronized(this) {
            if (FixSelector.isBetter(f, current)) {
                current = f
                _position.value = f.position
                _accuracy.value = f.accuracyM
            }
        }
        return f
    }

    @SuppressLint("MissingPermission")
    private fun publishLastKnown() {
        val known = (providers() + LocationManager.PASSIVE_PROVIDER).distinct()
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
        val best = FixSelector.best(known.map { it.toFix() }, SystemClock.elapsedRealtime()) ?: return
        known.firstOrNull { it.toFix() == best }?.let { offer(it) }
    }

    private fun forget() {
        synchronized(this) { current = null }
        _position.value = null
        _accuracy.value = null
    }

    override fun refresh() {
        if (!hasPermission()) { forget(); return }
        publishLastKnown()
        requestFresh(null) { }
    }

    override suspend fun locate(timeoutMs: Long): LatLng? {
        if (!hasPermission()) { forget(); return null }
        publishLastKnown()
        val signal = CancellationSignal()
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { signal.cancel() }
                requestFresh(signal) { if (cont.isActive) cont.resume(it) }
            }
        }
        if (fresh == null) signal.cancel()
        return _position.value
    }

    /**
     * Asks every enabled provider for one fresh fix; each answer goes through [offer]. [onDone] runs once,
     * with the published position, after the first precise answer (≤ 50 m) or when all providers answered.
     */
    @SuppressLint("MissingPermission")
    private fun requestFresh(signal: CancellationSignal?, onDone: (LatLng?) -> Unit) {
        val ps = providers()
        if (ps.isEmpty()) { onDone(_position.value); return }
        var left = ps.size
        var done = false
        val finish = { precise: Boolean ->
            left--
            if (!done && (precise || left == 0)) { done = true; onDone(_position.value) }
        }
        for (p in ps) {
            runCatching {
                LocationManagerCompat.getCurrentLocation(lm, p, signal, ContextCompat.getMainExecutor(context)) { loc: Location? ->
                    val f = loc?.let { offer(it) }
                    finish(f != null && (f.accuracyM ?: Float.MAX_VALUE) <= 50f)
                }
            }.onFailure { finish(false) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun register() {
        if (!hasPermission() || listener != null || !visible) return
        val l = LocationListenerCompat { loc: Location -> offer(loc) }
        listener = l
        for (p in providers()) {
            runCatching { lm.requestLocationUpdates(p, 2_000L, 3f, l, Looper.getMainLooper()) }
        }
    }

    private fun unregister() {
        listener?.let { runCatching { lm.removeUpdates(it) } }
        listener = null
    }

    override fun startTracking() {
        if (!hasPermission()) return
        _tracking.value = true
        register()
        refresh()
    }

    override fun stopTracking() {
        unregister()
        _tracking.value = false
    }

    override fun onAppVisible(visible: Boolean) {
        this.visible = visible
        if (!visible) unregister()
        else if (_tracking.value) { register(); refresh() }
    }
}
