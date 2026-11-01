package bo.piyo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bo.piyo.app.R
import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.Place
import bo.piyo.app.domain.model.TripRequest
import bo.piyo.app.domain.routing.RoutePlanner
import bo.piyo.app.map.MapCamera
import bo.piyo.app.map.MapContent
import bo.piyo.app.map.SantaCruz
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.LocalSession
import bo.piyo.app.ui.components.GroupedList
import bo.piyo.app.ui.components.IconCircleButton
import bo.piyo.app.ui.components.LeadingIcon
import bo.piyo.app.ui.components.ListRow
import bo.piyo.app.ui.components.PiyoButton
import bo.piyo.app.ui.components.SectionHeader
import bo.piyo.app.ui.components.SkeletonList
import bo.piyo.app.ui.theme.LocalPiyo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Choose a point: text search (Photon) with skeleton loading, recent searches, and a full-screen
 * "pick on the map" mode where the map moves under a fixed pin and the address is resolved as you go.
 * purpose = destination | origin (the trip's start; "Mi ubicación" is offered first) | home | work.
 */
@Composable
fun SearchScreen(purpose: String, onBack: () -> Unit, onResults: () -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var recents by remember { mutableStateOf<List<String>>(emptyList()) }
    var pickOnMap by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val position by c.location.position.collectAsStateWithLifecycle()
    val myLocationLabel = stringResource(R.string.my_location)
    val pointOnMapLabel = stringResource(R.string.point_on_map)
    val json = remember { Json }

    LaunchedEffect(Unit) {
        recents = c.settings.recentSearchesJson.first()?.let { runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull() } ?: emptyList()
        focus.requestFocus()
    }
    // Debounced search with a loading state: skeleton rows while Photon answers (first load too).
    LaunchedEffect(query) {
        loading = true
        if (query.isNotEmpty()) delay(300)
        results = c.mapProvider.searchPlaces(query, position)
        loading = false
    }

    /** Plans (or re-plans) the trip with the current session request and the chosen end. */
    fun plan(origin: LatLng, originLabel: String, destination: LatLng, destinationLabel: String) {
        scope.launch {
            val b = c.lines.load()
            val req = TripRequest(origin, originLabel, destination, destinationLabel)
            session.request = req
            session.itineraries = RoutePlanner(b).plan(req)
            session.selected = session.itineraries.firstOrNull()
            onResults()
        }
    }

    fun choose(place: Place) {
        scope.launch {
            if (purpose != "origin") {
                val newRecents = (listOf(place.name) + recents).distinct().take(5)
                c.settings.setRecentSearchesJson(json.encodeToString(ListSerializer(String.serializer()), newRecents))
            }
            when (purpose) {
                "home" -> { c.favorites.setHome(place); onBack() }
                "work" -> { c.favorites.setWork(place); onBack() }
                "origin" -> {
                    session.originPlace = place
                    val r = session.request
                    if (r != null) plan(place.position, place.name, r.destination, r.destinationLabel) else onBack()
                }
                else -> if (purpose.startsWith("place:")) { c.favorites.setPlaceLocation(purpose.removePrefix("place:"), place); onBack() } else {
                    val from = session.originPlace
                    session.destinationPlace = place
                    plan(from?.position ?: position ?: SantaCruz.center, from?.name ?: myLocationLabel, place.position, place.name)
                }
            }
        }
    }

    fun chooseMyLocation() {
        session.originPlace = null
        val r = session.request
        val p = position ?: SantaCruz.center
        if (r != null) plan(p, myLocationLabel, r.destination, r.destinationLabel) else onBack()
    }

    if (pickOnMap) {
        MapPicker(
            initial = session.destinationPlace?.position?.takeIf { purpose == "destination" } ?: position ?: SantaCruz.center,
            onCancel = { pickOnMap = false },
            onPick = { p, name -> choose(Place("pin-${p.lat}-${p.lng}", name ?: pointOnMapLabel, "", p)) },
        )
        return
    }

    // The field lives in the body, inside the same 18 dp gutters as the lists under it, so edges line up.
    Page(onBack = onBack, title = stringResource(when { purpose == "origin" -> R.string.origin_title; purpose == "home" || purpose == "work" || purpose.startsWith("place:") -> R.string.place_title; else -> R.string.search_title })) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(t.surface).border(1.dp, t.line, RoundedCornerShape(14.dp)).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Search, null, tint = t.muted, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(stringResource(if (purpose == "origin") R.string.choose_origin else R.string.search_hint), color = t.muted, fontSize = 15.sp)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = t.ink, fontSize = 15.sp), cursorBrush = SolidColor(t.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let(::choose) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) IconCircleButton(Icons.Rounded.Close, stringResource(R.string.clear_search), onClick = { query = "" }, tint = t.muted)
        }
        // Quick options first: the phone's position (origin) and the map picker.
        GroupedList {
            if (purpose == "origin") ListRow(onClick = ::chooseMyLocation, leading = { LeadingIcon(Icons.Rounded.MyLocation) }) {
                Text(myLocationLabel, color = t.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            ListRow(onClick = { pickOnMap = true }, leading = { LeadingIcon(Icons.Rounded.Map, t.accent, t.surface2) }, divider = false) {
                Text(stringResource(R.string.pick_on_map), color = t.ink, fontSize = 16.sp)
            }
        }
        if (query.isEmpty() && recents.isNotEmpty()) {
            SectionHeader(stringResource(R.string.search_recent))
            GroupedList { recents.forEachIndexed { i, r -> ListRow(onClick = { query = r }, divider = i < recents.lastIndex, leading = { LeadingIcon(Icons.Rounded.History, t.muted, t.surface2) }) { Text(r, color = t.ink, fontSize = 16.sp) } } }
        }
        SectionHeader(stringResource(R.string.search_results))
        if (loading) SkeletonList(rows = if (query.isEmpty()) 3 else 5)
        else if (results.isEmpty() && query.isNotEmpty()) Text(stringResource(R.string.search_empty), color = t.muted, fontSize = 14.sp)
        if (!loading) GroupedList {
            results.forEachIndexed { i, p ->
                ListRow(onClick = { choose(p) }, leading = { LeadingIcon(Icons.Rounded.Place, t.accent, t.surface2) }, divider = i < results.lastIndex) {
                    Text(p.name, color = t.ink, fontSize = 16.sp)
                    if (p.detail.isNotBlank()) Text(p.detail, color = t.muted, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Full-screen map picker: the map moves under a fixed pin; when it settles, the address under the pin is
 * reverse-geocoded (debounced) and shown in the bottom card with the confirm button.
 */
@Composable
private fun MapPicker(initial: LatLng, onCancel: () -> Unit, onPick: (LatLng, String?) -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var center by remember { mutableStateOf(initial) }
    var address by remember { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf(true) }
    var job by remember { mutableStateOf<Job?>(null) }
    var lastResolved by remember { mutableStateOf<LatLng?>(null) }
    val cache = remember { HashMap<String, String?>() }
    val camera = remember { MapCamera(initial, 16.0) }
    // One reverse-geocode per settled position: wait 800 ms of stillness, ignore moves under 20 m,
    // and remember answers for the same ~10 m cell, so panning around costs very few requests.
    fun resolve(p: LatLng) {
        job?.cancel()
        val prev = lastResolved
        if (prev != null && bo.piyo.app.domain.geo.Geo.distanceM(prev, p) < 20.0) return
        val key = "%.4f,%.4f".format(p.lat, p.lng)
        cache[key]?.let { address = it; resolving = false; lastResolved = p; return }
        job = scope.launch {
            resolving = true
            delay(800)
            val a = c.mapProvider.describe(p)
            cache[key] = a; address = a; lastResolved = p; resolving = false
        }
    }
    LaunchedEffect(Unit) { resolve(initial) }

    Box(Modifier.fillMaxSize().background(t.bg)) {
        c.mapProvider.Map(Modifier.fillMaxSize(), MapContent(camera, darkTheme = t.dark), onTap = null, onCameraIdle = { p -> center = p; resolve(p) }, drag = null)
        // Fixed pin, same shape as the map markers (white body, accent head, no glyph); the tip is on the map centre.
        PinShape(color = t.accent, modifier = Modifier.align(Alignment.Center).size(width = 40.dp, height = 46.dp).offset(y = (-23).dp))
        Row(Modifier.statusBarsPadding().padding(14.dp)) { IconCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onCancel, filled = true) }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(t.surface).padding(horizontal = 18.dp, vertical = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.pick_move_hint), color = t.muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PinShape(color = t.accent, modifier = Modifier.size(width = 20.dp, height = 23.dp))
                if (resolving) Box(Modifier.fillMaxWidth(0.7f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(t.surface2))
                else Text(address ?: stringResource(R.string.point_on_map), color = t.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2)
            }
            PiyoButton(stringResource(R.string.use_point), onClick = { onPick(center, address) })
        }
    }
}

/** The app's map pin drawn in Compose (reference outline, 52 × 60 viewBox): white body, coloured head, white dot. */
@Composable
private fun PinShape(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val body = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString(bo.piyo.app.map.maplibre.MapLibreMapProvider.PIN_BODY).toPath() }
    androidx.compose.foundation.Canvas(modifier) {
        val k = size.width / 52f
        scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(body, androidx.compose.ui.graphics.Color.White)
            drawPath(body, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.1f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            drawCircle(color, radius = 17f, center = androidx.compose.ui.geometry.Offset(26f, 25f))
            drawCircle(androidx.compose.ui.graphics.Color.White, radius = 4.5f, center = androidx.compose.ui.geometry.Offset(26f, 25f))
        }
    }
}
