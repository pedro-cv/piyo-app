package bo.piyo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bo.piyo.app.R
import bo.piyo.app.data.lines.DatasetWriter
import bo.piyo.app.domain.LineColors
import bo.piyo.app.domain.geo.Geo
import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.Variant
import bo.piyo.app.map.MapCamera
import bo.piyo.app.map.MapContent
import bo.piyo.app.map.MapDrag
import bo.piyo.app.map.MapLine
import bo.piyo.app.map.MapPoint
import bo.piyo.app.map.SantaCruz
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.components.IconCircleButton
import bo.piyo.app.ui.components.PillButton
import bo.piyo.app.ui.components.PiyoButton
import bo.piyo.app.ui.theme.LocalPiyo
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt

private enum class Tool { ADD, MOVE, INSERT, DELETE }

/** Colours offered for a new route: already inside the toned range LineColors uses. */
private val ROUTE_COLORS = listOf("#C2452D", "#D07B16", "#8A8A2A", "#2E8B57", "#2A6A47", "#2B8A8A", "#2F6FB6", "#4C4CB3", "#7A4FB8", "#B8325E", "#8B5A2B", "#5C636A")

/**
 * Route editor (CONTRIBUTIONS_DESIGN.md §4.2): full-screen map where taps add, move, insert or delete
 * vertices of the polyline; a bottom panel holds the route data (number, name, type, colour, direction,
 * from/to) and the save button. Saving writes into the active local provider, creating one (and asking
 * its name) when the official data is in use.
 */
