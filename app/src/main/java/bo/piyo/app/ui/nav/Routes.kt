package bo.piyo.app.ui.nav

import kotlinx.serialization.Serializable

/** Type-safe navigation routes (navigation-compose 2.8). */
@Serializable object FirstLoadRoute
/** The tabs screen (Buscar / Líneas / Favoritos live in one pager; the selected tab is in the session). */
@Serializable object HomeRoute
@Serializable data class SearchRoute(val purpose: String = "destination") // "destination" | "origin" | "home" | "work" | "place:<id>"
@Serializable object ResultsRoute
@Serializable data class RouteDetailRoute(val itineraryId: String)
@Serializable data class LineDetailRoute(val lineId: String)
@Serializable object DataRoute
@Serializable data class ReportRoute(val lineId: String? = null, val lat: Double? = null, val lng: Double? = null, val type: String? = null)
@Serializable object ThanksRoute
@Serializable object AboutRoute
/** Route editor (Datos tab): empty for a new route, or the official/local variant to correct. */
@Serializable data class RouteEditorRoute(val lineId: String? = null, val variantId: String? = null)
