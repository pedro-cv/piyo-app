package bo.piyo.app.data.push

import android.app.Application
import bo.piyo.app.data.api.PiyoApi
import bo.piyo.app.data.settings.SettingsRepository
import bo.piyo.app.ui.util.Notifier
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Firebase values from keys.properties (BuildConfig); all blank = push unavailable. */
data class FirebaseConfig(val appId: String, val apiKey: String, val projectId: String, val senderId: String) {
    val complete get() = listOf(appId, apiKey, projectId, senderId).all { it.isNotBlank() }
}

/**
 * FCM registration (PRIVACY.md §Push). Firebase is initialised here from [FirebaseConfig], not from
 * google-services.json, so the app builds and runs without it. Auto-init is off in the manifest: a token
 * exists, and is sent to the backend (PUT /v1/push/token, tied to the same hashed install id as reports),
 * only while the notifications switch is on and the system permission is granted. Turning either off
 * deletes the token on the phone and on the server. Topic "dataset" announces new data releases.
 */
class PushManager(
    private val app: Application,
    private val api: PiyoApi,
    private val settings: SettingsRepository,
    private val config: FirebaseConfig,
) {
    @Serializable private data class TokenBody(val token: String)

    val available: Boolean get() = messaging() != null

    private fun messaging(): FirebaseMessaging? {
        if (FirebaseApp.getApps(app).isEmpty()) {
            if (!config.complete) return null
            val opts = FirebaseOptions.Builder()
                .setApplicationId(config.appId).setApiKey(config.apiKey)
                .setProjectId(config.projectId).setGcmSenderId(config.senderId)
                .build()
            runCatching { FirebaseApp.initializeApp(app, opts) }.getOrNull() ?: return null
        }
        return runCatching { FirebaseMessaging.getInstance() }.getOrNull()
    }

    private suspend fun wanted() = settings.notifications.first() && Notifier.permitted(app)

    /**
     * Bring the registration in line with the switch and the permission. Idempotent and cheap: called at
     * app start and whenever the switch changes. While enabled the token is re-sent weekly so the server's
     * 60-day purge never drops an active phone. Network errors are swallowed; the next call retries.
     */
    suspend fun sync(force: Boolean = false) = withContext(Dispatchers.IO) {
        runCatching {
            val want = wanted()
            val last = settings.pushRegisteredAt()
            if (!want && last == 0L) return@runCatching // off and never registered: Firebase is not even started
            val fm = messaging() ?: return@runCatching
            if (want) {
                if (force || last == 0L || System.currentTimeMillis() - last > WEEK_MS) {
                    fm.isAutoInitEnabled = true
                    register(fm, fm.token.await())
                }
            } else {
                unregister(fm)
            }
        }
        Unit
    }

    /** FCM rotated the token (PiyoMessagingService.onNewToken). */
    suspend fun onNewToken(token: String) = withContext(Dispatchers.IO) {
        runCatching { val fm = messaging(); if (fm != null && wanted()) register(fm, token) }
        Unit
    }

    private suspend fun register(fm: FirebaseMessaging, token: String) {
        val r = api.put("/v1/push/token", api.json.encodeToString(TokenBody.serializer(), TokenBody(token)))
        if (r.ok) {
            fm.subscribeToTopic(TOPIC_DATASET).await()
            settings.setPushRegisteredAt(System.currentTimeMillis())
        }
    }

    private suspend fun unregister(fm: FirebaseMessaging) {
        // Deleting the token on the phone first makes any copy left on the server useless (FCM answers
        // UNREGISTERED and the server drops it), so an offline DELETE is not a privacy leak.
        runCatching { fm.unsubscribeFromTopic(TOPIC_DATASET).await() }
        runCatching { fm.deleteToken().await() }
        fm.isAutoInitEnabled = false
        runCatching { api.delete("/v1/push/token") }
        settings.setPushRegisteredAt(0L)
    }

    companion object {
        const val TOPIC_DATASET = "dataset"
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { t ->
        if (t.isSuccessful) cont.resume(t.result)
        else cont.resumeWithException(t.exception ?: IllegalStateException("Firebase task failed"))
    }
}
