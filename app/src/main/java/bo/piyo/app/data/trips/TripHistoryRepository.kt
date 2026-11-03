package bo.piyo.app.data.trips

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import bo.piyo.app.domain.model.TripRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.tripsStore: DataStore<Preferences> by preferencesDataStore(name = "piyo_trips")

/** Finished trips, newest first, at most [MAX] of them. Stored only on the phone, like favorites. */
class TripHistoryRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("trips")
    private val serializer = ListSerializer(TripRecord.serializer())

    val trips: Flow<List<TripRecord>> = context.tripsStore.data.map { p -> decode(p[key]) }

    suspend fun add(record: TripRecord) = context.tripsStore.edit { p ->
        val cur = decode(p[key]).filter { it.id != record.id }
        p[key] = json.encodeToString(serializer, (listOf(record) + cur).take(MAX))
    }

    suspend fun remove(id: String) = context.tripsStore.edit { p ->
        p[key] = json.encodeToString(serializer, decode(p[key]).filter { it.id != id })
    }

    suspend fun clear() = context.tripsStore.edit { it.remove(key) }

    private fun decode(s: String?): List<TripRecord> =
        s?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()

    companion object { const val MAX = 50 }
}
