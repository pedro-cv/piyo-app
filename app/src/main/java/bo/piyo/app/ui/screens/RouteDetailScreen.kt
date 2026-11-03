package bo.piyo.app.ui.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bo.piyo.app.R
import bo.piyo.app.domain.LineColors
import bo.piyo.app.domain.geo.Geo
import bo.piyo.app.domain.model.Itinerary
import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.Leg
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.TripRecord
import bo.piyo.app.domain.model.Variant
import bo.piyo.app.domain.routing.TripGuide
import bo.piyo.app.map.MapCamera
import bo.piyo.app.map.MapContent
import bo.piyo.app.map.MapLine
import bo.piyo.app.map.MapPoint
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.LocalSession
import bo.piyo.app.ui.components.IconCircleButton
import bo.piyo.app.ui.components.LineChip
import bo.piyo.app.ui.components.SegmentStrip
import bo.piyo.app.ui.theme.LocalPiyo
import bo.piyo.app.ui.util.rememberCenterOnMe
import bo.piyo.app.ui.util.arrivalTime
import kotlinx.coroutines.launch

/**
 * 03 Detalle de ruta, laid out like Moovit's Directions screen: the map fills the screen with a
 * "Empezar" pill at the top left and the locate button at the top right; a Material 3
 * [BottomSheetScaffold] whose collapsed header is "27 min | llegás 22:40" + star and the segment strip,
 * and whose expanded body is the step timeline; a fixed action bar at the bottom (Empezar / Ver pasos /
 * Compartir). "Empezar" asks for the location permission when needed, turns on live tracking, follows
 * the user on the map and shows turn-by-turn guidance ([TripGuide]); reaching the destination offers to
 * finish, and finishing saves the trip to the history. Ends with the NPS sheet when it is due.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(itineraryId: String, onBack: () -> Unit, onHome: () -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val itin = session.find(itineraryId)
    if (itin == null) { onBack(); return }
    val position by c.location.position.collectAsStateWithLifecycle()
    var starred by remember { mutableStateOf(false) }
    val routePoints = remember(itin) { itin.legs.flatMap { l -> if (l is Leg.Ride) l.path else listOf(l.from, l.to) } }
    var camera by remember(itin) { mutableStateOf(MapCamera(centerOf(itin), 13.5, fit = routePoints)) }
    val active = session.activeTrip?.id == itin.id
    val scaffoldState = rememberBottomSheetScaffoldState()
    val destinationLabel = session.request?.destinationLabel?.takeIf { it.isNotBlank() } ?: (itin.legs.last() as? Leg.Walk)?.toLabel ?: ""

    // --- guidance while the trip is active ---
    var maxLeg by remember(itin) { mutableStateOf(0) }
    var follow by remember { mutableStateOf(true) }
    var arrivalShown by remember(itin) { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    val guidance = remember(active, position, maxLeg) { val p = position; if (active && p != null) TripGuide.guide(itin, p, maxLeg) else null }
    LaunchedEffect(guidance?.legIndex) { guidance?.let { if (it.legIndex > maxLeg) maxLeg = it.legIndex } }
    LaunchedEffect(active, position, follow) { val p = position; if (active && follow && p != null) camera = MapCamera(p, 16.0) }

    val centerOnMe = rememberCenterOnMe { p -> follow = true; camera = MapCamera(p, 15.5, seq = camera.seq + 1) }

    fun startTrip() {
        session.activeTrip = itin
        session.tripStartedAt = System.currentTimeMillis()
        maxLeg = 0; arrivalShown = false; follow = true
        c.location.startTracking()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) startTrip() else permissionDenied = true
    }
    fun onStart() {
        if (c.location.hasPermission()) startTrip()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    fun finishTrip(arrived: Boolean) {
        val now = System.currentTimeMillis()
        val started = session.tripStartedAt.takeIf { it > 0 } ?: now
        val record = TripRecord(
            id = "${itin.id}:$started", startedAt = started, finishedAt = now,
            originLabel = session.request?.originLabel ?: "", destinationLabel = destinationLabel,
            originLat = itin.legs.first().from.lat, originLng = itin.legs.first().from.lng,
            destLat = itin.legs.last().to.lat, destLng = itin.legs.last().to.lng,
            lineIds = itin.lines.map { it.lineId }, lineDisplays = itin.lines.map { it.display },
            plannedMinutes = itin.totalMinutes, actualMinutes = ((now - started) / 60_000L).toInt(),
            walkMeters = itin.walkMeters, transfers = itin.transfers, fareBs = itin.fareBs, arrived = arrived,
        )
        c.location.stopTracking()
        session.activeTrip = null
        scope.launch {
            c.trips.add(record)
            c.settings.recordCompletedTrip()
            session.showNps = c.settings.shouldAskNps()
            if (!session.showNps) onHome()
        }
    }
    fun share() {
        val text = context.getString(
            R.string.share_text, session.request?.originLabel ?: context.getString(R.string.my_location), destinationLabel,
            itin.totalMinutes, itin.lines.joinToString(" + ") { it.display }.ifBlank { context.getString(R.string.walking_only) }, itin.fareBs,
        )
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, null))
    }

    val content = remember(itin, camera, t.dark, position, active) {
        mapContentFor(itin, camera, t.dark, if (active) position else null) { line, variant -> LineColors.colorOf(line, variant) }
    }

    Box(Modifier.fillMaxSize().background(t.bg)) {
        BottomSheetScaffold(
            modifier = Modifier.fillMaxSize(),
            scaffoldState = scaffoldState,
            sheetPeekHeight = SHEET_PEEK + ACTION_BAR,
            sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            sheetContainerColor = t.surface,
            sheetContentColor = t.ink,
            containerColor = Color.Transparent,
            sheetContent = {
                Column(
                    Modifier.fillMaxHeight(0.64f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = ACTION_BAR + 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Collapsed header: guidance while on the way, otherwise "27 min | llegás 22:40" + star.
                    if (guidance != null) GuidanceHeader(itin, guidance, destinationLabel)
                    else Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(itin.totalMinutes.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = t.ink)
                        Text(" min", fontSize = 15.sp, color = t.ink)
                        Text("  |  ", fontSize = 15.sp, color = t.muted)
                        Text(stringResource(R.string.arrival_time, arrivalTime(itin.totalMinutes)), fontSize = 15.sp, color = t.ink, modifier = Modifier.weight(1f))
                        IconCircleButton(if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, stringResource(R.string.save_route), onClick = { starred = !starred }, filled = true, tint = if (starred) t.accent else t.primary)
                    }
                    // Segment strip in its own box, like Moovit.
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, t.line, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        SegmentStrip(itin)
                    }
                    HorizontalDivider(color = t.line)
                    // Timeline
                    itin.legs.forEachIndexed { i, leg -> Step(leg, last = i == itin.legs.lastIndex, current = guidance?.legIndex == i, walkOnly = itin.rides.isEmpty()) }
                }
            },
        ) {
            // The map ignores the scaffold padding on purpose: it fills the screen and shows under the sheet.
            Box(Modifier.fillMaxSize()) {
                c.mapProvider.Map(Modifier.fillMaxSize(), content, onTap = null, onCameraIdle = null, drag = null)
                Row(Modifier.statusBarsPadding().padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack, filled = true)
                        if (!active) StartButton(onClick = ::onStart)
                        else FilledTonalButton(onClick = { follow = true; position?.let { camera = MapCamera(it, 16.0, seq = camera.seq + 1) } }) {
                            Icon(Icons.Rounded.Navigation, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.trip_in_progress, elapsedMinutes(session.tripStartedAt)))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconCircleButton(Icons.Rounded.MyLocation, stringResource(R.string.center_on_me), onClick = centerOnMe, filled = true)
                        IconCircleButton(Icons.Rounded.CenterFocusStrong, stringResource(R.string.fit_route), onClick = { follow = false; camera = MapCamera(centerOf(itin), 13.5, fit = routePoints, seq = camera.seq + 1) }, filled = true)
                    }
                }
            }
        }
        // Fixed action bar under the sheet (Moovit: Start · Route view · Share ride).
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(t.surface).navigationBarsPadding().height(ACTION_BAR)
                .horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!active) StartButton(onClick = ::onStart)
            else Button(onClick = { finishTrip(arrived = guidance?.arrived == true) }, colors = ButtonDefaults.buttonColors(containerColor = t.danger, contentColor = Color.White)) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.end_trip))
            }
            FilledTonalButton(onClick = { scope.launch { scaffoldState.bottomSheetState.expand() } }) {
                Icon(Icons.AutoMirrored.Rounded.List, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_steps))
            }
            FilledTonalButton(onClick = ::share) {
                Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_share))
            }
        }
    }

    // Arrival: offer to finish (saves the trip); the user may keep going.
    if (guidance?.arrived == true && !arrivalShown) AlertDialog(
        onDismissRequest = { arrivalShown = true },
        title = { Text(stringResource(R.string.arrived_title, destinationLabel)) },
        text = { Text(stringResource(R.string.arrived_body)) },
        confirmButton = { TextButton(onClick = { arrivalShown = true; finishTrip(arrived = true) }) { Text(stringResource(R.string.end_trip)) } },
        dismissButton = { TextButton(onClick = { arrivalShown = true }) { Text(stringResource(R.string.keep_going)) } },
    )
    if (permissionDenied) AlertDialog(
        onDismissRequest = { permissionDenied = false },
        title = { Text(stringResource(R.string.location_needed_title)) },
        text = { Text(stringResource(R.string.location_needed_body)) },
        confirmButton = { TextButton(onClick = { permissionDenied = false }) { Text(stringResource(R.string.close)) } },
    )
    if (session.showNps) NpsSheet(itin, onClose = { session.showNps = false; onHome() })
}

private val SHEET_PEEK = 158.dp
private val ACTION_BAR = 72.dp

@Composable
private fun StartButton(onClick: () -> Unit) {
    val t = LocalPiyo.current
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = t.primary, contentColor = t.onPrimary)) {
        Icon(Icons.Rounded.NearMe, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.start_trip), fontWeight = FontWeight.Medium)
    }
}

/** What to do now, from [TripGuide]: one line in bold, one detail line. */
@Composable
private fun GuidanceHeader(itin: Itinerary, g: TripGuide.Guidance, destinationLabel: String) {
    val t = LocalPiyo.current
    val leg = itin.legs[g.legIndex]
    val dist = Geo.formatDistance(g.remainingM.toDouble())
    val nextRide = itin.legs.drop(g.legIndex).filterIsInstance<Leg.Ride>().firstOrNull()
    val (title, detail) = when (g.phase) {
        TripGuide.Phase.WALK_TO_BOARD -> stringResource(R.string.guide_walk_to, dist, (leg as Leg.Walk).toLabel) to (nextRide?.let { stringResource(R.string.guide_then_board, it.line.display) } ?: "")
        TripGuide.Phase.RIDE -> stringResource(R.string.guide_ride, (leg as Leg.Ride).line.display, leg.alightLabel) to stringResource(R.string.guide_remaining, dist)
        TripGuide.Phase.PREPARE_ALIGHT -> stringResource(R.string.guide_prepare, (leg as Leg.Ride).alightLabel) to stringResource(R.string.guide_remaining, dist)
        TripGuide.Phase.WALK_TO_DESTINATION -> stringResource(R.string.guide_walk_to, dist, destinationLabel) to ""
        TripGuide.Phase.ARRIVED -> stringResource(R.string.arrived_title, destinationLabel) to stringResource(R.string.arrived_body)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (leg is Leg.Ride) LineChip(leg.line, color = LineColors.colorOf(leg.line, leg.variant))
        else Box(Modifier.size(36.dp).clip(CircleShape).background(t.primarySoft), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Navigation, null, tint = t.primary, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = t.ink, maxLines = 2)
            val sub = if (g.offRoute) stringResource(R.string.guide_off_route, Geo.formatDistance(g.offRouteM.toDouble())) else detail
            if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = if (g.offRoute) t.danger else t.muted)
        }
    }
}

