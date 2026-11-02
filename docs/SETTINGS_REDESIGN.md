# Settings — options to add and a redesign

Status: **root screen and six sub-screens shipped**; the remaining items are listed at the end.
Design mockup: [`docs/preview/settings-redesign.html`](preview/settings-redesign.html).

## What shipped

| Screen | State |
|---|---|
| Settings root | Grouped cards, account status card, value-in-row summaries |
| Playback | Quality, Wi-Fi only, skip silence, speed picker, sleep timer, keep screen on |
| Lyrics | Provider order, on/off, prefer synced, show source — all previously stored but unreachable |
| Content | Source explanation with the unofficial warning, trending region picker, account |
| Appearance | Theme, system colours |
| Privacy & data | Cache, search history, crash reports, diagnostics |
| About | Version, legal links |

Still open: accent colour (the teal is hard-coded in dozens of composables and needs a
`CompositionLocal` first), the music-source picker (needs a maintainer decision about routing),
settings export/import, queue persistence across launches, and a cache-size breakdown.

## What the reference does

`JAY01-CYBER/Glossy` (a Metrolist fork) is the useful comparison because it has solved the
"the player grew and settings became enormous" problem. It ships roughly **300 preference
keys** across **20 settings sub-screens**: root → Appearance / Player & audio / Stream sources /
Content / Privacy / Backup / Integrations / About, each a grouped list of `Material3SettingsGroup`
cards with a title, optional subtitle and a trailing switch or value.

Nodes has 7 flat sections and 9 stored preferences, three of which no screen has ever shown.

Two lessons worth copying: **group by topic and drill down** (a root screen that stays short no
matter how many options exist), and **show the current value in the row** (`Off`, `1.0×`,
`United States`) so the list is readable without opening anything.

The third lesson is what *not* to copy — see "Out of scope" below.

## 1. Options that already exist in the code and only need a UI

These cost almost nothing: the DataStore keys and setters are already there, and the screens
that would use them already read them.

| Option | Where it lives now | Effect |
|---|---|---|
| Crash reports on/off | `crashReportsEnabled` + `SettingsViewModel.setCrashReportsEnabled` | The handler stops writing files; the Diagnostics screen explains why it is empty |
| Lyrics sources: order, on/off | `lyricsProviderOrder`, `lyricsDisabledProviders` | `local`, `lrclib`, `simp_music`, `youly_plus`; local always wins |
| Prefer synced lyrics | `lyricsPreferSynced` | Already read in `LyricsRepository` |
| Show which source was used | `lyricsShowSource` | Label under the lyrics |
| Trending region | `youtubeRegion` | Mode 1 only: `YouTubeOfficialSource` uses it for trending |

Adding these turns a settings screen that hides half the behaviour into an honest one.

## 2. New options worth adding (small and self-contained)

Playback:

- **Sleep timer** — 15 / 30 / 45 / 60 minutes or end of track, with a fade-out in the last 10 s.
  Media3 exposes play/pause and duration, so this is a service-side coroutine.
- **Gapless playback** — ExoPlayer is gapless by default for the same container; the switch makes
  it explicit and lets us turn it off when a source produces discontinuities.
- **Skip silence** — Media3 `SilenceSkippingAudioProcessor`.
- **Playback speed** — 0.5×–2.0×, `Player.setPlaybackSpeed`.
- **Pause when headphones unplug** — `ACTION_AUDIO_BECOMING_NOISY`.
- **Resume when they reconnect** — same receiver; needs care to avoid resuming in a pocket.
- **Keep the queue between launches** and **resume where you left off** — persist the queue and
  position, restore on start. This is the option most users ask a music player for.
- **Keep the screen on in the player** — `FLAG_KEEP_SCREEN_ON`.

Data and privacy:

- **Cache size shown in the row, a size cap, and auto-clear after N days** — today the row says
  "Clear cached responses" and tells you nothing about what it is costing.
