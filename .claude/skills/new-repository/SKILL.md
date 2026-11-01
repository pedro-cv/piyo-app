---
name: new-repository
description: Add a data repository (DataStore, HTTP or dataset-backed) to the Piyo Android app with its interface, implementation, AppContainer entry, offline behaviour and JVM test. Use when a feature needs new persisted or remote data.
---

# /new-repository — data layer additions

Argument: `/new-repository <Name> <kind>` where kind is `datastore`, `http` or `dataset`.

## Steps

1. **Pattern to copy**
   - `datastore`: `data/favorites/FavoritesRepository.kt` (Preferences DataStore, `Flow` + suspend writers).
   - `http`: `data/feedback/Repositories.kt` (interface) + `HttpRepositories.kt` (OkHttp via `PiyoApi`,
     offline outbox in settings, `X-Install-Id` header added centrally).
   - `dataset`: `data/lines/LinesRepository.kt` + `EffectiveLinesRepository.kt` (seed → downloaded
     release → local provider overlay).
2. **Interface first** in `data/<area>/<Name>Repository.kt`: suspend functions and `Flow`s, domain
   types from `domain/model`, no Android types in the signature unless it is a DataStore wrapper.
3. **Implementation** next to it. HTTP: parse JSON with kotlinx.serialization `@Serializable` DTOs,
   map errors to the API contract (`{"error":{"code"}}`: 400 validation, 403 blocked, 429 quota),
   never throw raw exceptions to the UI; return a sealed result or null + reason.
4. **Container**: add `val <name>: <Name>Repository` to `di/AppContainer.kt` and build it in
   `di/AppModule.kt`. Config (URLs, flags) enters there from `BuildConfig`; nowhere else.
5. **Offline**: reads must work from local state; writes queue in the outbox when offline and the UI
   copy says so ("Se enviará cuando haya conexión").
6. **Privacy**: new stored data ⇒ row in `docs/implementation/PRIVACY.md` (what, where, why,
   retention). Nothing identifying leaves the phone except the salted install id the API hashes.
7. **Test**: `src/test/java/bo/piyo/app/data/<area>/<Name>RepositoryTest.kt`. DataStore and HTTP
   implementations are tested through the interface with a fake; dataset logic against the real seed
   like `DatasetParserTest`. `./gradlew :app:testDebugUnitTest` green.
8. **Docs**: if the API contract changed, the backend README row and `CONTRIBUTIONS_DESIGN.md` /
   `DATA_MODEL.md` must match; link the backend PR.
