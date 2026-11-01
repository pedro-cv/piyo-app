---
name: verify-app
description: Verify the Piyo Android app - JVM unit tests, build and install on a device or emulator, walk the core flows, capture screenshots as evidence. Use before claiming any app change works.
---

# /verify-app — prove it works

## 1. Unit tests (always)
```sh
cd app && ./gradlew :app:testDebugUnitTest --console=plain
```
Expected: `BUILD SUCCESSFUL`, all tests in `src/test` pass (parser on the real seed, planner ranking,
direct ride on line 76, transfers, provider merge, variant reversal). Quote the count in the PR.

## 2. Install
```sh
adb devices                         # one device "device" (not "unauthorized")
./gradlew :app:installDebug         # or :app:installRelease for performance checks
adb shell am start -n bo.piyo.app/.MainActivity
```
Config: `app/keys.properties` (ignored) → `PIYO_API_BASE_URL`. Emulator: `http://10.0.2.2:8080`.
Phone on Wi-Fi: `http://<PC-IP>` with the compose stack up, check `adb shell curl <url>/v1/health` if the
device has curl, or watch the API log.

## 3. Core flows (tick what you ran, say "not run" otherwise)
- [ ] Cold start → Inicio in < 2 s, splash shows the icon on the app background (light and dark)
- [ ] Buscar: Mi ubicación → a known destination; results list "Menos transbordos" first, "Ver N rutas más"
- [ ] A direct ride case (line 76 from the saved phone position) appears as a 1-ride result
- [ ] Líneas: filter "41", open detail, Ida/Vuelta chips, map polyline drawn on the self-hosted style
- [ ] Favoritos: save Casa/Trabajo, plan from it
- [ ] Datos: official data version shown, local provider create → draw route → swap directions → use it
- [ ] Airplane mode: planning, lines, favourites and the editor still work; search says it needs internet
- [ ] Reportar / NPS: sent when online, queued offline (check the API log for 201)

## 4. Evidence
```sh
adb exec-out screencap -p > "$SCRATCHPAD/<flow>_<light|dark>.png"
adb logcat -d | grep -iE "piyo|AndroidRuntime" | tail -50     # no crashes
```
Keep screenshots out of the repo; reference them in the PR with the flow and device model.

## 5. Report
One table: flow · result · evidence. Any failure blocks `/ship`.
