package bo.piyo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.rounded.EditRoad
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bo.piyo.app.R
import bo.piyo.app.domain.LineColors
import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.ReportDraft
import bo.piyo.app.domain.model.ReportQuota
import bo.piyo.app.domain.model.ReportResult
import bo.piyo.app.domain.model.ReportType
import bo.piyo.app.map.MapCamera
import bo.piyo.app.map.MapContent
import bo.piyo.app.map.MapLine
import bo.piyo.app.map.MapPoint
import bo.piyo.app.map.SantaCruz
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.components.LineChip
import bo.piyo.app.ui.components.Note
import bo.piyo.app.ui.components.PillButton
import bo.piyo.app.ui.components.PiyoButton
import bo.piyo.app.ui.components.PiyoCard
import bo.piyo.app.ui.components.SectionHeader
import bo.piyo.app.ui.theme.LocalPiyo
import bo.piyo.app.ui.util.formatDayMonth
import kotlinx.coroutines.launch

/**
 * 07 Reportar línea: type chips, map with a movable pin, optional comment, quota, anonymous send. A wrong or
 * missing route is not reported here: the card on top opens the route editor, so the correction arrives as a
 * contribution with the actual geometry ([onEditRoute] with the line and variant, or nulls for a new route).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(lineId: String?, lat: Double?, lng: Double?, initialType: String?, onBack: () -> Unit, onEditRoute: (lineId: String?, variantId: String?) -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val bundle by c.lines.bundle.collectAsStateWithLifecycle()
    val position by c.location.position.collectAsStateWithLifecycle()
    val line = lineId?.let { id -> bundle?.lines?.firstOrNull { it.lineId == id } }
    var type by remember { mutableStateOf(ReportType.entries.firstOrNull { it.wire == initialType } ?: if (line == null) ReportType.MISSING else ReportType.STOP) }
    var pin by remember { mutableStateOf(if (lat != null && lng != null) LatLng(lat, lng) else position ?: SantaCruz.center) }
    var comment by remember { mutableStateOf("") }
    var quota by remember { mutableStateOf<ReportQuota?>(null) }
    var result by remember { mutableStateOf<ReportResult?>(null) }
    var sending by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { quota = c.reports.quota() }

    val mapTitle = stringResource(when (type) {
        ReportType.STOP -> R.string.map_title_stop; ReportType.GONE -> R.string.map_title_gone
        ReportType.MISSING -> R.string.map_title_missing; ReportType.OTHER -> R.string.map_title_other
    })
    val content = remember(pin, line, t.dark) {
        val lines = line?.variants?.firstOrNull()?.let { v -> listOf(MapLine("line", v.points, LineColors.colorOf(line))) } ?: emptyList()
        MapContent(MapCamera(pin, 14.5), lines, listOf(MapPoint("pin", pin, t.accent, MapPoint.Kind.PIN)), t.dark)
    }
    val blocked = quota?.blockedUntilIso != null || result is ReportResult.Blocked
    val remaining = (result as? ReportResult.Sent)?.remaining ?: quota?.remaining ?: 3

    Page(onBack = onBack, titleContent = {
        Row(Modifier.weight(1f).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.report_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = t.ink)
            if (line != null) LineChip(line)
        }
    }) {
        // Route problems go to the editor: correct this line, or draw the one that is missing.
        val fixLine = line != null && type != ReportType.MISSING
        PiyoCard(onClick = { if (fixLine) onEditRoute(line?.lineId, line?.variants?.firstOrNull()?.variantId) else onEditRoute(null, null) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.EditRoad, null, tint = t.primary, modifier = Modifier.size(22.dp))
                Text(stringResource(if (fixLine) R.string.report_contribute_title else R.string.report_missing_title), color = t.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Text(stringResource(if (fixLine) R.string.report_contribute_body else R.string.report_missing_body), color = t.muted, fontSize = 13.sp, lineHeight = 18.sp)
            Text(stringResource(if (fixLine) R.string.report_contribute_cta else R.string.report_missing_cta), color = t.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        SectionHeader(stringResource(R.string.whats_wrong))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ReportType.STOP to R.string.type_stop, ReportType.GONE to R.string.type_gone, ReportType.MISSING to R.string.type_missing, ReportType.OTHER to R.string.type_other)
                .forEach { (ty, label) -> PillButton(stringResource(label), selected = type == ty, onClick = { type = ty }) }
        }
        SectionHeader(mapTitle)
        Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp))) {
            c.mapProvider.Map(Modifier.fillMaxWidth().height(200.dp), content, onTap = { pin = it }, onCameraIdle = null, drag = null)
            Box(Modifier.align(Alignment.BottomCenter).padding(8.dp).clip(CircleShape).background(t.surface).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.tap_map_hint), fontSize = 11.sp, color = t.muted)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.tell_more), fontSize = 13.sp, color = t.muted)
            Box(Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(14.dp)).background(t.surface).border(1.dp, t.line, RoundedCornerShape(14.dp)).padding(12.dp)) {
                if (comment.isEmpty()) Text(stringResource(R.string.report_placeholder), color = t.muted, fontSize = 14.sp)
                BasicTextField(comment, { comment = it.take(500) }, textStyle = TextStyle(color = t.ink, fontSize = 15.sp), cursorBrush = SolidColor(t.primary), modifier = Modifier.fillMaxWidth())
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(quota?.limit ?: 3) { i -> Box(Modifier.size(8.dp).clip(CircleShape).background(if (i < (quota?.limit ?: 3) - remaining) t.primary else t.line)) }
            }
            Text(stringResource(R.string.quota_left, remaining, quota?.limit ?: 3), fontSize = 13.sp, color = t.muted)
        }
        when (val r = result) {
            is ReportResult.Sent -> Text(stringResource(R.string.report_sent, r.id), color = t.primary, fontWeight = FontWeight.Medium)
            ReportResult.Queued -> Text(stringResource(R.string.report_queued), color = t.primary, fontWeight = FontWeight.Medium)
            is ReportResult.Blocked -> Text(stringResource(R.string.report_blocked, formatDayMonth(r.untilIso)), color = t.danger, fontWeight = FontWeight.Medium)
            ReportResult.QuotaExceeded -> Text(stringResource(R.string.report_quota_exceeded), color = t.danger, fontWeight = FontWeight.Medium)
            is ReportResult.Failed -> Text(r.message, color = t.danger)
            null -> Unit
        }
        if (blocked && result == null) Text(stringResource(R.string.report_blocked, formatDayMonth(quota?.blockedUntilIso)), color = t.danger, fontWeight = FontWeight.Medium)
        PiyoButton(
            stringResource(R.string.send_report), enabled = !sending && !blocked && remaining > 0 && result !is ReportResult.Sent,
            onClick = {
                scope.launch {
                    sending = true
                    result = c.reports.send(ReportDraft(type, line?.lineId, pin, comment.trim()))
                    sending = false
                }
            },
        )
        Note(stringResource(R.string.report_note))
    }
}
