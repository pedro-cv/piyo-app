package bo.piyo.app.ui.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bo.piyo.app.R
import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.theme.LocalPiyo
import kotlinx.coroutines.launch

private val LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * Location permission flow shared by every screen. The returned function:
 * - with permission: runs [onGranted] at once;
 * - without: first explains what location is used for ([LocationInfoDialog]), then, if the user agrees, shows
 *   the system dialog; granting runs [onGranted], "Ahora no" or a refusal runs [onDenied];
 * - when the user asked ([fromUser] = true) and Android will no longer show the dialog (denied twice /
 *   "don't ask again"), opens piyo's page in system settings so the permission can still be granted later.
 * [upgradeToPrecise] (Ajustes): with approximate location only, asks again so Android offers precise.
 * The automatic first-launch request passes fromUser = false and never opens settings.
 */
@Composable
fun rememberLocationPermission(onDenied: () -> Unit = {}, upgradeToPrecise: Boolean = false, onGranted: () -> Unit): (fromUser: Boolean) -> Unit {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val granted by rememberUpdatedState(onGranted)
    val denied by rememberUpdatedState(onDenied)
    var fromUser by remember { mutableStateOf(false) }
    var explaining by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) granted()
        else {
            if (fromUser && ctx.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) == false) {
                ctx.startActivity(appSettingsIntent(ctx))
            }
            denied()
        }
    }
    if (explaining) LocationInfoDialog(
        onAllow = { explaining = false; launcher.launch(LOCATION) },
        onLater = { explaining = false; denied() },
    )
    return remember(launcher) {
        { user: Boolean ->
            fromUser = user
            when {
                c.location.hasPermission() && upgradeToPrecise && !c.location.hasPrecisePermission() -> launcher.launch(LOCATION)
                c.location.hasPermission() -> granted()
                else -> explaining = true
            }
        }
    }
}

/**
 * What piyo does with the location, shown before the system prompt and from Ajustes. Every line describes
 * the code as it is (PRIVACY.md §1): position in memory only, read only while the app is on screen, never
 * sent to the piyo API except as the pin of a report the user sends, and rounded to ~1 km as the search bias
 * sent to the address search. [onAllow] null = information only (permission already granted).
 */
@Composable
fun LocationInfoDialog(onAllow: (() -> Unit)?, onLater: () -> Unit) {
    val t = LocalPiyo.current
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.location_why_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoLine(Icons.Rounded.Map, stringResource(R.string.location_why_map))
                InfoLine(Icons.Rounded.DirectionsBus, stringResource(R.string.location_why_lines))
                InfoLine(Icons.Rounded.VisibilityOff, stringResource(R.string.location_why_foreground))
                InfoLine(Icons.Rounded.PhoneAndroid, stringResource(R.string.location_why_stays))
                InfoLine(Icons.Rounded.Search, stringResource(R.string.location_why_search))
                InfoLine(Icons.Rounded.History, stringResource(R.string.location_why_trips))
                Text(stringResource(R.string.location_why_precise), color = t.muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onAllow ?: onLater) { Text(stringResource(if (onAllow != null) R.string.location_why_allow else R.string.close), color = t.primary) }
        },
        dismissButton = if (onAllow != null) ({ TextButton(onClick = onLater) { Text(stringResource(R.string.location_why_later)) } }) else null,
        containerColor = t.surface, titleContentColor = t.ink, textContentColor = t.ink,
    )
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    val t = LocalPiyo.current
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = t.primary, modifier = Modifier.padding(top = 1.dp).size(20.dp))
        Text(text, fontSize = 14.sp, lineHeight = 19.sp, color = t.ink)
    }
}

/**
 * "Centrar en mi ubicación": asks for permission if needed, then calls [center] with the position it already
 * has (instant) and again with a fresh fix when it arrives. Every tap re-centres, whatever the user did to the
 * map in between, because the caller builds a new camera request each time ([bo.piyo.app.map.MapCamera.seq]).
 */
@Composable
fun rememberCenterOnMe(center: (LatLng) -> Unit): () -> Unit {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val onCenter by rememberUpdatedState(center)
    val ask = rememberLocationPermission {
        scope.launch {
            c.location.position.value?.let(onCenter)
            c.location.locate()?.let(onCenter)
        }
    }
    return remember(ask) { { ask(true) } }
}

fun appSettingsIntent(ctx: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
