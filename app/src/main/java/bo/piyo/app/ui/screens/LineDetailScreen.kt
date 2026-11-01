package bo.piyo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bo.piyo.app.R
import bo.piyo.app.domain.LineColors
import bo.piyo.app.domain.geo.Geo
import bo.piyo.app.domain.model.Favorites
import bo.piyo.app.domain.model.LineMode
import bo.piyo.app.map.MapCamera
import bo.piyo.app.map.MapContent
import bo.piyo.app.map.MapLine
import bo.piyo.app.map.MapPoint
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.components.IconCircleButton
import bo.piyo.app.ui.components.Note
import bo.piyo.app.ui.components.PiyoButton
import bo.piyo.app.ui.components.Tab
import bo.piyo.app.ui.theme.LocalPiyo
import bo.piyo.app.ui.util.nearestOnLine
import kotlinx.coroutines.launch

/**
 * 04 Detalle de línea: the map fills the screen; a Material 3 [BottomSheetScaffold] sheet (drag to
 * collapse to the colour band, drag up for the branches, Ida/Vuelta and the endpoints) sits on top of it,
 * so the map stays interactive and shows through the rounded corners.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineDetailScreen(lineId: String, onBack: () -> Unit, onReport: (Double, Double) -> Unit, onTab: (Tab) -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val bundle by c.lines.bundle.collectAsStateWithLifecycle()
    val favorites by c.favorites.favorites.collectAsStateWithLifecycle(initialValue = Favorites())
    val position by c.location.position.collectAsStateWithLifecycle()
    val line = bundle?.lines?.firstOrNull { it.lineId == lineId }
    if (line == null) { Box(Modifier.fillMaxSize().background(t.bg)); return }
    // A line may have several named routes ("41 Rojo", "41 Verde"); each has Ida/Vuelta.
    val branches = remember(line) { line.branches }
    var branchIdx by remember(line) { mutableStateOf(0) }
    val branchVariants = remember(line, branchIdx) { line.variantsOf(branches.getOrElse(branchIdx) { "" }) }
    var dir by remember(line, branchIdx) { mutableStateOf(0) }
    val variant = branchVariants.getOrNull(dir) ?: branchVariants.firstOrNull() ?: line.variants.firstOrNull()
    val color = LineColors.colorOf(line, variant)
    val near = remember(line, position) { nearestOnLine(line, position) }
    val pts = variant?.points ?: emptyList()
    // Frame the whole variant when it changes; "center on me" switches to the user.
    var camera by remember(variant) { mutableStateOf(MapCamera(pts.getOrElse(pts.size / 2) { bo.piyo.app.map.SantaCruz.center }, 13.0, fit = pts)) }
    val starred = line.lineId in favorites.lineIds
    val scaffoldState = rememberBottomSheetScaffoldState()

    // Map content: the polyline (drawn with direction arrows by the provider), its two endpoints, the user.
    val content = remember(variant, camera, position, t.dark) {
        val points = mutableListOf<MapPoint>()
        if (pts.size >= 2) {
            points += MapPoint("start", pts.first(), color, MapPoint.Kind.START)
            points += MapPoint("end", pts.last(), color, MapPoint.Kind.END)
        }
        position?.let { points += MapPoint("user", it, Color(0xFF2F7BF6), MapPoint.Kind.USER) }
        val lines = mutableListOf(MapLine("line", pts, color))
        if (near != null && position != null) lines += MapLine("tonear", listOf(position!!, near.first), t.accent, dashed = true)
        MapContent(camera, lines, points, t.dark)
    }

    // No tab bar here: the detail is reached from Líneas and leaves with the back arrow (BottomSheetScaffold
    // positions its sheet against the window, so it must own the full height).
    BottomSheetScaffold(
            modifier = Modifier.fillMaxSize().background(t.bg),
            scaffoldState = scaffoldState,
            sheetPeekHeight = 150.dp,
            sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            sheetContainerColor = t.surface,
            sheetContentColor = t.ink,
            containerColor = Color.Transparent,
            sheetContent = {
                // Scrollable details on top; "Reportar error" and the data credit pinned at the bottom of the sheet.
                Column(Modifier.fillMaxHeight(0.62f).padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 12.dp)) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Colour band (visible when collapsed)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val chipShape = RoundedCornerShape(if (line.lineMode == LineMode.TRUFI) 999.dp else 10.dp)
                        Box(Modifier.height(36.dp).clip(chipShape).background(Color.White.copy(alpha = 0.18f)).border(2.dp, Color.White.copy(alpha = 0.7f), chipShape).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            Text(line.display, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = bo.piyo.app.ui.theme.Sora)
                        }
                        Column(Modifier.weight(1f)) {
                            // Headline "origin → destination": the dataset's origin when it has one, else the other direction's headsign.
                            val other = variant?.origin?.takeIf { it.isNotBlank() }
                                ?: branchVariants.getOrNull(if (dir == 0) 1 else 0)?.headsign?.takeIf { it != variant?.headsign }
                                ?: variant?.streets?.firstOrNull()?.takeIf { it != variant.headsign }
                            Text(variant?.let { v -> if (other.isNullOrBlank()) v.headsign else "$other → ${v.headsign}" } ?: line.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                            val mode = stringResource(if (line.lineMode == LineMode.TRUFI) R.string.mode_trufi else R.string.mode_micro)
                            Text(if (near != null) stringResource(R.string.line_subtitle, mode, Geo.formatDistance(near.second), near.third) else "$mode · ${line.name}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 1)
                        }
                        IconCircleButton(if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, stringResource(R.string.favorite_line), tint = Color.White, onClick = { scope.launch { c.favorites.toggleLine(line.lineId) } })
                    }
                    // Branch chips (only when the line has more than one named route)
                    if (branches.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        branches.forEachIndexed { i, b ->
                            val on = i == branchIdx
                            val bColor = LineColors.colorOf(line, line.variantsOf(b).firstOrNull())
                            Box(
                                Modifier.clip(CircleShape).background(if (on) bColor else t.surface2).border(1.dp, if (on) bColor else t.line, CircleShape)
                                    .clickable { branchIdx = i }.padding(horizontal = 14.dp, vertical = 7.dp),
                            ) {
                                Text(b.ifBlank { stringResource(R.string.branch_main) }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (on) Color.White else t.ink, maxLines = 1)
                            }
                        }
                    }
                    // Direction toggle
                    Row(Modifier.fillMaxWidth().clip(CircleShape).background(t.surface2).padding(3.dp)) {
                        branchVariants.take(2).forEachIndexed { i, v ->
                            val on = i == dir
                            Box(Modifier.weight(1f).height(36.dp).clip(CircleShape).background(if (on) t.surface else Color.Transparent).clickable { dir = i }, contentAlignment = Alignment.Center) {
                                Text(stringResource(when (v.direction) { "ida" -> R.string.direction_ida; "vuelta" -> R.string.direction_vuelta; else -> if (i == 0) R.string.direction_ida else R.string.direction_vuelta }, v.headsign), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (on) t.ink else t.muted, maxLines = 1)
                            }
                        }
                    }
                    // Streets / endpoints
                    variant?.streets?.forEachIndexed { i, s ->
                        val isNear = near != null && near.third == s
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(if (isNear) t.accent else color))
                            Column {
                                Text(s, color = t.ink, fontSize = 15.sp)
                                val note = when { i == 0 -> stringResource(R.string.start_stop); i == variant.streets.lastIndex -> stringResource(R.string.end_stop); isNear -> stringResource(R.string.near_you, Geo.formatDistance(near!!.second)); else -> null }
                                if (note != null) Text(note, color = t.muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
                HorizontalDivider(color = t.line, modifier = Modifier.padding(vertical = 10.dp))
                PiyoButton(stringResource(R.string.report_error), primary = false, icon = Icons.Rounded.Flag, onClick = { val p = near?.first ?: pts.firstOrNull() ?: bo.piyo.app.map.SantaCruz.center; onReport(p.lat, p.lng) })
                Note(bundle?.attribution?.takeIf { it.isNotBlank() } ?: stringResource(R.string.osm_credit), modifier = Modifier.fillMaxWidth().padding(top = 8.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
                }
            },
        ) {
            // The map ignores the scaffold padding on purpose: it fills the screen and shows under the sheet.
            Box(Modifier.fillMaxSize()) {
                c.mapProvider.Map(Modifier.fillMaxSize(), content, onTap = null, onCameraIdle = null, drag = null)
                Row(Modifier.statusBarsPadding().padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack, filled = true)
                    IconCircleButton(Icons.Rounded.MyLocation, stringResource(R.string.center_on_me), onClick = { position?.let { camera = MapCamera(it, 14.5) } }, filled = true)
                }
            }
        }
}
