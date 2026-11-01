package bo.piyo.app.di

import bo.piyo.app.data.favorites.FavoritesRepository
import bo.piyo.app.data.feedback.ContributionsRepository
import bo.piyo.app.data.feedback.ManifestRepository
import bo.piyo.app.data.feedback.NpsRepository
import bo.piyo.app.data.feedback.ReportsRepository
import bo.piyo.app.data.lines.LinesRepository
import bo.piyo.app.data.location.LocationProvider
import bo.piyo.app.data.providers.ProvidersRepository
import bo.piyo.app.data.settings.SettingsRepository
import bo.piyo.app.data.trips.TripHistoryRepository
import bo.piyo.app.map.MapProvider

/**
 * Manual dependency container. The concrete graph is built once by `AppModule.create(...)`.
 * Screens never see BuildConfig.
 */
interface AppContainer {
    val appVersion: String
    /** Effective line data: official bundle + the active local provider (CONTRIBUTIONS_DESIGN.md §2). */
    val lines: LinesRepository
    /** Official data only (what contributions are based on). */
    val officialLines: LinesRepository
    val providers: ProvidersRepository
    val contributions: ContributionsRepository
    val favorites: FavoritesRepository
    val trips: TripHistoryRepository
    val settings: SettingsRepository
    val reports: ReportsRepository
    val nps: NpsRepository
    val manifest: ManifestRepository
    val location: LocationProvider
    val mapProvider: MapProvider
}
