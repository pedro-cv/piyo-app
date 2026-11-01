# Piyo Android app — guide for AI assistants

Free, ad-free public-transport app for Santa Cruz de la Sierra. Kotlin, Jetpack Compose (Material 3),
MapLibre for the map, Photon for address search, DataStore for local state. Trip planning runs on the
phone over a bundled dataset; the API only publishes dataset releases, the map style and receives
reports / NPS / route contributions.

If this folder is inside the workspace repo, the root `CLAUDE.md` and `.claude/rules/` also apply.

## Map of the code (`app/src/main/java/bo/piyo/app`)

| Package | Role | Start here |
|---|---|---|
| `di/` | Manual DI. `AppContainer` is the interface screens use; `AppModule.create()` builds it from `BuildConfig` | `AppContainer.kt` |
| `data/lines/` | Dataset parsing (package format 2), effective lines = official + active local provider, seed/download/swap | `DatasetParser.kt`, `LinesRepository.kt`, `EffectiveLinesRepository.kt` |
| `data/providers/` | Local data providers (user-drawn routes), statuses from review | `ProvidersRepository.kt` |
| `data/feedback/` | Reports, NPS, manifest, contributions; HTTP implementations + offline outbox | `Repositories.kt`, `HttpRepositories.kt`, `ContributionsRepository.kt` |
| `data/favorites/`, `data/trips/`, `data/settings/` | DataStore-backed state | one file each |
| `data/location/` | Fused/GPS location, in memory only | `LocationProvider.kt` |
| `data/api/` | OkHttp client, headers (`X-Install-Id`, app version) | `PiyoApi.kt` |
| `domain/model/` | `Line`, `Variant`, `Bundle`, `Itinerary`, `Provider`, `TripRecord` | `Models.kt` |
| `domain/routing/` | Planner: snap to line, direct + one-transfer, ranking (Menos transbordos / Menos caminata) | `RoutePlanner.kt`, `TransferBuilder.kt`, `TripGuide.kt` |
| `domain/geo/` | Haversine, polyline distance, grid index | `Geo.kt` |
| `map/` | `MapProvider` interface + MapLibre implementation, Photon geocoder | `MapProvider.kt` |
| `ui/nav/` | Type-safe routes and the NavHost | `Routes.kt`, `PiyoNavHost.kt` |
| `ui/screens/` | One file per screen; tabs Buscar / Líneas / Favoritos / Datos in `TabsScreen.kt` | `HomeScreen.kt`, `ResultsScreen.kt`, `RouteEditorScreen.kt` |
| `ui/components/`, `ui/theme/`, `ui/util/` | Shared widgets, Material theme, notifier/helpers | — |
| `ui/Session.kt` | In-memory trip session (origin, destination, results, selected tab) | — |

Resources: `res/values/strings.xml` (all copy, Spanish voseo), `values-night` (dark), adaptive icon in
`mipmap-*`, splash via `core-splashscreen`. The bundled dataset is `assets/dataset-seed.json`.

## Commands

```sh
./gradlew :app:testDebugUnitTest          # JVM tests (parser on the real seed, planner, provider merge)
./gradlew :app:installDebug               # build + install; config from keys.properties (see .example)
./gradlew :app:assembleRelease            # minified; signed with the debug key until a real keystore exists
adb shell am start -n bo.piyo.app/.MainActivity
adb exec-out screencap -p > shot.png      # evidence for PRs
```
Windows: use `gradlew.bat`. Without `keys.properties` the app talks to `http://10.0.2.2:8080`
(emulator → host). Phone on the same Wi-Fi: `PIYO_API_BASE_URL=http://<PC-IP>`.

## Rules that matter most here

1. No secrets, no analytics, no ads, no accounts, no third-party SDK that phones home.
   `keys.properties` is ignored; new keys get a default in `build.gradle.kts` and a line in the `.example`.
2. Offline first: everything except address search, unseen map tiles and sending things must work with
   no network, and the UI says so honestly.
3. Copy in `strings.xml`, Spanish, voseo. Never inline text in composables.
4. Provider branding stays out of code and copy; show the dataset's `attribution`/`license` fields.
5. Planner or parser change ⇒ JVM test + a real-device check of a known trip, both recorded in the PR.
6. Dependencies through the version catalog, justified in `docs/implementation/DEPENDENCIES.md`.
7. Tests green and `/ship` before a PR. `main` is PR-only.

## Skills

`/new-screen`, `/new-repository`, `/verify-app` live in `.claude/skills/`. Root-level `/ship` and
`/public-release-check` apply as well.
