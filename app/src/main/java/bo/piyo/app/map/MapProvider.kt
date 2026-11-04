package bo.piyo.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.Place

/** A point drawn on the map. */
data class MapPoint(
    val id: String,
    val position: LatLng,
    val color: Color,
    val kind: Kind = Kind.STOP,
    val label: String? = null,
    /** USER only: accuracy radius in metres, drawn as a translucent halo so an imprecise fix looks imprecise. */
    val accuracyM: Float? = null,
) {
    /**
     * ORIGIN = where you board (bus glyph in the line colour), TRANSFER = where you change (smaller bus),
     * DESTINATION = where you get off / the trip ends (flag, accent), START/END = first and last point of a
     * line (play / flag in the line colour), USER = position dot, PIN = report pin, STOP = plain dot.
     */
    enum class Kind { STOP, ORIGIN, DESTINATION, USER, PIN, TRANSFER, START, END }
}

/** A line drawn on the map (route polyline), with optional casing like the canvas. */
data class MapLine(
    val id: String,
    val points: List<LatLng>,
    val color: Color,
    val widthDp: Float = 7f,
    val dashed: Boolean = false,
)

/**
 * Drag support for editors. On touch-down [onStart] gets the touched position and a hit radius in metres
 * (about 24 dp at the current zoom) and returns true to capture the gesture: the map then stops panning and
 * [onMove] / [onEnd] follow the finger. Returning false leaves the touch to the map.
 */
class MapDrag(val onStart: (LatLng, Double) -> Boolean, val onMove: (LatLng) -> Unit, val onEnd: (LatLng) -> Unit)

/**
 * Camera: a center/zoom, or when [fit] has at least two points, the view that frames them (padding left to the
 * provider). The map moves whenever the camera value changes; [seq] makes a repeated request ("centrar" again
 * after the user panned) a new value: buttons pass `seq = camera.seq + 1`.
 */
data class MapCamera(val center: LatLng, val zoom: Double, val fit: List<LatLng>? = null, val seq: Int = 0)

data class MapContent(
    val camera: MapCamera,
    val lines: List<MapLine> = emptyList(),
    val points: List<MapPoint> = emptyList(),
    val darkTheme: Boolean = false,
)

/**
 * Everything the app needs from a map vendor: text search and a composable map that draws points and lines.
 * Implementation: MapLibreMapProvider (self-hosted or OpenFreeMap style + Photon search). A GoogleMapProvider
 * would implement the same interface; see docs/implementation/MAP_PROVIDERS.md.
 */
interface MapProvider {
    val attribution: String

    /** One-time local setup before the first map is shown (e.g. unpacking bundled tiles). Never throws. */
    suspend fun prepare() {}

    /** Text search biased to Santa Cruz de la Sierra. Returns an empty list on error (never throws). */
    suspend fun searchPlaces(query: String, near: LatLng?): List<Place>

    /** Reverse lookup for a tapped point; may return null when unsupported. */
    suspend fun describe(position: LatLng): String?

    /**
     * [onCameraIdle] receives the map centre whenever the user stops moving the map (pick-a-point screens).
     * No default value here: default arguments on abstract composables are not supported by the Compose
     * compiler (AbstractMethodError at runtime); use the 3-argument extension below instead.
     */
    @Composable
    fun Map(
        modifier: Modifier,
        content: MapContent,
        onTap: ((LatLng) -> Unit)?,
        onCameraIdle: ((LatLng) -> Unit)?,
        drag: MapDrag?,
    )
}

/** Santa Cruz de la Sierra constants shared by providers. */
object SantaCruz {
    val center = LatLng(-17.7834, -63.1821) // Plaza 24 de Septiembre
    val boundsMin = LatLng(-18.05, -63.35)
    val boundsMax = LatLng(-17.60, -62.95)
    fun contains(p: LatLng) = p.lat in boundsMin.lat..boundsMax.lat && p.lng in boundsMin.lng..boundsMax.lng
}
