package bo.piyo.app.data.push

import bo.piyo.app.PiyoApp
import bo.piyo.app.ui.util.Notifier
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Receives the backend's data-only messages (backend/README.md §Push) and turns them into local
 * notifications with copy from strings.xml. Nothing is shown when the switch is off.
 */
class PiyoMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val container get() = (application as PiyoApp).container

    override fun onNewToken(token: String) {
        scope.launch { container.push.onNewToken(token) }
    }

    // Called on a background thread with a ~10 s budget, so the short blocking reads are fine.
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val c = container
        if (!runBlocking { c.settings.notifications.first() }) return
        when (data["type"]) {
            "contribution_accepted" -> Notifier.contributionReviewed(this, accepted = true)
            "contribution_rejected" -> Notifier.contributionReviewed(this, accepted = false)
            "report_accepted" -> Notifier.reportAccepted(this)
            "dataset" -> {
                val version = data["version"]?.toIntOrNull() ?: return
                val local = runBlocking { runCatching { c.officialLines.load().dataVersion }.getOrDefault(0) }
                if (version > local) Notifier.datasetAvailable(this)
            }
        }
    }
}
