package bo.piyo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bo.piyo.app.R
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.components.GroupedList
import bo.piyo.app.ui.components.LeadingIcon
import bo.piyo.app.ui.components.ListRow
import bo.piyo.app.ui.components.Note
import bo.piyo.app.ui.components.SectionHeader
import bo.piyo.app.ui.theme.LocalPiyo
import bo.piyo.app.ui.util.Notifier
import bo.piyo.app.ui.util.appSettingsIntent
import bo.piyo.app.ui.util.rememberLocationPermission
import kotlinx.coroutines.launch

/**
 * Ajustes, reachable from the gear on every tab. Three short groups instead of a stack of cards:
 * Preferencias (what the user switches), Información (fares, about), Ayuda (feedback). Line data and its
 * update check live in the Datos tab only.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onAbout: () -> Unit) {
    val t = LocalPiyo.current
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showNps by remember { mutableStateOf(false) }
    var showFares by remember { mutableStateOf(false) }
    val darkOverride by c.settings.darkOverride.collectAsStateWithLifecycle(initialValue = "system")
    val notifications by c.settings.notifications.collectAsStateWithLifecycle(initialValue = true)
    // Permissions can change in the phone settings while piyo is in the background: re-read them on resume.
    var notifPermitted by remember { mutableStateOf(Notifier.permitted(ctx)) }
    var locationOn by remember { mutableStateOf(c.location.hasPermission()) }
    var notifDenied by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notifPermitted = Notifier.permitted(ctx); locationOn = c.location.hasPermission() }
    val askNotif = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        notifPermitted = granted
        if (granted) scope.launch { c.settings.setNotifications(true); c.push.sync() } else notifDenied = true
    }
    val askLocation = rememberLocationPermission { locationOn = true; c.location.refresh() }
    val switchColors = SwitchDefaults.colors(checkedTrackColor = t.primary, checkedThumbColor = t.onPrimary)

    Page(title = stringResource(R.string.settings), onBack = onBack) {
        SectionHeader(stringResource(R.string.settings_preferences))
        GroupedList {
            // Notifications are "on" only when the preference is on AND the system permission is granted.
            // A refused prompt sends the next tap to the system page, where the user can still allow them.
            val notifOn = notifications && notifPermitted
            ListRow(
                leading = { LeadingIcon(Icons.Rounded.Notifications, t.muted, t.surface2) },
                trailing = {
                    Switch(checked = notifOn, colors = switchColors, onCheckedChange = { on ->
                        when {
                            !on -> scope.launch { c.settings.setNotifications(false); c.push.sync() }
                            Notifier.permitted(ctx) -> scope.launch { c.settings.setNotifications(true); notifPermitted = true; c.push.sync() }
                            android.os.Build.VERSION.SDK_INT >= 33 && !notifDenied -> askNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            else -> ctx.startActivity(Notifier.settingsIntent(ctx))
                        }
                    })
                },
            ) {
                Text(stringResource(R.string.notifications_title), color = t.ink, fontSize = 16.sp)
                Text(stringResource(if (notifOn) R.string.notifications_on else R.string.notifications_off), color = t.muted, fontSize = 13.sp)
            }
            ListRow(
                onClick = { if (locationOn) ctx.startActivity(appSettingsIntent(ctx)) else askLocation(true) },
                leading = { LeadingIcon(Icons.Rounded.LocationOn, t.muted, t.surface2) },
            ) {
                Text(stringResource(R.string.location_title), color = t.ink, fontSize = 16.sp)
                Text(stringResource(if (locationOn) R.string.location_on else R.string.location_off), color = if (locationOn) t.muted else t.primary, fontSize = 13.sp)
            }
            ListRow(
                leading = { LeadingIcon(Icons.Rounded.DarkMode, t.muted, t.surface2) },
                trailing = {
                    Switch(checked = darkOverride == "on" || (darkOverride == "system" && t.dark), colors = switchColors,
                        onCheckedChange = { on -> scope.launch { c.settings.setDarkOverride(if (on) "on" else "off") } })
                },
            ) {
                Text(stringResource(R.string.dark_mode), color = t.ink, fontSize = 16.sp)
                Text(stringResource(when (darkOverride) { "on" -> R.string.dark_on; "off" -> R.string.dark_off; else -> R.string.dark_follows_system }), color = t.muted, fontSize = 13.sp)
            }
            ListRow(leading = { LeadingIcon(Icons.Rounded.Language, t.muted, t.surface2) }, trailing = { Text(stringResource(R.string.spanish), color = t.muted, fontSize = 13.sp) }, divider = false) {
                Text(stringResource(R.string.language), color = t.ink, fontSize = 16.sp)
            }
        }

        SectionHeader(stringResource(R.string.settings_info))
        GroupedList {
            ListRow(onClick = { showFares = true }, leading = { LeadingIcon(Icons.Rounded.Payments, t.muted, t.surface2) }, trailing = { Text(stringResource(R.string.fare_adult_value), color = t.muted, fontSize = 13.sp) }) {
                Text(stringResource(R.string.fares_title), color = t.ink, fontSize = 16.sp)
            }
            ListRow(onClick = onAbout, leading = { LeadingIcon(Icons.Rounded.Info, t.muted, t.surface2) }, divider = false) {
                Text(stringResource(R.string.about), color = t.ink, fontSize = 16.sp)
            }
        }

        SectionHeader(stringResource(R.string.settings_help))
        GroupedList {
            ListRow(onClick = { showNps = true }, leading = { LeadingIcon(Icons.Rounded.RateReview, t.muted, t.surface2) }, divider = false) {
                Text(stringResource(R.string.give_feedback), color = t.ink, fontSize = 16.sp)
            }
        }
        Note(stringResource(R.string.settings_offline_note))
        Note("piyo ${c.appVersion}")
    }
    if (showNps) NpsSheet(trip = null, onClose = { showNps = false })
    if (showFares) AlertDialog(
        onDismissRequest = { showFares = false },
        title = { Text(stringResource(R.string.fares_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Kv(stringResource(R.string.fare_adult), stringResource(R.string.fare_adult_value))
                Kv(stringResource(R.string.fare_university), stringResource(R.string.fare_university_value))
                Kv(stringResource(R.string.fare_elderly), stringResource(R.string.fare_elderly_value))
                Kv(stringResource(R.string.fare_school), stringResource(R.string.fare_school_value))
                Text(stringResource(R.string.fares_note), color = t.muted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { showFares = false }) { Text(stringResource(R.string.close)) } },
        containerColor = t.surface, titleContentColor = t.ink, textContentColor = t.muted,
    )
}
