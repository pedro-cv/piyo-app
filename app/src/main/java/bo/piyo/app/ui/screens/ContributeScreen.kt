package bo.piyo.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bo.piyo.app.R
import bo.piyo.app.data.lines.DatasetWriter
import bo.piyo.app.domain.model.ContributionQuota
import bo.piyo.app.domain.model.DataStatus
import bo.piyo.app.domain.model.ContributionResult
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.LocalProvider
import bo.piyo.app.domain.model.ProviderMeta
import bo.piyo.app.domain.model.RouteEdit
import bo.piyo.app.domain.model.Variant
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.components.GroupedList
import bo.piyo.app.ui.components.IconCircleButton
import bo.piyo.app.ui.components.LineChip
import bo.piyo.app.ui.components.ListRow
import bo.piyo.app.ui.components.Note
import bo.piyo.app.ui.components.PillButton
import bo.piyo.app.ui.components.PiyoButton
import bo.piyo.app.ui.components.SectionHeader
import bo.piyo.app.ui.theme.LocalPiyo
import bo.piyo.app.ui.util.formatDayMonth
import bo.piyo.app.ui.util.formatShortDate
import kotlinx.coroutines.launch

/**
 * Datos tab (CONTRIBUTIONS_DESIGN.md §4.1, renamed from Aportar): official data + update check, the data in
 * use (official or a local provider), the routes of the active provider with their review status, add or
 * correct a route, swap ida/vuelta, send contributions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributeScreen(onEditRoute: (lineId: String?, variantId: String?) -> Unit, onSettings: () -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val providers by c.providers.providers.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeId by c.providers.activeId.collectAsStateWithLifecycle(initialValue = null)
    val active by c.providers.active.collectAsStateWithLifecycle()
    val bundle by c.lines.bundle.collectAsStateWithLifecycle()
    var official by remember { mutableStateOf<Triple<Int, Int, String>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<ProviderMeta?>(null) }
    var deleting by remember { mutableStateOf<ProviderMeta?>(null) }
    var pickLine by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var routeMenu by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<DataStatus?>(null) }
    var swapping by remember { mutableStateOf<Line?>(null) }
    val importFailed = stringResource(R.string.provider_import_failed)

    LaunchedEffect(Unit) {
        status = c.officialLines.status()
        val b = c.officialLines.load()
        official = Triple(b.dataVersion, b.lines.size, b.attribution.ifBlank { b.source })
        // Pull review results for anything this phone sent.
        val mine = c.contributions.mine()
        c.providers.applyStatuses(mine.associate { it.id to (it.status to it.note) })
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            val meta = text?.let { runCatching { c.providers.import(it) }.getOrNull() }
            message = if (meta != null) ctx.getString(R.string.provider_imported, meta.name, meta.routeCount) else importFailed
            if (meta != null) c.providers.activate(meta.id)
        }
    }

    /**
     * The loaded data sometimes has ida and vuelta the other way round: swap the direction labels of every
     * variant (geometry, headsigns and ids stay with their route) and save the line into the provider.
     */
    suspend fun swapDirections(line: Line, providerId: String) {
        val official = c.officialLines.load()
        // Every trace of the line is reversed point by point (Variant.reversed): the ida keeps its streets
        // but is travelled from its old end to its old start, and so is the vuelta. Labels and ids stay.
        val swapped = line.copy(variants = line.variants.map { it.reversed() })
        val officialLine = official.lines.firstOrNull { it.lineId == line.lineId }
        val before = officialLine?.variants?.associate { it.variantId to DatasetWriter.encodePolyline(it.points) } ?: emptyMap()
        c.providers.saveLine(providerId, swapped, if (officialLine != null) "update" else "add", before, official.dataVersion, official.lines)
        message = ctx.getString(R.string.swap_done, line.display)
    }
    fun requestSwap(line: Line) {
        val p = active
        if (p == null) swapping = line else scope.launch { swapDirections(line, p.meta.id) }
    }

    fun export(meta: ProviderMeta) = scope.launch {
        val f = c.providers.exportFile(meta.id) ?: return@launch
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, "piyo · " + meta.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, meta.name))
    }

    // The top-right slot is the same gear as on every tab; importing a routes file is a labelled action below.
    Page(title = stringResource(R.string.contribute_title), actions = { SettingsAction(onSettings) }) {
        Note(stringResource(R.string.contribute_intro))
        message?.let { Text(it, color = t.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium) }

        // 0. Official data + sync with the backend
        SectionHeader(stringResource(R.string.official_data))
        OfficialDataCard(status, reload = { c.officialLines.status() }, onStatus = { status = it })

        // 1. Data in use
        SectionHeader(stringResource(R.string.data_provider_in_use))
        GroupedList {
            ProviderRow(
                selected = activeId == null, title = stringResource(R.string.official_data),
                subtitle = official?.let { stringResource(R.string.official_data_meta, it.first, it.second, it.third) } ?: "…",
                onClick = { scope.launch { c.providers.activate(null) } }, divider = providers.isNotEmpty(),
            )
            providers.forEachIndexed { i, p ->
                ProviderRow(
                    selected = activeId == p.id, title = p.name,
                    subtitle = when (p.routeCount) { 0 -> stringResource(R.string.provider_meta_empty); 1 -> stringResource(R.string.provider_meta_one, formatShortDate(p.updatedAt)); else -> stringResource(R.string.provider_meta, p.routeCount, formatShortDate(p.updatedAt)) },
                    onClick = { scope.launch { c.providers.activate(p.id) } }, divider = i < providers.lastIndex,
                    menu = {
                        Box {
                            IconCircleButton(Icons.Rounded.MoreVert, stringResource(R.string.more_options), tint = t.muted, onClick = { menuFor = p.id })
                            DropdownMenu(expanded = menuFor == p.id, onDismissRequest = { menuFor = null }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menuFor = null; renaming = p })
                                DropdownMenuItem(text = { Text(stringResource(R.string.export)) }, onClick = { menuFor = null; export(p) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = t.danger) }, onClick = { menuFor = null; deleting = p })
                            }
                        }
                    },
                )
            }
        }

        // 2. Routes of the active provider
        val prov = active
        if (prov != null) {
            SectionHeader(stringResource(R.string.my_routes, prov.meta.name))
            if (prov.lines.isEmpty()) Note(stringResource(R.string.my_routes_empty))
            else GroupedList {
                prov.lines.forEachIndexed { i, line ->
                    val edit = prov.edit(line.lineId)
                    ListRow(
                        onClick = { onEditRoute(line.lineId, line.primaryVariant?.variantId) }, divider = i < prov.lines.lastIndex,
                        leading = { LineChip(line) },
                        trailing = {
                            Box {
                                IconCircleButton(Icons.Rounded.MoreVert, stringResource(R.string.more_options), tint = t.muted, onClick = { routeMenu = line.lineId })
                                DropdownMenu(expanded = routeMenu == line.lineId, onDismissRequest = { routeMenu = null }) {
                                    line.variants.forEach { v ->
                                        DropdownMenuItem(text = { Text(stringResource(R.string.edit) + " · " + directionLabel(v)) }, onClick = { routeMenu = null; onEditRoute(line.lineId, v.variantId) })
                                    }
                                    DropdownMenuItem(text = { Text(stringResource(R.string.swap_directions)) }, onClick = { routeMenu = null; requestSwap(line) })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.remove), color = t.danger) }, onClick = {
                                        routeMenu = null
                                        scope.launch { c.providers.deleteLine(prov.meta.id, line.lineId, c.officialLines.load().lines) }
                                    })
                                }
                            }
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(line.name.ifBlank { stringResource(R.string.line_n, line.display) }, color = t.ink, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                            Badge(stringResource(if (edit?.op == "update") R.string.route_updated else R.string.route_added), t.primarySoft, t.primary)
                        }
                        StatusLine(edit)
                    }
                }
            }
        }

        // 3. Actions
        SectionHeader(stringResource(R.string.add_or_fix))
        PiyoButton(stringResource(R.string.add_route), icon = Icons.Rounded.Add, onClick = { onEditRoute(null, null) })
        PiyoButton(stringResource(R.string.fix_route), primary = false, icon = Icons.Rounded.Edit, onClick = { pickLine = true })
        PiyoButton(stringResource(R.string.provider_import), primary = false, icon = Icons.Rounded.UploadFile, onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) })
        val sendable = prov?.lines?.filter { l -> prov.edit(l.lineId)?.sent.let { it == null || it.status == "rejected" } } ?: emptyList()
        if (sendable.isNotEmpty()) PiyoButton(stringResource(R.string.send_contributions, sendable.size), primary = false, icon = Icons.Rounded.Send, onClick = { sending = true })
        Note(stringResource(R.string.contribute_note))
    }

    // --- dialogs & sheets ------------------------------------------------------------------------
    renaming?.let { p ->
        NameDialog(title = stringResource(R.string.provider_rename_title), initial = p.name, onDismiss = { renaming = null }) { name ->
            scope.launch { c.providers.rename(p.id, name) }; renaming = null
        }
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.provider_delete_title, p.name)) },
            text = { Text(stringResource(R.string.provider_delete_body, p.routeCount)) },
            confirmButton = { TextButton(onClick = { scope.launch { c.providers.remove(p.id) }; deleting = null }) { Text(stringResource(R.string.delete), color = t.danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
            containerColor = t.surface, titleContentColor = t.ink, textContentColor = t.muted,
        )
    }
    if (pickLine) LinePickerDialog(lines = bundle?.lines ?: emptyList(), onDismiss = { pickLine = false }, onSwap = { line -> pickLine = false; requestSwap(line) }) { line, v -> pickLine = false; onEditRoute(line.lineId, v.variantId) }
    swapping?.let { line ->
        NameDialog(title = stringResource(R.string.provider_new_title), initial = "Mis rutas", body = stringResource(R.string.provider_new_body), onDismiss = { swapping = null }) { n ->
            swapping = null
            scope.launch {
                val meta = c.providers.create(n, c.officialLines.load().dataVersion)
                c.providers.activate(meta.id)
                swapDirections(line, meta.id)
            }
        }
    }
    active?.let { p -> if (sending) SendSheet(p, onDismiss = { sending = false }) }
}