private fun elapsedMinutes(startedAt: Long): Int = if (startedAt <= 0) 0 else ((System.currentTimeMillis() - startedAt) / 60_000L).toInt()

@Composable
private fun Step(leg: Leg, last: Boolean, current: Boolean, walkOnly: Boolean = false) {
    val t = LocalPiyo.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.then(if (current) Modifier.clip(RoundedCornerShape(12.dp)).background(t.primarySoft).padding(8.dp) else Modifier)) {
        Box(Modifier.width(24.dp).height(if (leg is Leg.Ride) 56.dp else 40.dp), contentAlignment = Alignment.TopCenter) {
            when (leg) {
                is Leg.Walk -> Box(Modifier.padding(top = 4.dp).size(14.dp).clip(CircleShape).background(t.surface).border(3.dp, if (last) t.accent else t.ink, CircleShape))
                is Leg.Ride -> Box(Modifier.fillMaxSize().padding(vertical = 2.dp).width(6.dp).clip(CircleShape).background(LineColors.colorOf(leg.line, leg.variant)))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when (leg) {
                is Leg.Walk -> {
                    val title = when { walkOnly -> stringResource(R.string.walk_all_the_way, leg.meters, leg.toLabel); last -> stringResource(R.string.get_off_at, leg.toLabel); else -> stringResource(R.string.walk_to, leg.meters, leg.toLabel) }
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = t.ink)
                    Text(when { walkOnly -> stringResource(R.string.arrive_walking, leg.minutes); last -> stringResource(R.string.walk_final, leg.meters, leg.minutes, leg.toLabel); else -> stringResource(R.string.wait_corner, leg.minutes) }, fontSize = 13.sp, color = t.muted)
                    Text(stringResource(R.string.walk_straight_hint), fontSize = 12.sp, color = t.muted)
                }
                is Leg.Ride -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LineChip(leg.line, color = LineColors.colorOf(leg.line, leg.variant))
                    Column {
                        Text("${leg.boardLabel} → ${leg.alightLabel}", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = t.ink)
                        if (leg.variant.branch.isNotBlank()) Text(stringResource(R.string.ride_branch, leg.variant.branch, leg.variant.headsign), fontSize = 13.sp, color = t.muted)
                        Text(stringResource(R.string.ride_detail, Geo.formatKm(leg.meters.toDouble()), leg.minutes, leg.fareBs), fontSize = 13.sp, color = t.muted)
                    }
                }
            }
        }
    }
}

