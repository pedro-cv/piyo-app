package bo.piyo.app.data.favorites

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import bo.piyo.app.domain.model.FavoritePlace
import bo.piyo.app.domain.model.Favorites
import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.favoritesStore: DataStore<Preferences> by preferencesDataStore(name = "piyo_favorites")

/**
 * Saved places (Casa and Trabajo always first, plus any the user adds, each with an icon and a colour)
 * and favorite lines. Stored only on the phone (docs/02-ux.md, screen 5). Older installs that stored
 * `home` / `work` as separate keys are migrated on first read.
 */
class FavoritesRepository(private val context: Context) {
    @Serializable
    private data class StoredPlace(val id: String, val name: String, val detail: String, val lat: Double, val lng: Double) {
        fun toPlace() = Place(id, name, detail, LatLng(lat, lng))
        companion object { fun of(p: Place) = StoredPlace(p.id, p.name, p.detail, p.position.lat, p.position.lng) }
    }

    @Serializable
    private data class StoredFav(val id: String, val label: String, val icon: String = "place", val color: String = DEFAULT_COLOR, val place: StoredPlace? = null) {
        fun toFav() = FavoritePlace(id, label, icon, color, place?.toPlace())
        companion object { fun of(f: FavoritePlace) = StoredFav(f.id, f.label, f.icon, f.color, f.place?.let { StoredPlace.of(it) }) }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val homeKey = stringPreferencesKey("home")   // legacy
    private val workKey = stringPreferencesKey("work")   // legacy
    private val placesKey = stringPreferencesKey("places")
    private val linesKey = stringSetPreferencesKey("lines")
    private val listSerializer = ListSerializer(StoredFav.serializer())

    val favorites: Flow<Favorites> = context.favoritesStore.data.map { p ->
        Favorites(places = places(p), lineIds = p[linesKey] ?: emptySet())
    }

    suspend fun current(): Favorites = favorites.first()

    /** Adds or replaces a place (by id). A blank id gets a new one. */
    suspend fun upsertPlace(f: FavoritePlace) = context.favoritesStore.edit { p ->
        val fav = if (f.id.isBlank()) f.copy(id = "f_" + UUID.randomUUID().toString().take(8)) else f
        val cur = places(p)
        val next = if (cur.any { it.id == fav.id }) cur.map { if (it.id == fav.id) fav else it } else cur + fav
        p[placesKey] = json.encodeToString(listSerializer, next.map { StoredFav.of(it) })
    }

    /** Removes a user-created place; Casa and Trabajo only lose their address. */
    suspend fun removePlace(id: String) = context.favoritesStore.edit { p ->
        val cur = places(p)
        val next = if (id == FavoritePlace.HOME || id == FavoritePlace.WORK) cur.map { if (it.id == id) it.copy(place = null) else it } else cur.filter { it.id != id }
        p[placesKey] = json.encodeToString(listSerializer, next.map { StoredFav.of(it) })
    }

    /** Sets the address of an existing place (from the search / map picker). */
    suspend fun setPlaceLocation(id: String, place: Place?) = context.favoritesStore.edit { p ->
        val next = places(p).map { if (it.id == id) it.copy(place = place) else it }
        p[placesKey] = json.encodeToString(listSerializer, next.map { StoredFav.of(it) })
    }

    suspend fun setHome(place: Place?) = setPlaceLocation(FavoritePlace.HOME, place)
    suspend fun setWork(place: Place?) = setPlaceLocation(FavoritePlace.WORK, place)

    suspend fun toggleLine(lineId: String) = context.favoritesStore.edit { p ->
        val cur = p[linesKey] ?: emptySet()
        p[linesKey] = if (lineId in cur) cur - lineId else cur + lineId
    }

    /** Places from the new key, or migrated from the legacy home/work keys; Casa and Trabajo are always present and first. */
    private fun places(p: Preferences): List<FavoritePlace> {
        val stored = p[placesKey]?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }?.map { it.toFav() }
        val base = stored ?: listOf(
            FavoritePlace(FavoritePlace.HOME, "Casa", "home", "#2A6A47", p[homeKey]?.let(::decode)),
            FavoritePlace(FavoritePlace.WORK, "Trabajo", "work", "#2F6FB6", p[workKey]?.let(::decode)),
        )
        val fixed = listOf(
            base.firstOrNull { it.id == FavoritePlace.HOME } ?: FavoritePlace(FavoritePlace.HOME, "Casa", "home", "#2A6A47"),
            base.firstOrNull { it.id == FavoritePlace.WORK } ?: FavoritePlace(FavoritePlace.WORK, "Trabajo", "work", "#2F6FB6"),
        )
        return fixed + base.filter { !it.fixed }
    }

    private fun decode(s: String): Place? = runCatching { json.decodeFromString(StoredPlace.serializer(), s).toPlace() }.getOrNull()

    companion object { const val DEFAULT_COLOR = "#2A6A47" }
}
