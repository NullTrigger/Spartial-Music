# Development guide

A practical tour for contributors. For agent-specific rules see
[AGENTS.md](AGENTS.md); for build setup see [BUILDING.md](BUILDING.md).

## Mental model

```
        ┌─────────────┐   SourceResult<Track>   ┌──────────────────┐
 UI ──▶ │  ViewModel  │ ◀────────────────────── │   MusicSource    │
        └──────┬──────┘                         └────────┬─────────┘
               │ play(track)                             │
        ┌──────▼──────────────┐              ┌───────────┴──────────┐
        │ PlaybackController  │              │ Local │ Jamendo │ YT │
        └──────┬──────────────┘              └──────────────────────┘
               │ Stream(url)                EmbeddedVideo(id)
        ┌──────▼──────┐                    ┌──────────────────────┐
        │   Media3    │                    │ Official YT player   │
        └─────────────┘                    └──────────────────────┘
```

A `Track` never contains a stream URL. It contains a `sourceId`, and calling
`play(track)` resolves a `PlaybackHandle` at the moment of playback. That keeps
URLs from being cached, shared or persisted, and it is what makes the official
YouTube path possible: the handle is a video id, not an audio stream.

## Data flow for Home

1. `HomeViewModel` asks `SourceRegistry` for enabled sources.
2. Each source returns `SourceResult` for `getHome()`; failures are collected,
   not thrown, so one dead provider does not blank the screen.
3. Sections are merged and de-duplicated by track id, hero picks come first.
4. `HomeScreen` renders `HeroCarousel`, `TrackRail` and `ContinuePlayingRow` from
   the single `HomeUiState`.

## State and errors

- `SourceResult.Success` / `SourceResult.Failure(reason, friendlyMessage,
  retryable)`.
- Quota exhaustion is `Failure` with `retryable = false`; the UI shows a banner
  with the reset time instead of a retry button.
- Missing API key is `Failure(retryable = false)` with a "add your key" message —
  the app is fully usable without any key.

## Room

- Entities: cached remote responses, likes, playlists and their tracks, play
  history, search history, lyrics.
- Remote responses go through `RemoteCache` with a TTL, so reopening Home does
  not spend YouTube quota.
- Schema exports live in `app/schemas/`; commit them when you change an entity
  and add a `Migration` (no `fallbackToDestructiveMigration` in release).

## Playback

`PlaybackController` owns exactly one active player:

- `Stream` → `Media3` `ExoPlayer`, exposed through `PlaybackService` (media
  session, notification, lock screen, Bluetooth controls).
- `EmbeddedVideo` → the YouTube player inside `NowPlayingScreen`. No background
  audio, no picture-in-picture, no queue prefetch. Leaving the screen pauses it.
- The ticker updates `PlayerUiState.positionMs` at 4 Hz while playing; the slider
  pauses its own updates while the user drags.

## Design system

- `Color.kt`: near-black background (#050506), layered glass fills, one violet
  accent, one warm accent for likes.
- `Glass.kt`: `glassSurface()` = vertical gradient film + 1 dp hairline border +
  optional artwork backdrop and scrim. This replaces blur so API 26 looks right.
- `Rails.kt`: 148 dp cards with 16 dp gutters, title + subtitle, no chips.
- Type: display for hero titles, labelSmall uppercase for section headers.

Screens must stay readable with the system font scale at 1.3× and with
TalkBack; every icon button needs a content description.

## Tests

```bash
./gradlew :app:testGmsDebugUnitTest :app:testFossDebugUnitTest
```

Unit tests currently cover duration parsing and source model invariants. Good
next tests: `QuotaTracker` date rollover, `RemoteCache` TTL, source response
mapping with fixtures (never live network in tests).

## Adding a screen

1. Add a route in `ui/navigation/Routes.kt`.
2. Add the composable and a `@HiltViewModel` ViewModel in
   `ui/screens/<name>/`.
3. Register it in `NodesApp.kt`; only top-level destinations appear in the bottom
   bar.
4. Keep the background gradient behind the content so the glass has something to
   sit on.

## Git

Small commits, imperative subject, body explains *why*. Run
`git status` before committing and confirm no `local.properties`,
`keystore.properties`, `*.jks` or key material is staged.