@Composable
private fun ProviderRow(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit, divider: Boolean, menu: (@Composable () -> Unit)? = null) {
    val t = LocalPiyo.current
    ListRow(
        onClick = onClick, divider = divider,
        leading = {
            Box(Modifier.size(24.dp).clip(CircleShape).border(2.dp, if (selected) t.primary else t.line, CircleShape), contentAlignment = Alignment.Center) {
                if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(t.primary))
            }
        },
        trailing = { menu?.invoke() },
    ) {
        Text(title, color = t.ink, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        Text(subtitle, color = t.muted, fontSize = 13.sp)
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun StatusLine(edit: RouteEdit?) {
    val t = LocalPiyo.current
    val sent = edit?.sent
    val (text, color, icon) = when (sent?.status) {
        null -> Triple(stringResource(R.string.route_unsent), t.muted, null)
        "accepted" -> Triple(stringResource(R.string.route_accepted), t.primary, Icons.Rounded.Verified)
        "rejected" -> Triple(stringResource(R.string.route_rejected) + (sent.note.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), t.danger, null)
        else -> Triple(stringResource(R.string.route_sent), t.accent, Icons.Rounded.CheckCircle)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, color = color, fontSize = 13.sp, maxLines = 2)
    }
}

@Composable
fun directionLabel(v: Variant): String = when (v.direction) {
    "vuelta" -> stringResource(R.string.direction_vuelta_short)
    "ida" -> stringResource(R.string.direction_ida_short)
    else -> v.direction
} + (v.branch.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") + (v.headsign.takeIf { it.isNotBlank() }?.let { " → $it" } ?: "")

/** One-field dialog used for provider names. */
@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, body: String? = null, onConfirm: (String) -> Unit) {
    val t = LocalPiyo.current
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (body != null) Text(body, color = t.muted, fontSize = 14.sp)
                Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(t.surface2).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(stringResource(R.string.provider_name_hint), color = t.muted, fontSize = 15.sp)
                    BasicTextField(value, { value = it.take(60) }, singleLine = true, textStyle = TextStyle(color = t.ink, fontSize = 15.sp), cursorBrush = SolidColor(t.primary), modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim()) }) { Text(stringResource(R.string.save), color = t.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = t.surface, titleContentColor = t.ink, textContentColor = t.muted,
    )
}

