package bo.piyo.app.domain.model

/** One step of an itinerary: walk or ride. */
sealed class Leg {
    abstract val from: LatLng
    abstract val to: LatLng
    abstract val meters: Int
    abstract val minutes: Int

    data class Walk(
        override val from: LatLng,
        override val to: LatLng,
        override val meters: Int,
        override val minutes: Int,
        val toLabel: String,
    ) : Leg()

    data class Ride(
        override val from: LatLng,
        override val to: LatLng,
        override val meters: Int,
        override val minutes: Int,
        val line: Line,
        val variant: Variant,
        val boardLabel: String,
        val alightLabel: String,
        val path: List<LatLng>,
        val fareBs: String,
    ) : Leg()
}

data class Itinerary(
    val id: String,
    val legs: List<Leg>,
    val totalMinutes: Int,
    val walkMeters: Int,
    val transfers: Int,
    val fareBs: String,
    val hint: String,
) {
    val rides: List<Leg.Ride> get() = legs.filterIsInstance<Leg.Ride>()
    val lines: List<Line> get() = rides.map { it.line }
}

data class TripRequest(
    val origin: LatLng,
    val originLabel: String,
    val destination: LatLng,
    val destinationLabel: String,
)
