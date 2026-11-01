package bo.piyo.app.ui.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import bo.piyo.app.domain.geo.Geo
import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.LineBundle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val esBo = Locale("es", "BO")

/** "20 oct" from an ISO instant. */
fun formatShortDate(iso: String?): String = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM", esBo)).replace(".", "")
}.getOrDefault("—")

/** "20 oct 2026 · hace 8 días". */
fun formatDateWithAge(iso: String?): String = runCatching {
    val z = Instant.parse(iso).atZone(ZoneId.systemDefault())
    val days = ChronoUnit.DAYS.between(z.toLocalDate(), java.time.LocalDate.now())
    z.format(DateTimeFormatter.ofPattern("d MMM yyyy", esBo)).replace(".", "") + " · hace $days días"
}.getOrDefault("—")

/** "12 nov" for a block-until timestamp. */
fun formatDayMonth(iso: String?): String = formatShortDate(iso)

/** "hoy", "ayer", "hace N días" or a short date for a past epoch-ms instant. */
fun formatAgo(epochMs: Long, today: String, yesterday: String, daysAgo: (Int) -> String): String {
    val days = ((System.currentTimeMillis() - epochMs) / 86_400_000L).toInt()
    return when {
        days <= 0 -> today
        days == 1 -> yesterday
        days < 30 -> daysAgo(days)
        else -> java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM"))
    }
}

/** HH:mm after adding minutes to now. */
fun arrivalTime(minutesFromNow: Int): String =
    java.time.LocalTime.now().plusMinutes(minutesFromNow.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))

fun Context.hasInternet(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
fun isOnline(): Boolean { val ctx = LocalContext.current; return remember { ctx.hasInternet() } }

/** Nearest vertex of a line to the user: where, how far, and the street name used there. */
data class NearestOnLine(val point: LatLng, val distanceM: Double, val street: String)

/**
 * One scan of every polyline per (bundle, position), shared by Inicio ("pasan cerca"), Líneas
 * ("pasa a X m") and Favoritos. 65k vertices take ~15 ms with the fast distance; the result is
 * memoised so switching tabs never recomputes it, and [rememberNearestByLine] computes it on a
 * background dispatcher so the first frame of a screen is never blocked.
 */
object NearestIndex {
    private var key: Pair<LineBundle, LatLng>? = null
    private var value: Map<String, NearestOnLine> = emptyMap()

    /** The cached map when it is already for this (bundle, position); empty otherwise, never computes. */
    @Synchronized
    fun peek(bundle: LineBundle?, position: LatLng?): Map<String, NearestOnLine> =
        if (bundle != null && position != null && key == (bundle to position)) value else emptyMap()

    @Synchronized
    fun get(bundle: LineBundle?, position: LatLng?): Map<String, NearestOnLine> {
        if (bundle == null || position == null) return emptyMap()
        val k = bundle to position
        if (key == k) return value
        val out = HashMap<String, NearestOnLine>(bundle.lines.size)
        for (l in bundle.lines) {
            var best: NearestOnLine? = null
            for (v in l.variants) {
                val s = Geo.nearestOnPolylineFast(position, v.points) ?: continue
                if (best == null || s.distanceM < best.distanceM) {
                    val street = if (v.streets.isEmpty()) v.headsign else v.streets[(s.index.toDouble() / v.points.size.coerceAtLeast(1) * v.streets.size).toInt().coerceIn(0, v.streets.lastIndex)]
                    best = NearestOnLine(s.point, s.distanceM, street)
                }
            }
            if (best != null) out[l.lineId] = best
        }
        key = k; value = out
        return out
    }
}

/** The nearest-line map for the current bundle and position; empty until the background scan finishes. */
@Composable
fun rememberNearestByLine(bundle: LineBundle?, position: LatLng?): Map<String, NearestOnLine> =
    androidx.compose.runtime.produceState(initialValue = NearestIndex.peek(bundle, position), bundle, position) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { NearestIndex.get(bundle, position) }
    }.value

/** Lines whose nearest polyline vertex is within [radiusM] of [position], sorted by distance. */
@Composable
fun rememberNearbyLines(bundle: LineBundle?, position: LatLng?, radiusM: Double): List<Pair<Line, Double>> {
    val nearest = rememberNearestByLine(bundle, position)
    return remember(nearest) { nearbyLines(bundle, nearest, radiusM) }
}

fun nearbyLines(bundle: LineBundle?, nearest: Map<String, NearestOnLine>, radiusM: Double): List<Pair<Line, Double>> {
    if (bundle == null) return emptyList()
    return bundle.lines.mapNotNull { l -> nearest[l.lineId]?.takeIf { it.distanceM <= radiusM }?.let { l to it.distanceM } }.sortedBy { it.second }
}

/** Nearest vertex of a single line (any variant) to the position; for one line it is cheap enough inline. */
fun nearestOnLine(line: Line, position: LatLng?): Triple<LatLng, Double, String>? {
    position ?: return null
    return line.variants.mapNotNull { v ->
        val s = Geo.nearestOnPolylineFast(position, v.points) ?: return@mapNotNull null
        val street = if (v.streets.isEmpty()) v.headsign else v.streets[(s.index.toDouble() / v.points.size.coerceAtLeast(1) * v.streets.size).toInt().coerceIn(0, v.streets.lastIndex)]
        Triple(s.point, s.distanceM, street)
    }.minByOrNull { it.second }
}
