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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.ui.LocalContainer
import kotlinx.coroutines.launch

private val LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * Location permission flow shared by every screen. The returned function:
 * - with permission: runs [onGranted] at once;
 * - without: shows the system dialog; granting runs [onGranted];
 * - when the user asked ([fromUser] = true) and Android will no longer show the dialog (denied twice /
 *   "don't ask again"), opens piyo's page in system settings so the permission can still be granted later.
 * The automatic first-launch request passes fromUser = false and never opens settings. [onDenied] runs
 * after a refusal (the first-launch chain uses it to move on to the notification prompt).
 */
@Composable
fun rememberLocationPermission(onDenied: () -> Unit = {}, onGranted: () -> Unit): (fromUser: Boolean) -> Unit {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val granted by rememberUpdatedState(onGranted)
    val denied by rememberUpdatedState(onDenied)
    var fromUser by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) granted()
        else {
            if (fromUser && ctx.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) == false) {
                ctx.startActivity(appSettingsIntent(ctx))
            }
            denied()
        }
    }
    return remember(launcher) {
        { user: Boolean ->
            if (c.location.hasPermission()) granted() else { fromUser = user; launcher.launch(LOCATION) }
        }
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