/** Search a line, then pick the direction to correct. */
@Composable
private fun LinePickerDialog(lines: List<Line>, onDismiss: () -> Unit, onSwap: (Line) -> Unit, onPick: (Line, Variant) -> Unit) {
    val t = LocalPiyo.current
    var q by remember { mutableStateOf("") }
    var line by remember { mutableStateOf<Line?>(null) }
    val filtered = remember(lines, q) {
        val s = q.trim().lowercase()
        lines.filter { s.isEmpty() || it.display.lowercase().startsWith(s) || it.name.lowercase().contains(s) }
            .sortedWith(compareBy({ it.display.toIntOrNull() ?: Int.MAX_VALUE }, { it.display })).take(60)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (line == null) R.string.pick_line_title else R.string.pick_variant_title)) },
        text = {
            val l = line
            if (l == null) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(t.surface2).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Search, null, tint = t.muted, modifier = Modifier.size(18.dp))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Text(stringResource(R.string.lines_search_hint), color = t.muted, fontSize = 15.sp)
                        BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = t.ink, fontSize = 15.sp), cursorBrush = SolidColor(t.primary), modifier = Modifier.fillMaxWidth())
                    }
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.lineId }) { ln ->
                        Row(Modifier.fillMaxWidth().clickable { line = ln }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LineChip(ln, small = true)
                            Text(ln.name, color = t.ink, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                            if (ln.local != null) Badge(stringResource(if (ln.local == bo.piyo.app.domain.model.LocalEdit.ADDED) R.string.local_badge_added else R.string.local_badge_updated), t.primarySoft, t.primary)
                        }
                    }
                }
            } else Column {
                l.variants.forEach { v ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(l, v) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Check, null, tint = t.muted, modifier = Modifier.size(18.dp))
                        Text(directionLabel(v), color = t.ink, fontSize = 15.sp, maxLines = 2)
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { onSwap(l) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.SwapVert, null, tint = t.primary, modifier = Modifier.size(18.dp))
                    Column {
                        Text(stringResource(R.string.swap_directions), color = t.primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.swap_directions_sub), color = t.muted, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { if (line != null) line = null else onDismiss() }) { Text(stringResource(if (line != null) R.string.back else R.string.cancel)) } },
        containerColor = t.surface, titleContentColor = t.ink, textContentColor = t.muted,
    )
}

