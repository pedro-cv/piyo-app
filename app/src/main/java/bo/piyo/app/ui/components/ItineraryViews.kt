package bo.piyo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bo.piyo.app.domain.model.Itinerary
import bo.piyo.app.domain.model.Leg
import bo.piyo.app.ui.theme.LocalPiyo

/** "80 m › [76] › 300 m" segment strip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SegmentStrip(itinerary: Itinerary, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itinerary.legs.forEachIndexed { i, leg ->
            if (i > 0) SegmentArrow()
            when (leg) {
                is Leg.Walk -> WalkSegment(leg.meters)
                is Leg.Ride -> LineChip(leg.line, small = true)
            }
        }
    }
}

/** "1 línea · 0 transbordos · Bs 2,50" meta row. */
@Composable
fun ItineraryMeta(itinerary: Itinerary, extra: String? = null) {
    val t = LocalPiyo.current
    @Composable fun kv(n: String, label: String) = Row {
        Text(n, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = t.ink)
        Text(" $label", fontSize = 13.sp, color = t.muted)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        kv(itinerary.transfers.toString(), if (itinerary.transfers == 1) "transbordo" else "transbordos")
        Row { Text("Bs ", fontSize = 13.sp, color = t.muted); Text(itinerary.fareBs, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = t.ink) }
        if (extra != null) Text(extra, fontSize = 13.sp, color = t.muted)
    }
}

/** Route card from the canvas: segments on the left, duration on the right, meta below. */
@Composable
fun RouteCard(itinerary: Itinerary, selected: Boolean = false, title: String? = null, onClick: () -> Unit) {
    val t = LocalPiyo.current
    PiyoCard(onClick = onClick, selected = selected) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = t.ink, modifier = Modifier.weight(1f))
            else SegmentStrip(itinerary, Modifier.weight(1f))
            Duration(itinerary.totalMinutes)
        }
        if (title != null) SegmentStrip(itinerary)
        ItineraryMeta(itinerary, extra = if (title == null) itinerary.hint else null)
    }
}
