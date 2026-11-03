package bo.piyo.app.map.maplibre

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.Place
import bo.piyo.app.map.MapContent
import bo.piyo.app.map.MapDrag
import bo.piyo.app.map.MapPoint
import bo.piyo.app.map.MapProvider
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * The app's map: MapLibre Native (open source). With no [styleUrl] it renders the city map bundled in the
 * APK ([BundledMap]: Protomaps tiles + light/dark styles with the same layers, fully offline). A [styleUrl]
 * overrides it: the Piyo API's own style (`/v1/map/style.json`, Docker stack; `?theme=light|dark` is
 * appended), or any hosted style, switched to [darkStyleUrl] in dark theme when one is configured
 * (docs/implementation/MAP_HOSTING.md). Lines and points are two GeoJSON sources updated on every recomposition; rides get a white casing, the line colour and direction chevrons
 * placed along the line (symbol layer), walks are dashed, points are circles sized by kind.
 */
class MapLibreMapProvider(
    context: Context,
    private val styleUrl: String,
    private val darkStyleUrl: String,
    private val geocoder: PhotonGeocoder,
) : MapProvider {
    init { MapLibre.getInstance(context) }

    private val bundled = if (styleUrl.isBlank()) BundledMap(context.applicationContext) else null

    override val attribution = when {
        bundled != null || styleUrl.contains("/v1/map/style.json") -> BundledMap.ATTRIBUTION
        styleUrl.contains("openfreemap") -> "© OpenStreetMap contributors · OpenFreeMap"
        else -> "© OpenStreetMap contributors"
    }

    override suspend fun prepare() { bundled?.prepare() }

    override suspend fun searchPlaces(query: String, near: LatLng?): List<Place> = geocoder.search(query, near)
    override suspend fun describe(position: LatLng): String? = geocoder.reverse(position)

    @Composable
    override fun Map(modifier: Modifier, content: MapContent, onTap: ((LatLng) -> Unit)?, onCameraIdle: ((LatLng) -> Unit)?, drag: MapDrag?) {
        val ctx = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val mapView = remember { MapView(ctx) }
        val holder = remember { Holder().apply { density = ctx.resources.displayMetrics.density } }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer); mapView.onDestroy() }
        }

        AndroidView(
            modifier = modifier,
            factory = {
                mapView.apply {
                    onCreate(null)
                    // Inside a scrolling form the Compose scroll container would take over after a few dp;
                    // claim the gesture on the first touch so panning the map is smooth and natural.
                    holder.drag = drag
                    // The listener also implements vertex dragging for the route editor: when the current
                    // MapDrag captures the touch-down, the whole gesture is consumed here and MapLibre never
                    // sees it (so the map does not pan and no click fires).
                    setOnTouchListener { v, ev ->
                        val m = holder.map
                        fun at(): LatLng { val ll = m!!.projection.fromScreenLocation(android.graphics.PointF(ev.x, ev.y)); return LatLng(ll.latitude, ll.longitude) }
                        when (ev.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                                val d = holder.drag
                                if (d != null && m != null) {
                                    val p = at()
                                    val radiusM = m.projection.getMetersPerPixelAtLatitude(p.lat) * 24 * holder.density
                                    if (d.onStart(p, radiusM)) { holder.dragging = true; return@setOnTouchListener true }
                                }
                            }
                            android.view.MotionEvent.ACTION_MOVE -> if (holder.dragging && m != null) { holder.drag?.onMove(at()); return@setOnTouchListener true }
                            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                                if (holder.dragging) { holder.dragging = false; if (m != null) holder.drag?.onEnd(at()); return@setOnTouchListener true }
                            }
                        }
                        false
                    }
                    getMapAsync { map ->
                        holder.map = map
                        map.uiSettings.isAttributionEnabled = true
                        map.uiSettings.isLogoEnabled = false
                        holder.loadStyle(map, styleFor(content.darkTheme), if (content.darkTheme) "dark" else "light") { holder.apply(content) }
                        map.addOnMapClickListener { p -> onTap?.invoke(LatLng(p.latitude, p.longitude)); onTap != null }
                        map.addOnCameraIdleListener { val c = map.cameraPosition.target; if (c != null) onCameraIdle?.invoke(LatLng(c.latitude, c.longitude)) }
                    }
                }
            },
            update = {
                holder.drag = drag
                val wanted = if (content.darkTheme) "dark" else "light"
                val m = holder.map
                if (m != null && holder.theme != wanted && holder.theme != "") {
                    holder.loadStyle(m, styleFor(content.darkTheme), wanted) { holder.apply(content) }
                } else holder.apply(content)
            },
        )
    }

    private fun styleFor(dark: Boolean): Style.Builder {
        if (bundled != null) {
            // Null only if unpacking failed (e.g. disk full): a plain background keeps routes and points usable.
            val json = bundled.styleJson(dark) ?: """{"version":8,"sources":{},"layers":[{"id":"bg","type":"background","paint":{"background-color":"${if (dark) "#1f2421" else "#eef0ec"}"}}]}"""
            return Style.Builder().fromJson(json)
        }
        val url = when {
            styleUrl.contains("/v1/map/style.json") -> styleUrl.substringBefore('?') + "?theme=" + (if (dark) "dark" else "light")
            dark && darkStyleUrl.isNotBlank() -> darkStyleUrl
            else -> styleUrl
        }
        return Style.Builder().fromUri(url)
    }

    /** Keeps the MapLibre objects outside composition and pushes [MapContent] into the sources. */
    private class Holder {
        /** Loads a style and recreates the Piyo sources/layers on top of it. */
        fun loadStyle(map: MapLibreMap, builder: Style.Builder, themeName: String, onReady: (Style) -> Unit) {
            theme = themeName
            style = null
            map.setStyle(builder) { st ->
                style = st
                st.addSource(GeoJsonSource(SRC_LINES))
                st.addSource(GeoJsonSource(SRC_POINTS))
                st.addImage(IMG_ARROW, arrowBitmap(density))
                val rides = Expression.eq(Expression.get("dashed"), Expression.literal(false))
                val walks = Expression.eq(Expression.get("dashed"), Expression.literal(true))
                st.addLayer(LineLayer(L_CASING, SRC_LINES).withFilter(rides).withProperties(
                    PropertyFactory.lineColor("#FFFFFF"), PropertyFactory.lineWidth(10f), PropertyFactory.lineOpacity(0.9f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ))
                st.addLayer(LineLayer(L_LINES, SRC_LINES).withFilter(rides).withProperties(
                    PropertyFactory.lineColor(Expression.get("color")), PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ))
                // Walks are straight lines between two points, not streets: draw them as a light, thin trail of
                // dots so they read as "roughly this way", never as a path to follow.
                st.addLayer(LineLayer(L_WALKS, SRC_LINES).withFilter(walks).withProperties(
                    PropertyFactory.lineColor(Expression.get("color")), PropertyFactory.lineWidth(3f), PropertyFactory.lineOpacity(0.55f),
                    PropertyFactory.lineDasharray(arrayOf(0.1f, 2.6f)), PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                ))
                // Direction chevrons every ~80 px along each ride, rotated with the line and kept on the map plane.
                st.addLayer(SymbolLayer(L_ARROWS, SRC_LINES).withFilter(rides).withProperties(
                    PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE), PropertyFactory.symbolSpacing(80f),
                    PropertyFactory.iconImage(IMG_ARROW), PropertyFactory.iconSize(1f),
                    PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                    PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                    PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
                ))
                val plain = Expression.not(Expression.has("icon"))
                st.addLayer(CircleLayer(L_POINTS, SRC_POINTS).withFilter(plain).withProperties(
                    PropertyFactory.circleColor(Expression.get("fill")),
                    PropertyFactory.circleStrokeColor(Expression.get("stroke")),
                    PropertyFactory.circleStrokeWidth(Expression.get("strokeWidth")),
                    PropertyFactory.circleRadius(Expression.get("radius")),
                ))
                // Glyph markers (bus, flag, play) are images registered on demand per kind+colour.
                st.addLayer(SymbolLayer(L_MARKERS, SRC_POINTS).withFilter(Expression.has("icon")).withProperties(
                    PropertyFactory.iconImage(Expression.get("icon")), PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                ))
                registered.clear()
                lastCamera = null
                onReady(st)
            }
        }

        var density = 2f
        private val registered = HashSet<String>()

        /** Registers (once per style) the marker image for a kind + colour and returns its name. */
        private fun markerImage(kind: MapPoint.Kind, colorHex: String): String {
            val name = "piyo-" + kind.name.lowercase() + "-" + colorHex.removePrefix("#")
            if (registered.add(name)) style?.addImage(name, markerBitmap(kind, android.graphics.Color.parseColor(colorHex), density))
            return name
        }

        /**
         * Map pin after the reference design (52 × 60 viewBox): white teardrop body, coloured round head,
         * white glyph centred in the head. No shadow; a faint 1 px edge keeps it visible on light tiles.
         * Glyphs: bus (board / change), flag (destination / line end), ring (line start / plain pin).
         * Anchored at the tip; drawn at 40 × 46 dp (transfer 34 × 39 dp).
         */
        private fun markerBitmap(kind: MapPoint.Kind, color: Int, density: Float): Bitmap {
            val dp = if (kind == MapPoint.Kind.TRANSFER) 34f else 40f
            val k = dp * density / 52f
            val w = (52f * k).toInt(); val h = (60f * k).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(k, k)
            val body = androidx.core.graphics.PathParser.createPathFromPathData(PIN_BODY)
            c.drawPath(body, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() })
            c.drawPath(body, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x1A000000; style = Paint.Style.STROKE; strokeWidth = 1f })
            c.drawCircle(26f, 25f, 17f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
            when (kind) {
                MapPoint.Kind.ORIGIN, MapPoint.Kind.TRANSFER -> drawGlyph24(c, GLYPH_BUS, white)
                MapPoint.Kind.DESTINATION, MapPoint.Kind.END -> drawGlyph24(c, GLYPH_FLAG, white)
                else -> drawGlyph24(c, GLYPH_RING, white)
            }
            return bmp
        }

        /** Draws a 24 dp Material path centred in the pin head (20 × 20 box at 16,15 like the reference). */
        private fun drawGlyph24(c: Canvas, d: String, paint: Paint) {
            val path = androidx.core.graphics.PathParser.createPathFromPathData(d)
            c.save()
            c.translate(16f, 15f)
            c.scale(20f / 24f, 20f / 24f)
            c.drawPath(path, paint)
            c.restore()
        }

        /** A white chevron with a soft dark outline, pointing right (the direction of the line). */
        private fun arrowBitmap(density: Float): Bitmap {
            val size = (14 * density).toInt().coerceAtLeast(14)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val s = size.toFloat()
            val path = Path().apply {
                moveTo(s * 0.30f, s * 0.18f)
                lineTo(s * 0.68f, s * 0.50f)
                lineTo(s * 0.30f, s * 0.82f)
            }
            val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = s * 0.30f; color = 0x66000000
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            }
            val stroke = Paint(outline).apply { strokeWidth = s * 0.16f; color = 0xFFFFFFFF.toInt() }
            c.drawPath(path, outline)
            c.drawPath(path, stroke)
            return bmp
        }

        var map: MapLibreMap? = null
        var style: Style? = null
        var drag: MapDrag? = null
        var dragging = false
        var theme: String = ""
        private var lastCamera: Any? = null

        fun apply(content: MapContent) {
            val m = map ?: return
            val s = style ?: return
            if (lastCamera != content.camera) {
                lastCamera = content.camera
                val fit = content.camera.fit
                if (fit != null && fit.size >= 2) {
                    // Frame the geometry; the bottom padding keeps it above the collapsed sheet.
                    val b = org.maplibre.android.geometry.LatLngBounds.Builder()
                    fit.forEach { b.include(org.maplibre.android.geometry.LatLng(it.lat, it.lng)) }
                    val d = density
                    m.moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), (32 * d).toInt(), (110 * d).toInt(), (32 * d).toInt(), (190 * d).toInt()))
                } else {
                    m.moveCamera(CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(content.camera.center.lat, content.camera.center.lng), content.camera.zoom))
                }
            }
            val lines = content.lines.filter { it.points.size >= 2 }.map { l ->
                Feature.fromGeometry(LineString.fromLngLats(l.points.map { Point.fromLngLat(it.lng, it.lat) })).apply {
                    addStringProperty("color", hex(l.color.toArgb()))
                    addBooleanProperty("dashed", l.dashed)
                }
            }
            s.getSourceAs<GeoJsonSource>(SRC_LINES)?.setGeoJson(FeatureCollection.fromFeatures(lines))
            val pts = content.points.map { p ->
                Feature.fromGeometry(Point.fromLngLat(p.position.lng, p.position.lat)).apply {
                    val col = hex(p.color.toArgb())
                    when (p.kind) {
                        MapPoint.Kind.ORIGIN, MapPoint.Kind.TRANSFER, MapPoint.Kind.DESTINATION, MapPoint.Kind.START, MapPoint.Kind.END, MapPoint.Kind.PIN ->
                            addStringProperty("icon", markerImage(p.kind, col))
                        else -> {
                            // fill, stroke, stroke width, radius
                            val (fill, stroke, sw, radius) = when (p.kind) {
                                MapPoint.Kind.USER -> Quad("#2F7BF6", "#FFFFFF", 3f, 8f)
                                else -> Quad("#FFFFFF", col, 2f, 4f)
                            }
                            addStringProperty("fill", fill); addStringProperty("stroke", stroke)
                            addNumberProperty("strokeWidth", sw); addNumberProperty("radius", radius)
                        }
                    }
                }
            }
            s.getSourceAs<GeoJsonSource>(SRC_POINTS)?.setGeoJson(FeatureCollection.fromFeatures(pts))
        }

        private fun hex(argb: Int) = String.format("#%06X", 0xFFFFFF and argb)
        private data class Quad(val fill: String, val stroke: String, val strokeWidth: Float, val radius: Float)
    }

    companion object {
        /** Reference pin outline (52 × 60 viewBox). */
        const val PIN_BODY = "M26 4C37.598 4 47 13.402 47 25C47 33.8926 41.4727 41.4942 33.6663 44.5567L27.1961 53.4007C27.0612 53.5858 26.8821 53.7369 26.6739 53.8412C26.4657 53.9455 26.2346 54 26 54C25.7654 54 25.5343 53.9455 25.3261 53.8412C25.1179 53.7369 24.9388 53.5858 24.8039 53.4007L18.3337 44.5567C10.5273 41.4941 5 33.8926 5 25C5 13.402 14.402 4 26 4Z"
        /** Material Symbols "directions_bus" (24 dp). */
        const val GLYPH_BUS = "M4 16c0 .88.39 1.67 1 2.22V20c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h8v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1.78c.61-.55 1-1.34 1-2.22V6c0-3.5-3.58-4-8-4s-8 .5-8 4v10zm3.5 1c-.83 0-1.5-.67-1.5-1.5S6.67 14 7.5 14s1.5.67 1.5 1.5S8.33 17 7.5 17zm9 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm1.5-6H6V6h12v5z"
        /** Material Symbols "flag" and "trip_origin" (24 dp). */
        const val GLYPH_FLAG = "M14.4 6L14 4H5v17h2v-7h5.6l.4 2h7V6z"
        const val GLYPH_RING = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 16c-3.31 0-6-2.69-6-6s2.69-6 6-6 6 2.69 6 6-2.69 6-6 6z"
        const val SRC_LINES = "piyo-lines"
        const val SRC_POINTS = "piyo-points"
        const val L_CASING = "piyo-lines-casing"
        const val L_LINES = "piyo-lines-layer"
        const val L_WALKS = "piyo-walks-layer"
        const val L_ARROWS = "piyo-arrows-layer"
        const val L_POINTS = "piyo-points-layer"
        const val L_MARKERS = "piyo-markers-layer"
        const val IMG_ARROW = "piyo-arrow"
    }
}
