# AGENTS.md

Instructions for AI coding agents (and humans who like explicit rules) working in
this repository. Read this before your first edit.

## What this project is

**Nodes** is an open-source Android music player: Kotlin, Jetpack Compose,
Material 3, Media3, Room, Hilt, Retrofit, kotlinx.serialization, Coil. minSdk 26,
targetSdk 36. Package `app.nodes`. Licence GPL-3.0-or-later.

The defining idea is the `MusicSource` interface: every provider of music is a
plugin behind one contract, so the player never knows where a track came from.

## Non-negotiable rules

1. **YouTube has exactly two modes.** Mode 1 is the official Data API v3 plus
   the embedded player. Mode 2 is the unofficial InnerTube client in
   `core/source/youtubemusic/`, which the maintainer added deliberately. Since
   31 October 2026 Mode 2 is the default source by maintainer decision (recorded
   in `YOUTUBE.md`), because Mode 1 cannot show a real music catalogue; Mode 1
   stays registered and selectable, and reversing the decision is a matter of
   flipping the defaults back. In either mode: never download,
   cache or re-encode YouTube media, never skip ads, never bundle a PoToken or
   attestation bypass, never harvest account cookies without a maintainer
   decision recorded in `YOUTUBE.md`. Mode 2 is outside the YouTube API Services
   Terms and can break at any time — say so anywhere it is exposed. If a task
   needs a third mechanism, stop and ask the maintainer.
2. **No third-party content.** No real artist names, album art, lyrics, audio or
   logos. Placeholder art is generated procedurally; sample data is fictional
   (`DemoCatalog.kt`).
3. **No secrets in the repo.** Keys go in git-ignored `local.properties` /
   `keystore.properties` or CI secrets. Never print, log or commit a key. Never
   ask a user to paste a key into a chat.
4. **Ask before licensing or legal decisions.** Changing the licence, adding a
   dependency with a non-permissive licence, or crediting borrowed code are all
   decisions for the maintainer.
5. **Licence headers.** Every new source file starts with:

   ```kotlin
   /*
    * Copyright (C) 2026 The Nodes Authors.
    * Licensed under GPL-3.0-or-later. See LICENSE.
    */
   ```

## Layout

```
app/src/main/java/app/nodes/
  core/
    model/        Track, Playlist, PlaybackHandle, SourceResult — plain data
    source/       MusicSource, SourceRegistry, DemoCatalog, providers
    data/         Room entities/DAOs/database, repositories, cache, quota
    util/         formatting helpers
  playback/       Media3 service, controller, UI state
  di/             Hilt modules
  ui/
    theme/        colours, type, glass surfaces
    components/   reusable composables (Rails, HeroCarousel, MiniPlayer, …)
    navigation/   NavHost, routes, bottom bar
    screens/      one package per screen, each with its ViewModel
```

## How to add a music source

1. Implement `MusicSource` (`core/source/MusicSource.kt`): `search`, `getHome`,
   `getTrending`, `getPlaylist`, `play`. Return `SourceResult` — never throw for
   expected failures such as a missing key or an exhausted quota.
2. Give every track a stable `id` prefixed with the source id (`jamendo:123`) so
   Room and the queue can tell providers apart.
3. Choose a `PlaybackHandle`: `Stream(url)` for progressive audio that Media3 can
   play, `EmbeddedVideo(id)` for the official YouTube player, or `Unavailable`
   with a reason.
4. Register it in `SourceRegistry` / `di/SourceModule.kt`.
5. Document the provider's terms, attribution requirements and rate limits in
   `docs/` or the relevant markdown file, and add it to
   `THIRD_PARTY_LICENSES.md` if you add a dependency.
6. Add a unit test for anything that parses or transforms data.

## Conventions

- Kotlin official style, 4 spaces, trailing commas in multi-line argument lists.
- Compose: state hoisting, `collectAsStateWithLifecycle`, no business logic in
  composables; ViewModels expose a single `StateFlow<UiState>`.
- Coroutines: `Dispatchers.IO` for disk/network, `viewModelScope` for work tied to
  a screen, `SupervisorJob` where one failure must not cancel siblings.
- Error messages are friendly and actionable ("YouTube quota reached for today —
  it resets at midnight Pacific"), never raw stack traces.
- Every user-visible string goes in `res/values/strings.xml`.
- Dark theme only; the glass surfaces must stay readable on API 26 (no
  `Modifier.blur` — use the gradient film + border approach in `Glass.kt`).

## Build and verify

```bash
./gradlew :app:assembleDebug        # must pass before you hand work back
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug            # abortOnError = true
```

Windows: `.\gradlew.bat`. JDK 17. Details: [BUILDING.md](BUILDING.md).

## Things that are deliberately incomplete

- `SessionsScreen` is a labelled UI stub (shared listening is not implemented).
- Mode 2 (unofficial InnerTube) is not implemented, by design. See
  [YOUTUBE.md](YOUTUBE.md).
- Store screenshots must be captured from a real device; see
  `fastlane/metadata/android/en-US/images/README.md`.
