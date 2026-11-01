package bo.piyo.app.domain.model

import kotlinx.serialization.Serializable

/**
 * A finished trip, kept on the phone so the user can review past trips and repeat them
 * ("Tus viajes"). Planned numbers come from the itinerary; [actualMinutes] is wall-clock time
 * between "Empezar" and "Terminar". Nothing here is sent to the backend.
 */
@Serializable
data class TripRecord(
    val id: String,
    val startedAt: Long,
    val finishedAt: Long,
    val originLabel: String,
    val destinationLabel: String,
    val originLat: Double,
    val originLng: Double,
    val destLat: Double,
    val destLng: Double,
    val lineIds: List<String>,
    val lineDisplays: List<String>,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val walkMeters: Int,
    val transfers: Int,
    val fareBs: String,
    val arrived: Boolean,
) {
    val origin: LatLng get() = LatLng(originLat, originLng)
    val destination: LatLng get() = LatLng(destLat, destLng)
}
