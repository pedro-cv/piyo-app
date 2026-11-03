package bo.piyo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bo.piyo.app.R
import bo.piyo.app.domain.LineColors
import bo.piyo.app.domain.geo.Geo
import bo.piyo.app.domain.model.Favorites
import bo.piyo.app.domain.model.Line
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.components.IconCircleButton
import bo.piyo.app.ui.components.Tab
import bo.piyo.app.ui.theme.LineNumberStyle
import bo.piyo.app.ui.theme.LocalPiyo
import bo.piyo.app.ui.util.rememberNearestByLine
import kotlinx.coroutines.flow.filter

/** Everything a row needs, computed once per filter change instead of inside every item composition. */
private data class LineRowModel(val line: Line, val color: Color, val route: String, val branches: Int, val numberSize: Int)

/**
 * Directorio de líneas: search by number or barrio (with a clear button), one card per line with a
 * bus tile in the line colour, the route "origin → destination", "pasa a X m" and the favorite star.
 *
 * Scroll performance: rows are precomputed models (no string building or colour parsing per item),
 * the vector icons are rasterised once and shared by all rows, and the list reveals itself in batches
 * of [BATCH] rows (the next batch is appended when the user is [PREFETCH] rows from the end), so a
 * fling never has to compose dozens of fresh cards in one frame.
 */
@Composable
fun LinesScreen(onLine: (String) -> Unit, onTab: (Tab) -> Unit, onSettings: () -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val bundle by c.lines.bundle.collectAsStateWithLifecycle()
    val favorites by c.favorites.favorites.collectAsStateWithLifecycle(initialValue = Favorites())
    val position by c.location.position.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val nearest = rememberNearestByLine(bundle, position)
    val rows = remember(bundle, query) {
        val q = query.trim().lowercase()
        (bundle?.lines ?: emptyList())
            .filter { q.isEmpty() || it.display.lowercase().startsWith(q) || it.name.lowercase().contains(q) || it.variants.any { v -> v.streets.any { s -> s.lowercase().contains(q) } } }
            .sortedWith(compareBy({ it.display.toIntOrNull() ?: Int.MAX_VALUE }, { it.display }))
            .map { line ->
                val first = line.primaryVariant
                val route = first?.let { v -> if (v.origin.isNotBlank()) "${v.origin} → ${v.headsign}" else v.headsign }?.takeIf { it != line.name } ?: ""
                LineRowModel(line, LineColors.colorOf(line), route, line.branches.size, if (line.display.length > 3) 14 else 17)
            }
    }

    // Batched reveal
    val listState = rememberLazyListState()
    var shown by remember(rows) { mutableIntStateOf(minOf(BATCH, rows.size)) }
    val nearEnd by remember(listState, rows) { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= shown - PREFETCH } }
    LaunchedEffect(rows) {
        snapshotFlowOf { nearEnd }.filter { it }.collect { if (shown < rows.size) shown = minOf(shown + BATCH, rows.size) }
    }
    val visible = remember(rows, shown) { rows.take(shown) }

    // Icons rasterised once for every row
    val busIcon = rememberVectorPainter(Icons.Rounded.DirectionsBus)
    val walkIcon = rememberVectorPainter(Icons.AutoMirrored.Rounded.DirectionsWalk)
    val starIcon = rememberVectorPainter(Icons.Rounded.Star)
    val chevronIcon = rememberVectorPainter(Icons.Rounded.ChevronRight)

    Page(title = stringResource(R.string.lines_title), scroll = false, actions = { SettingsAction(onSettings) }) {
        // Search field with a clear button
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(t.surface).border(1.dp, t.line, RoundedCornerShape(14.dp)).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Search, null, tint = t.muted, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(stringResource(R.string.lines_search_hint), color = t.muted, fontSize = 15.sp)
                BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = t.ink, fontSize = 15.sp), cursorBrush = SolidColor(t.primary), modifier = Modifier.fillMaxWidth())
            }
            if (query.isNotEmpty()) IconCircleButton(Icons.Rounded.Close, stringResource(R.string.clear_search), onClick = { query = "" }, tint = t.muted)
        }
        LazyColumn(
            Modifier.fillMaxSize(), state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp), // breathing room above the tab bar
        ) {
            items(visible, key = { it.line.lineId }, contentType = { "line" }) { m ->
                LineRow(m, nearest[m.line.lineId]?.distanceM, starred = m.line.lineId in favorites.lineIds, busIcon, walkIcon, starIcon, chevronIcon, onClick = { onLine(m.line.lineId) })
            }
        }
    }
}

private const val BATCH = 24
private const val PREFETCH = 8

/** Observes a snapshot-backed value as a flow (small local helper, avoids importing the whole snapshotFlow name). */
private fun <T> snapshotFlowOf(block: () -> T) = androidx.compose.runtime.snapshotFlow(block)

/** Card: bus tile with the number in the line colour, name + route, distance, star, chevron. */
@Composable
private fun LineRow(m: LineRowModel, distanceM: Double?, starred: Boolean, busIcon: Painter, walkIcon: Painter, starIcon: Painter, chevronIcon: Painter, onClick: () -> Unit) {
    val t = LocalPiyo.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(t.surface).border(1.dp, t.line, shape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(m.color),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Icon(busIcon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
            Text(m.line.display, style = LineNumberStyle.copy(fontSize = m.numberSize.sp), color = Color.White, maxLines = 1)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(m.line.name, color = t.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (m.branches > 1) Text(stringResource(R.string.n_branches, m.branches), color = t.muted, fontSize = 12.sp)
                m.line.local?.let { Box(Modifier.clip(RoundedCornerShape(6.dp)).background(t.primarySoft).padding(horizontal = 6.dp, vertical = 1.dp)) { Text(stringResource(if (it == bo.piyo.app.domain.model.LocalEdit.ADDED) R.string.local_badge_added else R.string.local_badge_updated), color = t.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium) } }
            }
            if (m.route.isNotBlank()) Text(m.route, color = t.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (distanceM != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(walkIcon, null, tint = t.primary, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.passes_at, Geo.formatDistance(distanceM)), color = t.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (starred) Icon(starIcon, null, tint = t.accent, modifier = Modifier.size(20.dp))
        Icon(chevronIcon, null, tint = t.muted, modifier = Modifier.size(22.dp))
    }
}
