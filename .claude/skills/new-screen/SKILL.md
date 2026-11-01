---
name: new-screen
description: Add a Compose screen to the Piyo Android app - route, NavHost wiring, strings, container access, dark mode and a device check. Use for any new screen, sheet or tab.
---

# /new-screen — add a screen the Piyo way

Argument: screen name in PascalCase (e.g. `/new-screen LineAlerts`) and one line on what it shows.

## Steps

1. **Read the pattern first**: `ui/nav/Routes.kt`, the matching `composable<…>` block in
   `ui/nav/PiyoNavHost.kt`, and one similar screen (`ui/screens/DataScreen.kt` for a settings-like
   page, `ui/screens/LineDetailScreen.kt` for a map + sheet page, `TabsScreen.kt` for a new tab).
   Reuse `Scaffolding.kt` (`PiyoTopBar`, sheets) and `ui/components`.
2. **Route**: add `@Serializable object XRoute` or `data class XRoute(val id: String)` to `Routes.kt`.
   Arguments are primitives/strings only.
3. **Screen**: `ui/screens/XScreen.kt` with
   `@Composable fun XScreen(onBack: () -> Unit, …)`. Get data through `LocalAppContainer.current`
   (or the container parameter used by neighbours), never `BuildConfig` or singletons. State in
   `remember`/`rememberSaveable`; long work in `LaunchedEffect` with the repository's suspend API.
4. **Wire it**: `composable<XRoute> { … }` in `PiyoNavHost.kt`; navigate from the caller with
   `nav.navigate(XRoute(...))`. Back = `nav.popBackStack()`.
5. **Strings**: every label in `res/values/strings.xml`, prefixed by the screen (`x_title`, `x_empty`).
   Spanish, voseo, short. Reuse existing strings (`back`, `retry`, `official_data`) where they fit.
6. **Theme**: use `MaterialTheme.colorScheme` tokens; check light and dark (`values-night`).
7. **Offline**: if the screen needs the API, show the honest offline state used elsewhere
   (see `DataScreen.kt` connection card) and keep the rest usable.
8. **Verify**: `./gradlew :app:testDebugUnitTest` (still green), `./gradlew :app:installDebug`,
   navigate on the device, `adb exec-out screencap -p > <scratchpad>/x_light.png` (and dark).
   Mention the screenshots and the trip/flow you checked in the PR.
9. If the screen changes stored data, update `docs/implementation/PRIVACY.md` (table "On the phone").

## Checklist for the PR
- [ ] Route + NavHost + back navigation
- [ ] No inline strings, no `BuildConfig` in UI
- [ ] Light + dark screenshots from a device/emulator
- [ ] Unit tests green; new domain logic has a test