/** Pick routes, add a comment, see the monthly quota, send. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendSheet(prov: LocalProvider, onDismiss: () -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val candidates = remember(prov) { prov.lines.filter { l -> prov.edit(l.lineId)?.sent.let { it == null || it.status == "rejected" } } }
    var picked by remember { mutableStateOf(candidates.map { it.lineId }.toSet()) }
    var comment by remember { mutableStateOf("") }
    var quota by remember { mutableStateOf<ContributionQuota?>(null) }
    var result by remember { mutableStateOf<ContributionResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { quota = c.contributions.quota() }
    val q = quota
    val blocked = q?.blockedUntilIso != null || result is ContributionResult.Blocked
    val remaining = (result as? ContributionResult.Sent)?.remaining ?: q?.remaining ?: 0
    val maxRoutes = q?.maxRoutes ?: 10

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.surface, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.send_contributions_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = t.ink)
            Text(stringResource(R.string.send_contributions_body), color = t.muted, fontSize = 14.sp)
            GroupedList {
                candidates.forEachIndexed { i, l ->
                    val on = l.lineId in picked
                    ListRow(onClick = { picked = if (on) picked - l.lineId else if (picked.size < maxRoutes) picked + l.lineId else picked }, divider = i < candidates.lastIndex,
                        leading = { Checkbox(checked = on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = t.primary)) },
                        trailing = { LineChip(l, small = true) }) {
                        Text(l.name.ifBlank { stringResource(R.string.line_n, l.display) }, color = t.ink, fontSize = 15.sp, maxLines = 1)
                        Text(stringResource(if (prov.edit(l.lineId)?.op == "update") R.string.route_updated else R.string.route_added), color = t.muted, fontSize = 12.sp)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(t.surface2).padding(12.dp)) {
                if (comment.isEmpty()) Text(stringResource(R.string.contrib_comment_hint), color = t.muted, fontSize = 14.sp)
                BasicTextField(comment, { comment = it.take(500) }, textStyle = TextStyle(color = t.ink, fontSize = 15.sp), cursorBrush = SolidColor(t.primary), modifier = Modifier.fillMaxWidth())
            }
            if (q != null) Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(q.limit) { i -> Box(Modifier.size(8.dp).clip(CircleShape).background(if (i < q.limit - remaining) t.primary else t.line)) } }
                    Text(stringResource(R.string.contrib_quota_left, remaining, q.limit), fontSize = 13.sp, color = t.muted)
                }
                q.resetsAtIso?.let { Text(stringResource(R.string.contrib_quota_reset, formatDayMonth(it)), fontSize = 12.sp, color = t.muted) }
            }
            when (val r = result) {
                is ContributionResult.Sent -> Text(stringResource(R.string.contrib_sent, r.id), color = t.primary, fontWeight = FontWeight.Medium)
                is ContributionResult.Blocked -> Text(stringResource(R.string.contrib_blocked, formatDayMonth(r.untilIso)), color = t.danger, fontWeight = FontWeight.Medium)
                ContributionResult.QuotaExceeded -> Text(stringResource(R.string.contrib_quota_exceeded), color = t.danger, fontWeight = FontWeight.Medium)
                is ContributionResult.Invalid -> Text(stringResource(R.string.contrib_invalid, r.message), color = t.danger)
                is ContributionResult.Failed -> Text(stringResource(R.string.contrib_failed), color = t.danger)
                null -> if (blocked) Text(stringResource(R.string.contrib_blocked, formatDayMonth(q?.blockedUntilIso)), color = t.danger, fontWeight = FontWeight.Medium)
            }
            PiyoButton(
                stringResource(R.string.send), icon = Icons.Rounded.Send,
                enabled = !busy && picked.isNotEmpty() && !blocked && remaining > 0 && result !is ContributionResult.Sent,
                onClick = {
                    scope.launch {
                        busy = true
                        val r = c.contributions.send(prov, picked, comment.trim())
                        if (r is ContributionResult.Sent) c.providers.markSent(prov.meta.id, picked, r.id)
                        result = r; busy = false
                    }
                },
            )
            PillButton(stringResource(R.string.close), onClick = onDismiss)
        }
    }
}
