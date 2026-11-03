package bo.piyo.app.di

import android.app.Application
import android.os.Build
import bo.piyo.app.BuildConfig
import bo.piyo.app.data.api.PiyoApi
import bo.piyo.app.data.favorites.FavoritesRepository
import bo.piyo.app.data.feedback.HttpContributionsRepository
import bo.piyo.app.data.feedback.HttpManifestRepository
import bo.piyo.app.data.feedback.HttpNpsRepository
import bo.piyo.app.data.feedback.HttpReportsRepository
import bo.piyo.app.data.lines.AssetLinesRepository
import bo.piyo.app.data.lines.EffectiveLinesRepository
import bo.piyo.app.data.location.AndroidLocationProvider
import bo.piyo.app.data.providers.ProvidersRepository
import bo.piyo.app.data.push.FirebaseConfig
import bo.piyo.app.data.push.PushManager
import bo.piyo.app.data.settings.SettingsRepository
import bo.piyo.app.data.trips.TripHistoryRepository
import bo.piyo.app.map.maplibre.MapLibreMapProvider
import bo.piyo.app.map.maplibre.PhotonGeocoder

/**
 * The one dependency graph of the app, for every environment. Lines come from the bundled seed plus
 * releases fetched from BuildConfig.API_BASE_URL (keys.properties → PIYO_API_BASE_URL); reports, NPS
 * and the manifest go to the same API. The map is MapLibre rendering the city map bundled in the APK
 * (assets/map: Protomaps tiles + light/dark styles, offline); MAP_STYLE_URL may override it with the
 * API's own style (<api>/v1/map/style.json, Docker stack) or a hosted one (e.g. OpenFreeMap), and
 * MAP_STYLE_DARK_URL gives a hosted override a dark twin. Text search uses Photon (OpenStreetMap, key-free); see MAP_HOSTING.md.
 */
object AppModule {
    fun create(app: Application): AppContainer {
        val settings = SettingsRepository(app)
        val official = AssetLinesRepository(app)
        val providers = ProvidersRepository(app)
        val lines = EffectiveLinesRepository(official, providers)
        val api = PiyoApi(BuildConfig.API_BASE_URL, { settings.installId() }, BuildConfig.VERSION_NAME)
        val dataVersion: suspend () -> Int = { lines.load().dataVersion }
        val deviceClass = "android-${Build.VERSION.SDK_INT}"
        val styleBase = BuildConfig.MAP_STYLE_URL // blank = bundled offline map
        return object : AppContainer {
            override val appVersion = BuildConfig.VERSION_NAME
            override val settings = settings
            override val lines = lines
            override val officialLines = official
            override val providers = providers
            override val contributions = HttpContributionsRepository(api, BuildConfig.VERSION_NAME)
            override val favorites = FavoritesRepository(app)
            override val trips = TripHistoryRepository(app)
            override val location = AndroidLocationProvider(app)
            override val mapProvider = MapLibreMapProvider(app, styleBase, BuildConfig.MAP_STYLE_DARK_URL, PhotonGeocoder(BuildConfig.GEOCODER_URL, BuildConfig.VERSION_NAME))
            override val reports = HttpReportsRepository(api, settings, BuildConfig.VERSION_NAME, dataVersion)
            override val nps = HttpNpsRepository(api, settings, BuildConfig.VERSION_NAME, dataVersion, deviceClass)
            override val manifest = HttpManifestRepository(api)
            override val push = PushManager(app, api, settings, FirebaseConfig(
                BuildConfig.FIREBASE_APP_ID, BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_PROJECT_ID, BuildConfig.FIREBASE_SENDER_ID,
            ))
        }
    }
}
