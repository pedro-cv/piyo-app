package bo.piyo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditRoad
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import bo.piyo.app.ui.util.Notifier
import bo.piyo.app.ui.util.rememberLocationPermission
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bo.piyo.app.R
import bo.piyo.app.domain.geo.Geo
import bo.piyo.app.domain.model.Favorites
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.Place
import bo.piyo.app.domain.model.TripRequest
import bo.piyo.app.domain.routing.RoutePlanner
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.LocalSession
import bo.piyo.app.ui.components.GroupedList
import bo.piyo.app.ui.components.IconCircleButton
import bo.piyo.app.ui.components.LeadingIcon
import bo.piyo.app.ui.components.ListRow
import bo.piyo.app.ui.components.LineChip
import bo.piyo.app.ui.components.OfflineBanner
import bo.piyo.app.ui.components.RouteCard
import bo.piyo.app.ui.components.SectionHeader
import bo.piyo.app.ui.components.Tab
import bo.piyo.app.ui.theme.BrandStyle
import bo.piyo.app.ui.theme.LocalPiyo
import bo.piyo.app.ui.util.formatAgo
import bo.piyo.app.ui.util.formatShortDate
import bo.piyo.app.ui.util.isOnline
import bo.piyo.app.ui.util.rememberNearbyLines
import kotlinx.coroutines.launch

