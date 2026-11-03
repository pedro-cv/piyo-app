package bo.piyo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bo.piyo.app.R
import bo.piyo.app.domain.geo.Geo
import bo.piyo.app.domain.model.FavoritePlace
import bo.piyo.app.domain.model.Favorites
import bo.piyo.app.domain.model.Place
import bo.piyo.app.domain.model.TripRequest
import bo.piyo.app.domain.routing.RoutePlanner
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.LocalSession
import bo.piyo.app.ui.components.FavoriteIcons
import bo.piyo.app.ui.components.GroupedList
import bo.piyo.app.ui.components.IconCircleButton
import bo.piyo.app.ui.components.LeadingIcon
import bo.piyo.app.ui.components.LineChip
import bo.piyo.app.ui.components.ListRow
import bo.piyo.app.ui.components.Note
import bo.piyo.app.ui.components.PillButton
import bo.piyo.app.ui.components.SectionHeader
import bo.piyo.app.ui.components.Tab
import bo.piyo.app.ui.theme.LocalPiyo
import bo.piyo.app.ui.util.formatAgo
import bo.piyo.app.ui.util.rememberNearestByLine
import kotlinx.coroutines.launch

/** 05 Favoritos: saved places (icon + colour, add/edit/remove), favorite lines, trip history. Nothing leaves the phone. */
@Composable
fun FavoritesScreen(onResults: () -> Unit, onPickPlace: (String) -> Unit, onLine: (String) -> Unit, onTab: (Tab) -> Unit, onSettings: () -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    val favorites by c.favorites.favorites.collectAsStateWithLifecycle(initialValue = Favorites())
    val bundle by c.lines.bundle.collectAsStateWithLifecycle()
    val position by c.location.position.collectAsStateWithLifecycle()
    val myLocationLabel = stringResource(R.string.my_location)
    val trips by c.trips.trips.collectAsStateWithLifecycle(initialValue = emptyList())
    val nearest = rememberNearestByLine(bundle, position)
    val context = androidx.compose.ui.platform.LocalContext.current
    var editing by remember { mutableStateOf<FavoritePlace?>(null) }
    var adding by remember { mutableStateOf(false) }

    fun go(place: Place) {
        val b = bundle ?: return
        val req = TripRequest(position ?: bo.piyo.app.map.SantaCruz.center, myLocationLabel, place.position, place.name)
        session.request = req; session.destinationPlace = place
        session.itineraries = RoutePlanner(b).plan(req)
        session.selected = session.itineraries.firstOrNull()
        onResults()
    }

    Page(title = stringResource(R.string.favorites_title), actions = { SettingsAction(onSettings) }) {
        SectionHeader(stringResource(R.string.places))
        GroupedList {
            favorites.places.forEach { f ->
                ListRow(
                    onClick = { f.place?.let(::go) ?: onPickPlace("place:${f.id}") },
                    leading = { PlaceIcon(f) },
                    trailing = { IconCircleButton(Icons.Rounded.Edit, stringResource(R.string.place_edit), tint = t.muted, onClick = { editing = f }) },
                ) {
                    Text(f.label, color = t.ink, fontSize = 16.sp)
                    if (f.place != null) Text(stringResource(R.string.tap_to_go, f.place.name), color = t.muted, fontSize = 13.sp)
                    else Text(stringResource(R.string.choose_address), color = t.primary, fontSize = 13.sp)
                }
            }
            ListRow(onClick = { adding = true }, leading = { LeadingIcon(Icons.Rounded.Add, t.muted, Color.Transparent) }, divider = false) {
                Text(stringResource(R.string.add_place_long), color = t.muted, fontSize = 16.sp)
            }
        }
        SectionHeader(stringResource(R.string.tab_lines))
        val favLines = bundle?.lines?.filter { it.lineId in favorites.lineIds } ?: emptyList()
        if (favLines.isEmpty()) Note(stringResource(R.string.no_favorite_lines))
        else GroupedList {
            favLines.forEachIndexed { i, line ->
                val near = nearest[line.lineId]
                ListRow(
                    onClick = { onLine(line.lineId) }, divider = i < favLines.lastIndex,
                    leading = { LineChip(line) },
                    trailing = { IconCircleButton(Icons.Rounded.Star, stringResource(R.string.remove_favorite), tint = t.accent, onClick = { scope.launch { c.favorites.toggleLine(line.lineId) } }) },
                ) {
                    Text(stringResource(R.string.line_n, line.display), color = t.ink, fontSize = 16.sp)
                    Text(line.name + (near?.let { " · a ${Geo.formatDistance(it.distanceM)}" } ?: ""), color = t.muted, fontSize = 13.sp)
                }
            }
        }
        // Trip history: every finished trip, newest first; tap to plan it again from here.
        SectionHeader(stringResource(R.string.trips_title))
        if (trips.isEmpty()) Note(stringResource(R.string.trips_empty))
        else {
            val today = stringResource(R.string.trip_today); val yesterday = stringResource(R.string.trip_yesterday)
            GroupedList {
                trips.forEachIndexed { i, tr ->
                    ListRow(
                        onClick = { go(Place("trip-${tr.id}", tr.destinationLabel, "", tr.destination)) }, divider = i < trips.lastIndex,
                        leading = { LeadingIcon(Icons.Rounded.History, t.muted, t.surface2) },
                        trailing = {
                            bundle?.lines?.filter { l -> l.lineId in tr.lineIds }?.take(2)?.forEach { l -> LineChip(l, small = true) }
                            IconCircleButton(Icons.Rounded.Close, stringResource(R.string.remove_trip), tint = t.muted, onClick = { scope.launch { c.trips.remove(tr.id) } })
                        },
                    ) {
                        Text("${tr.originLabel.ifBlank { myLocationLabel }} → ${tr.destinationLabel}", color = t.ink, fontSize = 15.sp, maxLines = 1)
                        Text(formatAgo(tr.finishedAt, today, yesterday) { d -> context.getString(R.string.days_ago, d) } + " · " + stringResource(R.string.trip_planned_actual, tr.plannedMinutes, tr.actualMinutes), color = t.muted, fontSize = 13.sp)
                    }
                }
            }
            PillButton(stringResource(R.string.clear_trips), onClick = { scope.launch { c.trips.clear() } })
        }
        Note(stringResource(R.string.favorites_note))
    }

    if (editing != null || adding) {
        val target = editing
        PlaceEditorSheet(
            existing = target,
            onDismiss = { editing = null; adding = false },
            onSave = { f -> scope.launch { c.favorites.upsertPlace(f) }; editing = null; adding = false },
            onPickAddress = { f -> scope.launch { c.favorites.upsertPlace(f) }; editing = null; adding = false; onPickPlace("place:${f.id}") },
            onDelete = if (target != null && !target.fixed) { f -> scope.launch { c.favorites.removePlace(f.id) }; editing = null } else null,
        )
    }
}

/** Round icon in the place's own colour. */
@Composable
fun PlaceIcon(f: FavoritePlace, size: Int = 36) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(FavoriteIcons.color(f.color)), contentAlignment = Alignment.Center) {
        Icon(FavoriteIcons.icon(f.icon), null, tint = Color.White, modifier = Modifier.size((size * 0.55f).dp))
    }
}
