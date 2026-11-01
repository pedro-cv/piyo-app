package bo.piyo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bo.piyo.app.R
import bo.piyo.app.domain.model.TripRequest
import bo.piyo.app.domain.routing.RoutePlanner
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.LocalSession
import bo.piyo.app.ui.components.IconCircleButton
import bo.piyo.app.ui.components.Note
import bo.piyo.app.ui.components.PillButton
import bo.piyo.app.ui.components.RouteCard
import bo.piyo.app.ui.components.Tab
import bo.piyo.app.ui.theme.LocalPiyo

/** 02 Resultados de ruta. */
@Composable
fun ResultsScreen(onBack: () -> Unit, onRoute: (String) -> Unit, onTab: (Tab) -> Unit, onPickOrigin: () -> Unit, onPickDestination: () -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val session = LocalSession.current
    // The planner already ranks by fewer rides, then time (RoutePlanner.plan). Three cards first, the rest
    // behind "Ver más" so a long list does not bury the best option.
    var showAll by remember(session.itineraries) { mutableStateOf(false) }
    var sort by remember { mutableStateOf("transfers") }
    val req = session.request
    val all = remember(session.itineraries, sort) {
        if (sort == "walk") session.itineraries.sortedWith(compareBy({ it.walkMeters }, { it.totalMinutes }))
        else session.itineraries.sortedWith(compareBy({ it.transfers }, { it.totalMinutes }))
    }
    val shown = if (showAll) all else all.take(3)

    Page(onBack = onBack, title = stringResource(R.string.route_title), tab = Tab.SEARCH, onTab = onTab) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Both ends are editable: tap to search or pick on the map, the results are planned again.
                Field(Icons.Rounded.MyLocation, t.primary, req?.originLabel ?: stringResource(R.string.my_location), onClick = onPickOrigin)
                Field(Icons.Rounded.Place, t.accent, req?.destinationLabel ?: "", onClick = onPickDestination)
            }
            IconCircleButton(Icons.Rounded.SwapVert, stringResource(R.string.swap), filled = true, onClick = {
                val r = req ?: return@IconCircleButton
                val b = c.lines.bundle.value ?: return@IconCircleButton
                val swapped = TripRequest(r.destination, r.destinationLabel, r.origin, r.originLabel)
                session.request = swapped
                session.itineraries = RoutePlanner(b).plan(swapped)
                session.selected = session.itineraries.firstOrNull()
            })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(stringResource(R.string.filter_transfers), selected = sort == "transfers", icon = Icons.Rounded.CheckCircle, onClick = { sort = "transfers" })
            PillButton(stringResource(R.string.filter_walk), selected = sort == "walk", icon = Icons.AutoMirrored.Rounded.DirectionsWalk, onClick = { sort = "walk" })
        }
        if (all.isEmpty()) Text(stringResource(R.string.no_routes), color = t.muted, fontSize = 14.sp)
        shown.forEachIndexed { i, it ->
            RouteCard(it, selected = i == 0, onClick = { session.selected = it; onRoute(it.id) })
        }
        if (all.size > 3) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            PillButton(
                if (showAll) stringResource(R.string.results_less) else stringResource(R.string.results_more, all.size - 3),
                selected = false, icon = if (showAll) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, onClick = { showAll = !showAll },
            )
        }
        Note(stringResource(R.string.results_note) + " " + stringResource(R.string.fares_line))
    }
}

@Composable
private fun Field(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String, onClick: () -> Unit) {
    val t = LocalPiyo.current
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(t.surface).border(1.dp, t.line, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, color = t.ink, fontSize = 15.sp, maxLines = 1)
    }
}