/** 01 Inicio · Buscar. */
@Composable
fun HomeScreen(
    onSearch: () -> Unit, onResults: () -> Unit, onRoute: (String) -> Unit, onLine: (String) -> Unit,
    onReportMissing: () -> Unit, onSettings: () -> Unit, onTab: (Tab) -> Unit, onPickOrigin: () -> Unit = {},
) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val session = LocalSession.current
    val bundle by c.lines.bundle.collectAsStateWithLifecycle()
    val favorites by c.favorites.favorites.collectAsStateWithLifecycle(initialValue = Favorites())
    val position by c.location.position.collectAsStateWithLifecycle()
    val label by c.location.label.collectAsStateWithLifecycle(initialValue = "")
    val near = rememberNearbyLines(bundle, position, 300.0)
    val trips by c.trips.trips.collectAsStateWithLifecycle(initialValue = emptyList())
    val online = isOnline()
    val myLocationLabel = stringResource(R.string.my_location)
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    // First visit to Inicio: the location prompt, then (Android 13+) the notification prompt, each shown by the
    // app on its own only once. Later, location is asked again only from a "Centrar en mi ubicación" tap and
    // both can be granted from Ajustes. Granting notifications turns the switch on and registers push.
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { c.settings.setNotifications(true); c.push.sync(force = true) }
    }
    fun askNotificationsOnce() = scope.launch {
        if (Build.VERSION.SDK_INT >= 33 && !c.settings.notifPermissionAsked() && !Notifier.permitted(context)) {
            c.settings.setNotifPermissionAsked()
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val askLocation = rememberLocationPermission(onDenied = { askNotificationsOnce() }) { c.location.refresh(); askNotificationsOnce() }
    LaunchedEffect(Unit) {
        c.lines.load(); c.location.refresh(); c.reports.flushQueue(); c.nps.flushQueue(); c.push.sync()
        if (!c.location.hasPermission() && !c.settings.locationPermissionAsked()) {
            c.settings.setLocationPermissionAsked()
            askLocation(false)
        } else askNotificationsOnce()
    }

    fun go(place: Place) {
        val b = bundle ?: return
        val from = session.originPlace
        val origin = from?.position ?: position ?: bo.piyo.app.map.SantaCruz.center
        val req = TripRequest(origin, from?.name ?: myLocationLabel, place.position, place.name)
        session.request = req
        session.destinationPlace = place
        session.itineraries = RoutePlanner(b).plan(req)
        session.selected = session.itineraries.firstOrNull()
        onResults()
    }

    Page(
        titleContent = { Text(stringResource(R.string.brand), style = BrandStyle, color = t.primary, modifier = Modifier.weight(1f).padding(start = 4.dp)) },
        actions = { SettingsAction(onSettings) },
    ) {
        if (!online) OfflineBanner(stringResource(R.string.offline_banner, bundle?.dataVersion ?: 0, formatShortDate(bundle?.generatedAt)))
        bundle?.providerName?.let { pn ->
            Row(Modifier.fillMaxWidth().clickable { onTab(Tab.CONTRIBUTE) }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(androidx.compose.material.icons.Icons.Rounded.EditRoad, null, tint = t.primary, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.using_provider, pn), fontSize = 12.sp, color = t.primary, fontWeight = FontWeight.Medium)
            }
        }
        // 1. Where from (tap to change: search or pick on the map)
        val from = session.originPlace
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onPickOrigin).padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.from), fontSize = 13.sp, color = t.muted)
            Icon(if (from == null) Icons.Rounded.MyLocation else Icons.Rounded.Place, null, tint = if (from == null) t.primary else t.accent, modifier = Modifier.size(18.dp))
            Text(from?.name ?: stringResource(R.string.my_location), fontWeight = FontWeight.Medium, color = if (from == null) t.primary else t.ink, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            if (from == null && label.isNotBlank() && label != myLocationLabel) Text("· $label", fontSize = 13.sp, color = t.muted, maxLines = 1)
            Icon(Icons.Rounded.ChevronRight, null, tint = t.muted, modifier = Modifier.size(18.dp))
        }
        // 2. Where to
        Row(
            Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(16.dp), ambientColor = t.ink.copy(alpha = 0.08f)).clip(RoundedCornerShape(16.dp)).background(t.surface).clickable(onClick = onSearch).height(58.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Search, null, tint = t.muted)
            Text(stringResource(R.string.where_to), color = t.muted, fontSize = 16.sp)
        }
        // 3. Saved places
        // Favorite chips
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            favorites.places.forEach { f ->
                FavChip(bo.piyo.app.ui.components.FavoriteIcons.icon(f.icon), f.label, tint = bo.piyo.app.ui.components.FavoriteIcons.color(f.color)) { f.place?.let { go(it) } ?: onTab(Tab.FAVORITES) }
            }
            FavChip(Icons.Rounded.Add, stringResource(R.string.add_place), muted = true) { onTab(Tab.FAVORITES) }
        }
        // Last route (this session) and finished trips (history on the phone): tap to plan it again from here.
        val last = session.selected ?: session.activeTrip
        if (last != null) {
            SectionHeader(stringResource(R.string.last_route))
            RouteCard(last, title = session.request?.destinationLabel ?: "", onClick = { onRoute(last.id) })
        }
        if (trips.isNotEmpty()) {
            SectionHeader(stringResource(R.string.trips_title))
            val today = stringResource(R.string.trip_today); val yesterday = stringResource(R.string.trip_yesterday)
            GroupedList {
                trips.take(3).forEachIndexed { i, tr ->
                    ListRow(
                        onClick = { go(Place("trip-${tr.id}", tr.destinationLabel, "", tr.destination)) }, divider = i < minOf(2, trips.lastIndex),
                        leading = { LeadingIcon(Icons.Rounded.History, t.muted, t.surface2) },
                        trailing = { bundle?.lines?.filter { l -> l.lineId in tr.lineIds }?.take(2)?.forEach { l -> LineChip(l, small = true) } },
                    ) {
                        Text(tr.destinationLabel, color = t.ink, fontSize = 16.sp, maxLines = 1)
                        Text(stringResource(R.string.trip_row_meta, formatAgo(tr.finishedAt, today, yesterday) { d -> context.getString(R.string.days_ago, d) }, if (tr.actualMinutes > 0) tr.actualMinutes else tr.plannedMinutes), color = t.muted, fontSize = 13.sp)
                    }
                }
            }
        }
        // Lines passing within 300 m
        SectionHeader(stringResource(R.string.near_lines))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            near.forEach { (line, d) ->
                Column(Modifier.clickable { onLine(line.lineId) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LineChip(line)
                    Text(Geo.formatDistance(d), fontSize = 11.sp, color = t.muted)
                }
            }
            if (near.isEmpty()) Text("Ninguna línea conocida pasa a menos de 300 m", fontSize = 13.sp, color = t.muted)
        }
        Row(Modifier.fillMaxWidth().clickable(onClick = onReportMissing).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Flag, null, tint = t.muted, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.missing_line_cta), fontSize = 13.sp, color = t.muted)
        }
    }
}

@Composable
private fun FavChip(icon: ImageVector, label: String, muted: Boolean = false, tint: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val t = LocalPiyo.current
    Row(
        Modifier.height(40.dp).clip(CircleShape).background(t.surface).border(1.dp, t.line, CircleShape).clickable(onClick = onClick).padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = tint ?: if (muted) t.muted else t.ink, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (muted) t.muted else t.ink)
    }
}