@Composable
fun RouteEditorScreen(lineId: String?, variantId: String?, onDone: () -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val bundle by c.lines.bundle.collectAsStateWithLifecycle()
    val position by c.location.position.collectAsStateWithLifecycle()
    val active by c.providers.active.collectAsStateWithLifecycle()
    val base: Line? = remember(bundle, lineId) { lineId?.let { id -> bundle?.lines?.firstOrNull { it.lineId == id } } }
    val baseVariant: Variant? = remember(base, variantId) { base?.variants?.let { vs -> vs.firstOrNull { it.variantId == variantId } ?: vs.firstOrNull() } }

    // Geometry + undo history
    var points by remember(baseVariant) { mutableStateOf(baseVariant?.points ?: emptyList()) }
    val history = remember { ArrayDeque<List<LatLng>>() }
    var tool by remember { mutableStateOf(Tool.ADD) }
    var dragIdx by remember { mutableStateOf<Int?>(null) }
    fun push() { history.addLast(points); if (history.size > 50) history.removeFirst() }

    // Route data
    var display by remember(base) { mutableStateOf(base?.display ?: "") }
    var name by remember(base) { mutableStateOf(base?.name ?: "") }
    var mode by remember(base) { mutableStateOf(base?.mode ?: "micro") }
    var colorHex by remember(base) { mutableStateOf(base?.color ?: ROUTE_COLORS[6]) }
    var direction by remember(baseVariant) { mutableStateOf(baseVariant?.direction ?: "ida") }
    var origin by remember(baseVariant) { mutableStateOf(baseVariant?.origin ?: "") }
    var headsign by remember(baseVariant) { mutableStateOf(baseVariant?.headsign ?: "") }
    var expanded by remember { mutableStateOf(base == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var askName by remember { mutableStateOf(false) }
    var pickColor by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val color = remember(colorHex) { LineColors.toned(colorHex) ?: t.primary }
    // Leaving with unsaved points asks first (an accidental back would throw the drawing away).
    var askDiscard by remember { mutableStateOf(false) }
    val dirty = points != (baseVariant?.points ?: emptyList<LatLng>())
    androidx.activity.compose.BackHandler(enabled = dirty) { askDiscard = true }
    fun leave() { if (dirty) askDiscard = true else onDone() }
    val camera = remember(baseVariant) {
        val pts = baseVariant?.points
        if (pts != null && pts.size >= 2) MapCamera(pts[pts.size / 2], 13.0, fit = pts) else MapCamera(position ?: SantaCruz.center, 14.0)
    }
    // No "other lines" overlay here on purpose: drawing all 294 variants strains low-end phones.
    val content = remember(points, color, t.dark) {
        val lines = ArrayList<MapLine>()
        if (points.size >= 2) lines += MapLine("route", points, color)
        val pts = ArrayList<MapPoint>()
        points.forEachIndexed { i, p -> pts += MapPoint("v$i", p, color, MapPoint.Kind.STOP) }
        if (points.isNotEmpty()) pts += MapPoint("start", points.first(), color, MapPoint.Kind.START)
        if (points.size >= 2) pts += MapPoint("end", points.last(), color, MapPoint.Kind.END)
        position?.let { pts += MapPoint("me", it, t.userDot, MapPoint.Kind.USER) }
        MapContent(camera, lines, pts, t.dark)
    }

    fun onTap(p: LatLng) {
        error = null
        when (tool) {
            Tool.ADD -> { push(); points = points + p }
            Tool.MOVE -> Unit // moving is a drag gesture (see `drag` below)
            Tool.INSERT -> {
                if (points.size < 2) { push(); points = points + p; return }
                val seg = nearestSegment(points, p)
                push(); points = points.toMutableList().also { it.add(seg + 1, p) }
            }
            Tool.DELETE -> nearestIndex(points, p)?.let { i -> push(); points = points.toMutableList().also { it.removeAt(i) } }
        }
    }

    // Move tool: press a vertex and drag it; the map provider captures the gesture once onStart says yes.
    val drag = if (tool == Tool.MOVE) MapDrag(
        onStart = { p, radiusM ->
            val i = points.indices.minByOrNull { Geo.distanceM(points[it], p) }?.takeIf { Geo.distanceM(points[it], p) <= radiusM }
            if (i != null) { push(); dragIdx = i; error = null }
            i != null
        },
        onMove = { p -> dragIdx?.let { i -> points = points.toMutableList().also { it[i] = p } } },
        onEnd = { p -> dragIdx?.let { i -> points = points.toMutableList().also { it[i] = p } }; dragIdx = null },
    ) else null

    val needPoints = stringResource(R.string.editor_need_points)
    val needFields = stringResource(R.string.editor_need_fields)
    suspend fun persist(providerId: String) {
        val official = c.officialLines.load()
        val lid = base?.lineId ?: ("u-" + display.trim().lowercase().replace(Regex("[^a-z0-9]+"), "") .ifBlank { "ruta" } + "-" + java.util.UUID.randomUUID().toString().take(4))
        val vid = baseVariant?.variantId ?: "$lid:$direction"
        val lengthM = Geo.pathLengthM(points).roundToInt()
        val v = Variant(vid, direction, headsign.trim(), origin.trim(), points, baseVariant?.streets ?: emptyList(), emptyList(), lengthM, baseVariant?.name ?: "", baseVariant?.branch ?: "", null)
        val variants = base?.variants?.let { vs -> if (vs.any { it.variantId == vid }) vs.map { if (it.variantId == vid) v else it } else vs + v } ?: listOf(v)
        val line = Line(lid, display.trim(), name.trim().ifBlank { "Línea ${display.trim()}" }, mode, colorHex, base?.fareBs, variants, base?.colorSource)
        val officialLine = official.lines.firstOrNull { it.lineId == lid }
        val op = if (officialLine != null) "update" else "add"
        val before = officialLine?.variants?.associate { it.variantId to DatasetWriter.encodePolyline(it.points) } ?: emptyMap()
        c.providers.saveLine(providerId, line, op, before, official.dataVersion, official.lines)
    }
    fun save() {
        if (points.size < 2) { error = needPoints; return }
        if (display.isBlank() || headsign.isBlank()) { error = needFields; expanded = true; return }
        val prov = active
        if (prov == null) { askName = true; return }
        scope.launch { saving = true; persist(prov.meta.id); saving = false; onDone() }
    }

    Box(Modifier.fillMaxSize().background(t.bg)) {
        c.mapProvider.Map(Modifier.fillMaxSize(), content, onTap = ::onTap, onCameraIdle = null, drag = drag)
        // Top bar: back, title, undo, clear
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), ::leave, filled = true)
            Box(Modifier.weight(1f).clip(CircleShape).background(t.surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(if (base == null) stringResource(R.string.editor_title_new) else stringResource(R.string.editor_title_edit, base.name.ifBlank { base.display }), color = t.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            IconCircleButton(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.undo), onClick = { history.removeLastOrNull()?.let { points = it } }, filled = true)
            IconCircleButton(Icons.Rounded.DeleteSweep, stringResource(R.string.clear_points), onClick = { if (points.isNotEmpty()) { push(); points = emptyList() } }, filled = true, tint = t.danger)
        }

        // Bottom panel
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(t.surface).padding(horizontal = 18.dp, vertical = 12.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(when (tool) { Tool.ADD -> R.string.editor_hint_add; Tool.MOVE -> R.string.editor_hint_move; Tool.INSERT -> R.string.editor_hint_insert; Tool.DELETE -> R.string.editor_hint_delete }), color = t.muted, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.tool_add), selected = tool == Tool.ADD, icon = Icons.Rounded.Add, onClick = { tool = Tool.ADD })
                PillButton(stringResource(R.string.tool_move), selected = tool == Tool.MOVE, icon = Icons.Rounded.OpenWith, onClick = { tool = Tool.MOVE })
                PillButton(stringResource(R.string.tool_insert), selected = tool == Tool.INSERT, icon = Icons.Rounded.AddLocationAlt, onClick = { tool = Tool.INSERT })
                PillButton(stringResource(R.string.tool_delete), selected = tool == Tool.DELETE, icon = Icons.Rounded.Delete, onClick = { tool = Tool.DELETE })
                // Not a tool but an action: the whole trace the other way round (undoable), origin <-> destination.
                PillButton(stringResource(R.string.tool_reverse), selected = false, icon = Icons.Rounded.SwapVert, onClick = {
                    if (points.size >= 2) { push(); points = points.reversed(); val o = origin; origin = headsign; headsign = o.ifBlank { headsign } }
                })
            }
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(color), contentAlignment = Alignment.Center) {
                    Text(display.ifBlank { "?" }, color = Color.White, fontSize = if (display.length > 3) 9.sp else 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.route_data), color = t.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.editor_stats, Geo.formatDistance(Geo.pathLengthM(points)), points.size) + (headsign.takeIf { it.isNotBlank() }?.let { " · → $it" } ?: ""), color = t.muted, fontSize = 12.sp, maxLines = 1)
                }
                Icon(if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, null, tint = t.muted)
            }
            if (expanded) Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(stringResource(R.string.route_number), display, { display = it.take(12) }, Modifier.weight(0.45f))
                    Field(stringResource(R.string.route_name), name, { name = it.take(80) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.route_type), color = t.muted, fontSize = 13.sp)
                    PillButton(stringResource(R.string.mode_micro), selected = mode == "micro", onClick = { mode = "micro" })
                    PillButton(stringResource(R.string.mode_trufi), selected = mode == "trufi", onClick = { mode = "trufi" })
                    PillButton(stringResource(R.string.mode_other), selected = mode == "other", onClick = { mode = "other" })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.route_direction), color = t.muted, fontSize = 13.sp)
                    PillButton(stringResource(R.string.direction_ida_short), selected = direction == "ida", onClick = { direction = "ida" })
                    PillButton(stringResource(R.string.direction_vuelta_short), selected = direction == "vuelta", onClick = { direction = "vuelta" })
                }
                // The line's own colour (and the official one when editing) come first so they are always
                // selectable, then the presets, then a rainbow swatch that opens the custom picker.
                val swatches = remember(colorHex, base) { (listOfNotNull(colorHex, base?.color) + ROUTE_COLORS).map { it.uppercase() }.distinct() }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    swatches.forEach { hex ->
                        val cc = LineColors.toned(hex) ?: t.primary
                        val on = hex.equals(colorHex, ignoreCase = true)
                        Box(Modifier.size(30.dp).clip(CircleShape).background(cc).border(if (on) 3.dp else 0.dp, if (on) t.ink else Color.Transparent, CircleShape).clickable { colorHex = hex }, contentAlignment = Alignment.Center) {
                            if (on) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    val rainbow = androidx.compose.ui.graphics.Brush.sweepGradient((0..6).map { Color.hsl(it * 60f, 0.8f, 0.5f) })
                    Box(Modifier.size(30.dp).clip(CircleShape).background(rainbow).clickable { pickColor = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Palette, stringResource(R.string.color_custom_title), tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(stringResource(R.string.route_origin), origin, { origin = it.take(80) }, Modifier.weight(1f))
                    Field(stringResource(R.string.route_headsign), headsign, { headsign = it.take(80) }, Modifier.weight(1f))
                }
            }
            error?.let { Text(it, color = t.danger, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            PiyoButton(stringResource(R.string.save_route_btn), enabled = !saving, onClick = ::save)
        }
    }

    if (askDiscard) androidx.compose.material3.AlertDialog(
        onDismissRequest = { askDiscard = false },
        text = { Text(stringResource(R.string.editor_discard_body)) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { askDiscard = false; onDone() }) { Text(stringResource(R.string.editor_discard), color = t.danger) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { askDiscard = false }) { Text(stringResource(R.string.keep_going)) } },
        containerColor = t.surface, textContentColor = t.ink,
    )
    if (pickColor) ColorPickerDialog(colorHex, display, onDismiss = { pickColor = false }) { hex -> colorHex = hex; pickColor = false }
    if (askName) NameDialog(
        title = stringResource(R.string.provider_new_title), initial = "Mis rutas", body = stringResource(R.string.provider_new_body),
        onDismiss = { askName = false },
    ) { n ->
        askName = false
        scope.launch {
            saving = true
            val meta = c.providers.create(n, c.officialLines.load().dataVersion)
            c.providers.activate(meta.id)
            persist(meta.id)
            saving = false
            onDone()
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalPiyo.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = t.muted, fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(t.surface2).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = t.ink, fontSize = 15.sp), cursorBrush = SolidColor(t.primary), modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun nearestIndex(pts: List<LatLng>, p: LatLng): Int? = Geo.nearestVertex(p, pts)

/** Index i of the segment (pts[i], pts[i+1]) closest to p, with an equirectangular projection. */
private fun nearestSegment(pts: List<LatLng>, p: LatLng): Int {
    val kLng = cos(Math.toRadians(p.lat))
    fun x(q: LatLng) = q.lng * kLng
    var best = 0
    var bestD = Double.MAX_VALUE
    for (i in 0 until pts.size - 1) {
        val ax = x(pts[i]); val ay = pts[i].lat; val bx = x(pts[i + 1]); val by = pts[i + 1].lat
        val px = x(p); val py = p.lat
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val u = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + u * dx; val cy = ay + u * dy
        val d = (px - cx) * (px - cx) + (py - cy) * (py - cy)
        if (d < bestD) { bestD = d; best = i }
    }
    return best
}