- **Search history: clear / turn off** — `SearchHistoryRepository` exists; only the bundled
  cache clear touches it.
- **Export / import settings** as JSON, and **reset all settings** — both are cheap on DataStore
  and make bug reports reproducible.
- **Quota meter (Mode 1)** — `QuotaTracker` already records units used per Pacific day; showing
  "4,120 / 10,000 units today" removes the guesswork behind the quota error message.

Appearance:

- **Accent colour** — teal stays the default; the palette already exists as `ArtAccents`.
- **Reduce motion** — stops the hero auto-slide and lyric animation (accessibility, and cheaper
  on API 26 devices).
- **Compact rows** — a density switch for the queue and library lists.
- **Artwork backdrop on/off** — the blurred copy behind the player costs a Coil decode per track.

## 3. Options that need a decision first

- **Music source picker** — `primarySource` is currently a constant
  (`SettingsRepository.AppSettings`), because the old Sources section was removed. `AGENTS.md`
  says Mode 1 "stays registered and selectable", but there is no way to select it. A picker with
  Demo / YouTube Data API / YouTube Music (InnerTube) would implement that decision, provided the
  InnerTube row carries the "outside the YouTube API Services Terms, can break at any time"
  warning. **Needs your approval.**
- **Crossfade** — ExoPlayer has no crossfade; it means a second player and manual gain ramps.
  Real feature, real risk, not a settings row.
- **Loudness normalisation** — needs an audio processor and per-track gain data we do not have.

## 4. Out of scope for Nodes (do not port from Glossy)

Downloads and offline caching, sponsor-block, canvas/thumbnail caching of YouTube media, player
cipher and proxy configuration, Cast, Discord / Last.fm / Listen Together. These either download
or re-encode YouTube media, skip ads, or need attestation — all excluded by `AGENTS.md`, and the
Sessions screen stays a labelled stub.

## 5. The redesign

Root screen, top to bottom:

1. **Header** — back, `Settings`, search icon. Search is an icon rather than a field so the list
   starts higher; tapping it expands a query row over the same data.
2. **Status card** — avatar, `Anonymous` / signed-in account, source badge. It answers "what am I
   signed in to and what is serving my audio" before any switch does.
3. **Five groups**, each a 22 dp card on `NodesSurface` with a hairline border:
   Playback · Appearance · Content · Privacy & data · About.
4. **Row anatomy** — optional 32 dp icon tile, title (14 sp), optional subtitle (11 sp, grey),
   then a switch, a value, or a chevron. 54 dp minimum height. Dividers are inset hairlines.
5. **Footer** — version *and build stamp* (`0.1.0 · build 082a7a3`), so a report always identifies
   the binary that produced it.

Sub-screens, one route each: `settings/playback`, `settings/appearance`, `settings/content`,
`settings/lyrics`, `settings/data`, `settings/about`; `diagnostics` already exists.

Deliberate choices:

- No blur — the card treatment is a flat surface plus border, per the `AGENTS.md` rule.
- No accent colour on switches other than teal until the accent picker ships.
- Destructive rows (clear cache, reset) are grey, not red, and confirm before acting.
- Every string in `strings.xml`; no hard-coded copy in composables.

## 6. Implementation order (each step its own commit)

1. `SettingsRow` / `SettingsGroup` / `SettingsScaffold` components + root screen restructure, no
   new preferences yet. Pure UI, easiest to review against the mockup.
2. Expose the five stored-but-hidden preferences (crash reports, lyrics set, region).
3. Playback sub-screen: sleep timer, gapless, skip silence, speed, headphone handling.
4. Queue persistence and resume.
5. Cache size/cap, search history, export/import/reset.
6. Appearance sub-screen (accent, reduce motion, density, backdrop).
7. New files: GPL header; new tests for anything that serialises (settings JSON, queue snapshot).

Verification stays on CI: `assembleDebug`, `testDebugUnitTest`, `lintDebug`. No local build.