internal fun centerOf(it: Itinerary): LatLng {
    val pts = it.legs.flatMap { l -> listOf(l.from, l.to) }
    return LatLng(pts.map { p -> p.lat }.average(), pts.map { p -> p.lng }.average())
}

/**
 * Lines: each ride in its line colour (the provider draws direction arrows along it), each walk dashed.
 * Points: where you board each ride, where you get off the last one, and the user ([userPos], live while
 * the trip is active; otherwise the planned origin). No intermediate "stop" dots: micros stop on request.
 */
internal fun mapContentFor(it: Itinerary, camera: MapCamera, dark: Boolean, userPos: LatLng?, colorOf: (Line, Variant?) -> Color): MapContent {
    val lines = mutableListOf<MapLine>()
    val points = mutableListOf<MapPoint>()
    val ink = if (dark) Color(0xFFF2F3F1) else Color(0xFF15191C)
    val accent = if (dark) Color(0xFFF6B45A) else Color(0xFFF2A33A)
    it.legs.forEachIndexed { i, leg ->
        when (leg) {
            is Leg.Walk -> lines += MapLine("walk$i", listOf(leg.from, leg.to), ink, dashed = true)
            is Leg.Ride -> {
                val col = colorOf(leg.line, leg.variant)
                lines += MapLine("ride$i", leg.path, col)
                points += MapPoint("board$i", leg.from, col, if (i == 1) MapPoint.Kind.ORIGIN else MapPoint.Kind.TRANSFER)
            }
        }
    }
    points += MapPoint("user", userPos ?: it.legs.first().from, Color(0xFF2F7BF6), MapPoint.Kind.USER)
    points += MapPoint("dest", it.legs.last().to, accent, MapPoint.Kind.DESTINATION)
    return MapContent(camera, lines, points, dark)
}
