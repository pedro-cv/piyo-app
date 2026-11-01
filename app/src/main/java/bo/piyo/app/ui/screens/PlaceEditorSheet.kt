package bo.piyo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bo.piyo.app.R
import bo.piyo.app.domain.model.FavoritePlace
import bo.piyo.app.ui.components.FavoriteIcons
import bo.piyo.app.ui.components.PiyoButton
import bo.piyo.app.ui.components.SectionHeader
import bo.piyo.app.ui.theme.LocalPiyo
import java.util.UUID

/**
 * Add / edit a saved place: name, icon, colour and address (CONTRIBUTIONS_DESIGN.md §4.4).
 * [onPickAddress] receives the place as saved so far and opens the search; [onDelete] is null for Casa/Trabajo.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaceEditorSheet(existing: FavoritePlace?, onDismiss: () -> Unit, onSave: (FavoritePlace) -> Unit, onPickAddress: (FavoritePlace) -> Unit, onDelete: ((FavoritePlace) -> Unit)?) {
    val t = LocalPiyo.current
    val id = remember(existing) { existing?.id ?: ("f_" + UUID.randomUUID().toString().take(8)) }
    var label by remember(existing) { mutableStateOf(existing?.label ?: "") }
    var icon by remember(existing) { mutableStateOf(existing?.icon ?: "place") }
    var color by remember(existing) { mutableStateOf(existing?.color ?: FavoriteIcons.palette[0]) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun current() = FavoritePlace(id, label.trim().ifBlank { stringLabel(icon) }, icon, color, existing?.place)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.surface, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(FavoriteIcons.color(color)), contentAlignment = Alignment.Center) {
                    Icon(FavoriteIcons.icon(icon), null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Text(stringResource(if (existing == null) R.string.place_title_new else R.string.place_title_edit), style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = t.ink)
            }
            SectionHeader(stringResource(R.string.place_name))
            Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(t.surface2).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (label.isEmpty()) Text(stringResource(R.string.place_name_hint), color = t.muted, fontSize = 15.sp)
                BasicTextField(label, { label = it.take(40) }, singleLine = true, textStyle = TextStyle(color = t.ink, fontSize = 15.sp), cursorBrush = SolidColor(t.primary), modifier = Modifier.fillMaxWidth())
            }
            SectionHeader(stringResource(R.string.place_icon))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FavoriteIcons.all.forEach { e ->
                    val on = e.key == icon
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) t.primarySoft else t.surface2).clickable { icon = e.key; if (label.isBlank() || FavoriteIcons.all.any { stringLabel(it.key) == label }) label = "" }.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(e.icon, null, tint = if (on) t.primary else t.muted, modifier = Modifier.size(22.dp))
                        Text(stringResource(e.label), color = if (on) t.ink else t.muted, fontSize = 11.sp)
                    }
                }
            }
            SectionHeader(stringResource(R.string.place_color))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FavoriteIcons.palette.forEach { hex ->
                    val on = hex == color
                    Box(Modifier.size(32.dp).clip(CircleShape).background(FavoriteIcons.color(hex)).border(if (on) 3.dp else 0.dp, if (on) t.ink else Color.Transparent, CircleShape).clickable { color = hex }, contentAlignment = Alignment.Center) {
                        if (on) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            SectionHeader(stringResource(R.string.place_address))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.surface2).clickable { onPickAddress(current()) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Place, null, tint = t.accent, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(existing?.place?.name ?: stringResource(R.string.choose_address), color = if (existing?.place != null) t.ink else t.primary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    if (existing?.place != null) Text(stringResource(R.string.place_change_address), color = t.muted, fontSize = 12.sp)
                }
            }
            PiyoButton(stringResource(R.string.place_save), onClick = { onSave(current()) })
            if (onDelete != null && existing != null) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.place_delete), color = t.danger) }
        }
    }
    if (confirmDelete && existing != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        text = { Text(stringResource(R.string.place_delete_confirm, existing.label)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete?.invoke(existing) }) { Text(stringResource(R.string.remove), color = t.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        containerColor = t.surface, textContentColor = t.ink,
    )
}

/** Spanish default label for an icon key (used when the user leaves the name empty). */
private fun stringLabel(icon: String): String = when (icon) {
    "home" -> "Casa"; "work" -> "Trabajo"; "school" -> "Estudio"; "gym" -> "Gimnasio"; "health" -> "Salud"
    "shopping" -> "Compras"; "food" -> "Comida"; "family" -> "Familia"; "church" -> "Iglesia"; "park" -> "Parque"; else -> "Lugar"
}
