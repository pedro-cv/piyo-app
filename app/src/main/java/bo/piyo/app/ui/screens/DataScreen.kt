package bo.piyo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bo.piyo.app.R
import bo.piyo.app.data.feedback.ManifestRepository
import bo.piyo.app.domain.model.DataStatus
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.components.GroupedList
import bo.piyo.app.ui.components.LeadingIcon
import bo.piyo.app.ui.components.ListRow
import bo.piyo.app.ui.components.Note
import bo.piyo.app.ui.components.OfflineBanner
import bo.piyo.app.ui.components.PiyoButton
import bo.piyo.app.ui.components.PiyoCard
import bo.piyo.app.ui.theme.LocalPiyo
import bo.piyo.app.ui.util.formatDateWithAge
import bo.piyo.app.ui.util.isOnline
import bo.piyo.app.ui.util.Notifier
import kotlinx.coroutines.launch

/** 06 Datos y conexión: data cards, update check, language, dark mode, notifications, feedback, about. */
@Composable
fun DataScreen(onBack: () -> Unit, onAbout: () -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val online = isOnline()
    var status by remember { mutableStateOf<DataStatus?>(null) }
    var showNps by remember { mutableStateOf(false) }
    val darkOverride by c.settings.darkOverride.collectAsStateWithLifecycle(initialValue = "system")
    val notifications by c.settings.notifications.collectAsStateWithLifecycle(initialValue = true)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var permitted by remember { mutableStateOf(Notifier.permitted(ctx)) }
    var deniedOnce by remember { mutableStateOf(false) }
    val askNotif = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        if (granted) scope.launch { c.settings.setNotifications(true) } else deniedOnce = true
    }
    LaunchedEffect(Unit) { status = c.lines.status() }

    Page(title = stringResource(R.string.data_title), onBack = onBack) {
        if (!online) OfflineBanner(stringResource(R.string.offline_banner_short))
        OfficialDataCard(status, reload = { c.lines.status() }, onStatus = { status = it })
        // The map is streamed from our server and MapLibre keeps the tiles it already fetched (ambient
        // cache), so areas you visited keep working offline; there is no whole-city download.
        PiyoCard {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(stringResource(R.string.data_city_map), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = t.ink, modifier = Modifier.weight(1f))
            }
            Kv(stringResource(R.string.data_coverage), stringResource(R.string.data_metro))
            Kv(stringResource(R.string.data_map_offline), stringResource(R.string.data_map_offline_value))
            Kv(stringResource(R.string.data_search), stringResource(R.string.data_search_value))
        }
        // Fares in one place. Trips show the adult fare per micro (fare_bs of the dataset).
        PiyoCard {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(stringResource(R.string.fares_title), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = t.ink, modifier = Modifier.weight(1f))
            }
            Kv(stringResource(R.string.fare_adult), stringResource(R.string.fare_adult_value))
            Kv(stringResource(R.string.fare_university), stringResource(R.string.fare_university_value))
            Kv(stringResource(R.string.fare_elderly), stringResource(R.string.fare_elderly_value))
            Kv(stringResource(R.string.fare_school), stringResource(R.string.fare_school_value))
            Text(stringResource(R.string.fares_note), color = t.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        GroupedList {
            ListRow(leading = { LeadingIcon(Icons.Rounded.Language, t.muted, t.surface2) }, trailing = { Text(stringResource(R.string.spanish), color = t.muted, fontSize = 13.sp) }) {
                Text(stringResource(R.string.language), color = t.ink, fontSize = 16.sp)
            }
            ListRow(
                leading = { LeadingIcon(Icons.Rounded.DarkMode, t.muted, t.surface2) },
                trailing = {
                    Switch(checked = darkOverride == "on" || (darkOverride == "system" && t.dark), onCheckedChange = { on -> scope.launch { c.settings.setDarkOverride(if (on) "on" else "off") } },
                        colors = SwitchDefaults.colors(checkedTrackColor = t.primary, checkedThumbColor = t.onPrimary))
                },
            ) {
                Text(stringResource(R.string.dark_mode), color = t.ink, fontSize = 16.sp)
                Text(stringResource(when (darkOverride) { "on" -> R.string.dark_on; "off" -> R.string.dark_off; else -> R.string.dark_follows_system }), color = t.muted, fontSize = 13.sp)
            }
            // Notifications: the switch is only "on" when the preference is on AND the system permission is granted.
            // Turning it on asks for POST_NOTIFICATIONS (Android 13+); a refusal leaves it off and offers the settings page.
            val notifOn = notifications && permitted
            ListRow(
                leading = { LeadingIcon(Icons.Rounded.Notifications, t.muted, t.surface2) },
                trailing = {
                    Switch(
                        checked = notifOn,
                        onCheckedChange = { on ->
                            if (!on) scope.launch { c.settings.setNotifications(false) }
                            else if (Notifier.permitted(ctx)) scope.launch { c.settings.setNotifications(true); permitted = true }
                            else if (android.os.Build.VERSION.SDK_INT >= 33 && !deniedOnce) askNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            else { deniedOnce = true }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = t.primary, checkedThumbColor = t.onPrimary),
                    )
                },
            ) {
                Text(stringResource(R.string.notifications), color = t.ink, fontSize = 16.sp)
                Text(stringResource(if (notifOn) R.string.notifications_on else R.string.notifications_off), color = t.muted, fontSize = 13.sp)
            }
            if (deniedOnce && !permitted) ListRow(onClick = { ctx.startActivity(Notifier.settingsIntent(ctx)) }, leading = { LeadingIcon(Icons.Rounded.Notifications, t.danger, t.surface2) }) {
                Text(stringResource(R.string.notifications_denied), color = t.muted, fontSize = 13.sp)
                Text(stringResource(R.string.open_settings), color = t.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            ListRow(onClick = { showNps = true }, leading = { LeadingIcon(Icons.Rounded.RateReview, t.muted, t.surface2) }) {
                Text(stringResource(R.string.give_feedback), color = t.ink, fontSize = 16.sp)
            }
            ListRow(onClick = onAbout, leading = { LeadingIcon(Icons.Rounded.Info, t.muted, t.surface2) }, divider = false) {
                Text(stringResource(R.string.about), color = t.ink, fontSize = 16.sp)
                Text(status?.license?.takeIf { it.isNotBlank() } ?: stringResource(R.string.osm_credit_long), color = t.muted, fontSize = 13.sp)
            }
        }
        Note("Piyo ${c.appVersion}")
    }
    if (showNps) NpsSheet(trip = null, onClose = { showNps = false })
}
