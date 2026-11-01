# Android app rules

- Architecture: screens (`ui/screens`) → repositories (`data/*`) via the manual `AppContainer`
  (`di/AppContainer.kt`, built in `di/AppModule.kt`). Screens never read `BuildConfig`; config enters
  through the container. Domain logic (`domain/routing`, `domain/geo`, `domain/model`) is pure Kotlin
  and unit-testable on the JVM.
- One build, no flavors. Environment comes from `app/keys.properties` (ignored) read by
  `app/build.gradle.kts`; add a new key there **and** in `keys.properties.example` with a safe default.
- Navigation: type-safe routes in `ui/nav/Routes.kt` (`@Serializable`), wired in `ui/nav/PiyoNavHost.kt`.
  Tabs (Buscar / Líneas / Favoritos / Datos) live in one pager under `HomeRoute`.
- Strings: all user-facing text in `res/values/strings.xml`, Spanish, voseo, short. No inline
  literals in composables. Keep the existing tone (see `contribute_intro`, `no_routes`).
- Material 3 + `ui/theme`. Reuse `ui/components` (BottomBar, ItineraryViews, Components) before adding
  new widgets. Support light and dark (`values-night`).
- Offline first: lines, planner, favourites, providers and the editor must work with no network.
  Anything that needs the API shows the honest wording used in Datos y conexión.
- Dataset: parse with `data/lines/DatasetParser.kt`; package format 2; never hard-code provider names
  (attribution comes from the dataset). Tests in `src/test` parse the real seed; keep them passing.
- Planner changes need a test in `domain/routing` (ranking, direct ride, transfers) and a device check
  with a known trip; record it in the PR.
- Verify on a device or emulator before claiming UI work is done: `./gradlew :app:installDebug`,
  `adb exec-out screencap -p > shot.png`. Capture evidence to the scratchpad, not the repo.
- Dependencies: version catalog `gradle/libs.versions.toml`; justify in
  `docs/implementation/DEPENDENCIES.md`. Prefer AndroidX / Kotlin stdlib over new libraries.
