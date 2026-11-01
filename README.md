# Piyo · Android app

Free, ad-free public-transport app for Santa Cruz de la Sierra, Bolivia. Trip planning, the lines
directory, favourites and the route editor run on the phone over a bundled dataset, so they work without
a connection. Address search and new map areas need internet. No accounts, no analytics, no ads.

The backend that publishes dataset releases and the map style lives in the `piyo-backend` repository.

## Run it

Requirements: JDK 17, Android SDK 35 (Android Studio installs both). Gradle comes with the wrapper.

```sh
cp keys.properties.example keys.properties     # optional: where the API and the map come from
./gradlew :app:installDebug                    # Windows: gradlew.bat
adb shell am start -n bo.piyo.app/.MainActivity
```

Without `keys.properties` the app talks to `http://10.0.2.2:8080` (an emulator's route to a backend on
the same PC). For a phone on the same Wi-Fi set `PIYO_API_BASE_URL=http://<PC-IP>` with the compose stack
from `piyo-backend` running. Lines and routing work even when no backend is reachable.

Tests: `./gradlew :app:testDebugUnitTest` (dataset parser on the bundled seed, planner ranking and transfer
cases, provider merge, variant reversal).

## What works offline

| Works offline | Needs internet |
|---|---|
| Trip planning (routing runs on the phone) | Address search by name and the address under a map pin (Photon) |
| Lines directory, line detail, favourites, trip history | Map tiles for areas not viewed before |
| Local data providers and the route editor | Dataset update check, sending reports / NPS / contributions, review status |
| Picking a destination on the map, "Mi ubicación", saved places | |

## Structure

`app/src/main/java/bo/piyo/app`: `ui/` (Compose screens, navigation, theme), `domain/` (models, planner,
geo), `data/` (dataset, providers, favourites, feedback, settings, location), `map/` (MapLibre + Photon),
`di/` (manual container). Details for contributors and AI assistants: `CLAUDE.md`.

## Data and attribution

Line geometry comes from dataset releases published by the backend. Each release carries its own
`attribution` and `license`, which the app shows on the line screen and under Datos → Acerca de.
Sources are listed in `docs/DATA_SOURCES.md`. Map data © OpenStreetMap contributors; tiles from a
self-hosted Protomaps extract; search by Photon (komoot).

## Contributing

See `CONTRIBUTING.md` (Spanish or English welcome), `CODE_OF_CONDUCT.md` and `SECURITY.md`.
Wrong line data is best reported from inside the app (Reportar) or drawn in Datos and sent for review.

## License

See `LICENSE`.
