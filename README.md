# Nodes

A quiet home for your music. Nodes is an open-source Android music player built
with Kotlin and Jetpack Compose: near-black surfaces, frosted glass, generous
corners, and a strict rule that every track it plays comes from a source it is
allowed to play.

Licensed under **GPL-3.0-or-later**. See [LICENSE](LICENSE).

![Nodes mark](branding/nodes-logo.svg)

---

## What it is

- **Local library** — reads audio already on your device through MediaStore.
- **Creative Commons streaming** — Jamendo's catalogue, credited per track.
- **YouTube, the official way** — YouTube Data API v3 for browsing, the official
  embedded player for playback. No stream extraction, no downloads, no
  background audio for YouTube tracks. See [YOUTUBE.md](YOUTUBE.md).

Everything else (likes, playlists, history, search history) is stored on the
device in Room/DataStore. There is no account, no sync and no analytics.

## Design

| Screen | Notes |
| --- | --- |
| Onboarding | Mark + serif wordmark, "This is an Indie Project", community pills |
| Home | Full-bleed hero carousel fading to black, glass Play pill, pager dots, Continue Playing rail with edge glow, New songs rail, floating glass nav bar + round search button |
| Trending | Chart list with position numbers, per-source |
| Sessions | Listen-together rooms (designed stub: UI is real, shared playback is not) |
| Library | Playlists, Liked songs, Downloads (CC-only policy explained in-app) |
| Search | Debounced search across the active source, recent terms kept locally |
| Now Playing | Large art, seek bar, controls, queue, synced `.lrc` lyrics, YouTube video card |
| Settings | Appearance, sources, audio, YouTube + attribution, cache, about/licences |

Dark by default. Glass is built from a translucent gradient film, a hairline
border and a blurred artwork backdrop (see `ui/components/Glass.kt`) so it looks
the same on API 26 as it does on Android 15, where real backdrop blur exists.

### Screenshots

Screenshots are not committed yet. To add them:

1. Run the app on a device or emulator at 1080 × 2400.
2. Capture Home, Now Playing, Library and Settings.
3. Drop them in `docs/screenshots/` and reference them here, plus
   `fastlane/metadata/android/en-US/images/phoneScreenshots/` for F-Droid.

## Features

- [x] Dark-first Material 3 theme, glass surfaces, custom typography scale
- [x] Hero carousel with generated placeholder artwork (no copyrighted covers)
- [x] Pluggable `MusicSource` interface with three providers
- [x] Media3/ExoPlayer playback through a `MediaSessionService`
- [x] Official YouTube IFrame playback with visible player
- [x] Room-backed likes, playlists, history and provider cache
- [x] YouTube quota accounting with a friendly, actionable error
- [x] Optional Google Sign-In (`youtube.readonly`) for your own playlists
- [x] Synced lyrics from local `.lrc` sidecars
- [x] Settings for source selection, audio, cache and licences

## Architecture

```
app/src/main/java/app/nodes
├── core
│   ├── data          SettingsRepository, LibraryRepository, RemoteCache, QuotaTracker
│   ├── data/db       Room entities, DAOs, database
│   ├── model         Track, Playlist, HomeFeed, PlaybackHandle, SourceResult
│   ├── source        MusicSource interface + SourceRegistry + demo catalogue
│   │   ├── local     MediaStore
│   │   ├── jamendo   Creative Commons streaming
│   │   └── youtube   Data API v3 + optional OAuth
│   └── util
├── playback          PlaybackService (Media3), PlaybackController, PlayerUiState
├── ui
│   ├── components    Glass, Artwork, HeroCarousel, Rails, NodesBottomBar, MiniPlayer
│   ├── navigation    Routes, NodesApp
│   ├── screens       onboarding, home, trending, sessions, library, search,
│   │                 player, playlist, settings
│   └── theme         Color, Type, Theme
└── di                Hilt modules
```

Every provider implements one interface:

```kotlin
interface MusicSource {
    val id: SourceId
    suspend fun availability(): SourceAvailability
    suspend fun search(query: String, limit: Int = 25): SourceResult<List<Track>>
    suspend fun getHome(): SourceResult<HomeFeed>
    suspend fun getTrending(limit: Int = 25): SourceResult<List<Track>>
    suspend fun getPlaylist(playlistId: String): SourceResult<PlaylistDetail>
    suspend fun play(track: Track): SourceResult<PlaybackHandle>
}
```

`play()` returns either `PlaybackHandle.Stream(url)` (local files, CC audio) or
`PlaybackHandle.EmbeddedVideo(videoId)` (YouTube). The player routes accordingly:
ExoPlayer for streams, the official embedded player for YouTube.

## Build

Requirements:

- JDK 21+ (AGP 9 requires 17+; CI uses 21)
- Android SDK with `compileSdk 36` and `build-tools 36.0.0`
- Gradle 9.6.1 (wrapper included)

```bash
git clone <your-fork-url> nodes && cd nodes
cp local.properties.example local.properties   # add your SDK path + keys
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

### Keys

Nothing secret is committed. `app/build.gradle.kts` reads, in order,
`local.properties` then environment variables:

| Key | Needed for |
| --- | --- |
| `YOUTUBE_API_KEY` | YouTube search, Home, Trending |
| `YOUTUBE_OAUTH_CLIENT_ID` | Optional Google Sign-In |
| `JAMENDO_CLIENT_ID` | Creative Commons streaming |

Without keys the app still runs: Home shows the placeholder catalogue, Local
source works if you grant audio permission, and Settings explains what is
missing.

## Roadmap

- [ ] Light theme and true backdrop blur on API 31+
- [ ] Downloads for Creative Commons audio, with licence receipts
- [ ] Sessions backend (shared queue, presence) on top of a real transport
- [ ] Gapless playback and audio quality selection
- [ ] Android Auto browse tree
- [ ] Translated strings (currently English only)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Security reports:
[SECURITY.md](SECURITY.md). Data handling: [PRIVACY.md](PRIVACY.md).

## Credits and licences

- App code: GPL-3.0-or-later, see [NOTICE.md](NOTICE.md).
- Dependencies and their licences: [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
- YouTube integration: [YOUTUBE.md](YOUTUBE.md), "Powered by YouTube".

**Not affiliated with, funded, authorized or endorsed by YouTube, Google LLC,
or any streaming service.** "YouTube" is a trademark of Google LLC. All
third-party content remains the property of its owners and is used only through
the providers' public APIs and licences.
