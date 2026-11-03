package bo.piyo.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "piyo_settings")

/** Everything stored on the phone that is not line data or favorites. No account, no sync. */
class SettingsRepository(private val context: Context) {
    private object K {
        val installId = stringPreferencesKey("install_id")
        val firstRunDone = booleanPreferencesKey("first_run_done")
        val darkOverride = stringPreferencesKey("dark_override") // "system" | "on" | "off"
        val notifications = booleanPreferencesKey("notifications")
        val firstOpenAt = longPreferencesKey("first_open_at")
        val completedTrips = intPreferencesKey("completed_trips")
        val lastNpsAt = longPreferencesKey("last_nps_at")
        val npsDismissals = intPreferencesKey("nps_dismissals")
        val lastRoute = stringPreferencesKey("last_route")
        val recentSearches = stringPreferencesKey("recent_searches")
        val pendingReports = stringPreferencesKey("pending_reports")
        val pendingNps = stringPreferencesKey("pending_nps")
        val pushRegisteredAt = longPreferencesKey("push_registered_at")
        val notifPermissionAsked = booleanPreferencesKey("notif_permission_asked")
        val locationPermissionAsked = booleanPreferencesKey("location_permission_asked")
    }

    private val store get() = context.settingsStore

    /** Random id generated on first run. Never derived from hardware ids; resettable by clearing app data. */
    suspend fun installId(): String {
        val p = store.data.first()
        p[K.installId]?.let { return it }
        val id = UUID.randomUUID().toString()
        store.edit { it[K.installId] = id; it[K.firstOpenAt] = System.currentTimeMillis() }
        return id
    }

    val firstRunDone: Flow<Boolean> = store.data.map { it[K.firstRunDone] ?: false }
    suspend fun setFirstRunDone() = store.edit { it[K.firstRunDone] = true }

    val darkOverride: Flow<String> = store.data.map { it[K.darkOverride] ?: "system" }
    suspend fun setDarkOverride(v: String) = store.edit { it[K.darkOverride] = v }

    val notifications: Flow<Boolean> = store.data.map { it[K.notifications] ?: true }
    suspend fun setNotifications(v: Boolean) = store.edit { it[K.notifications] = v }

    /** When the FCM token was last sent to the backend; 0 = not registered (PushManager). */
    suspend fun pushRegisteredAt(): Long = store.data.first()[K.pushRegisteredAt] ?: 0L
    suspend fun setPushRegisteredAt(v: Long) = store.edit { it[K.pushRegisteredAt] = v }

    /** True once the app has shown the Android 13+ notification prompt on its own (only ever once). */
    suspend fun notifPermissionAsked(): Boolean = store.data.first()[K.notifPermissionAsked] ?: false
    suspend fun setNotifPermissionAsked() = store.edit { it[K.notifPermissionAsked] = true }

    /** True once the app has shown the location prompt on its own (first launch; later only on a user tap). */
    suspend fun locationPermissionAsked(): Boolean = store.data.first()[K.locationPermissionAsked] ?: false
    suspend fun setLocationPermissionAsked() = store.edit { it[K.locationPermissionAsked] = true }


    val lastRouteJson: Flow<String?> = store.data.map { it[K.lastRoute] }
    suspend fun setLastRouteJson(v: String) = store.edit { it[K.lastRoute] = v }

    val recentSearchesJson: Flow<String?> = store.data.map { it[K.recentSearches] }
    suspend fun setRecentSearchesJson(v: String) = store.edit { it[K.recentSearches] = v }

    val pendingReportsJson: Flow<String?> = store.data.map { it[K.pendingReports] }
    suspend fun setPendingReportsJson(v: String) = store.edit { it[K.pendingReports] = v }

    val pendingNpsJson: Flow<String?> = store.data.map { it[K.pendingNps] }
    suspend fun setPendingNpsJson(v: String) = store.edit { it[K.pendingNps] = v }

    /** NPS gating from docs/02-ux.md: ≥7 days since first open, ≥5 completed trips, ≤1 per 90 days, stop after 2 dismissals in a row. */
    suspend fun recordCompletedTrip(): Int {
        var n = 0
        store.edit { n = (it[K.completedTrips] ?: 0) + 1; it[K.completedTrips] = n }
        return n
    }

    suspend fun shouldAskNps(now: Long = System.currentTimeMillis()): Boolean {
        val p = store.data.first()
        val firstOpen = p[K.firstOpenAt] ?: now
        val trips = p[K.completedTrips] ?: 0
        val last = p[K.lastNpsAt] ?: 0L
        val dismissals = p[K.npsDismissals] ?: 0
        val day = 24L * 3600 * 1000
        return now - firstOpen >= 7 * day && trips >= 5 && now - last >= 90 * day && dismissals < 2
    }

    suspend fun recordNpsShown(dismissed: Boolean) = store.edit {
        it[K.lastNpsAt] = System.currentTimeMillis()
        it[K.npsDismissals] = if (dismissed) (it[K.npsDismissals] ?: 0) + 1 else 0
    }
}
