package bo.piyo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import bo.piyo.app.ui.LocalContainer
import bo.piyo.app.ui.LocalSession
import bo.piyo.app.ui.TripSession
import bo.piyo.app.ui.nav.FirstLoadRoute
import bo.piyo.app.ui.nav.HomeRoute
import bo.piyo.app.ui.nav.PiyoNavHost
import bo.piyo.app.ui.nav.ThanksRoute
import bo.piyo.app.ui.theme.PiyoTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** First screen; null while the bundle opens (the system splash stays up meanwhile). */
    private var start: Any? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The only splash: the system one (Theme.Piyo.Starting) — app background, launcher icon, and on
        // Android 12+ the wordmark + tagline at the bottom. It stays until the dataset is open, then
        // fades out over Inicio. No second, hand-drawn splash: the OS draws the icon with the device's own
        // mask, and nothing we draw afterwards would match it on every phone.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PiyoApp).container
        val openThanks = intent?.data?.host == "gracias"
        splash.setKeepOnScreenCondition { start == null }
        splash.setOnExitAnimationListener { v -> v.view.animate().alpha(0f).setDuration(220L).withEndAction { v.remove() }.start() }
        lifecycleScope.launch {
            container.lines.load()
            container.settings.installId()
            container.location.refresh()
            val firstRunDone = container.settings.firstRunDone.first()
            start = when {
                openThanks -> ThanksRoute
                firstRunDone -> HomeRoute
                else -> FirstLoadRoute
            }
        }
        setContent {
            val darkOverride by container.settings.darkOverride.collectAsStateWithLifecycle(initialValue = "system")
            val session = remember { TripSession() }
            val s = start
            CompositionLocalProvider(LocalContainer provides container, LocalSession provides session) {
                PiyoTheme(darkOverride) { if (s != null) PiyoNavHost(startRoute = s) }
            }
        }
    }
}
