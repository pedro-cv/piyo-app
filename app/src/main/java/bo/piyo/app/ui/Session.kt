package bo.piyo.app.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import bo.piyo.app.di.AppContainer
import bo.piyo.app.domain.model.Itinerary
import bo.piyo.app.domain.model.Place
import bo.piyo.app.domain.model.TripRequest
import bo.piyo.app.ui.components.Tab

/** In-memory state shared between Buscar → Resultados → Ruta → Opinión. Lives as long as the Activity. */
class TripSession {
    var request: TripRequest? by mutableStateOf(null)
    var itineraries: List<Itinerary> by mutableStateOf(emptyList())
    var selected: Itinerary? by mutableStateOf(null)
    var destinationPlace: Place? by mutableStateOf(null)
    /** Origin chosen on Inicio ("Desde"); null = the phone's position. */
    var originPlace: Place? by mutableStateOf(null)
    /** Set when the user taps "Empezar"; cleared on "Terminar viaje". */
    var activeTrip: Itinerary? by mutableStateOf(null)
    /** Wall-clock start of [activeTrip] (ms), for the elapsed time and the history record. */
    var tripStartedAt: Long by mutableStateOf(0L)
    var showNps: Boolean by mutableStateOf(false)
    var pendingPlacePick: ((Place) -> Unit)? = null
    /** Selected tab of the Inicio pager (Buscar / Líneas / Favoritos). */
    var tab: Tab by mutableStateOf(Tab.SEARCH)

    fun find(id: String): Itinerary? = itineraries.firstOrNull { it.id == id } ?: selected?.takeIf { it.id == id } ?: activeTrip?.takeIf { it.id == id }
}

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
val LocalSession = compositionLocalOf<TripSession> { error("TripSession not provided") }
