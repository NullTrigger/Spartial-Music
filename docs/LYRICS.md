# Lyrics providers

Nodes never bundles lyrics. A track gets lyrics from a local `.lrc` file the
user placed on the device, or from one of the public providers below, in the
order chosen under **Settings → Lyrics**. The repository asks providers one at a
time with a short budget, refuses an answer whose title, artist or duration
clearly belongs to another song, and caches the result on the device.

No provider is sent the user's account, cookie or listening history: a lookup
contains only the track title, artist, album, duration and (for YouTube Music
tracks) the video id. See [PRIVACY.md](../PRIVACY.md).

| Provider | Id | Timing | Key | Notes |
|---|---|---|---|---|
| Local files | `local` | LRC | none | `files/lyrics/<trackId>.lrc`; always first |
| NetEase Cloud Music | `netease` | LRC | none | Public web search + lyric endpoints; unofficial and rate-limited |
| LRCLIB | `lrclib` | LRC or plain | none | Free index; asks clients to identify with a User-Agent |
| SimpMusic | `simp_music` | LRC or plain | none | Keyed by YouTube video id; public lyrics API |
| Lyrics.ovh | `lyrics_ovh` | plain | none | Last-resort plain-text fallback |

## Terms and behaviour

- **Local** — the user's own file. It wins over every network source because it
  was put there deliberately, and it keeps working offline.
- **NetEase Cloud Music** — uses the same web endpoints as the NetEase player.
  They are not a documented public API, may change without notice, and may be
  rate-limited or blocked in some regions. Nodes sends a browser User-Agent and
  a `Referer` header, does not log in, and does not scrape account pages.
- **LRCLIB** — <https://lrclib.net>. The API is free for non-commercial use and
  asks callers to identify themselves; the provider sends the `Nodes/1.0`
  User-Agent. `/api/get` is exact-match, with `/api/search` as a filtered
  fallback.
- **SimpMusic** — <https://api-lyrics.simpmusic.org>. The provider prefers the
  YouTube video id and falls back to a text search. SimpMusic's own terms apply.
- **Lyrics.ovh** — <https://lyrics.ovh>. A free, keyless API that returns plain
  text only. It is last in the default order because it cannot time a song.

Providers must never throw: a network error is `LyricsPayload.Failed`, a miss is
`LyricsPayload.NotFound`. The repository handles timeouts, cancellation and
parsing failures, so a dead provider costs the lyrics, not playback.

## Adding a provider

1. Implement `LyricsProvider` in `core/lyrics/providers/` and never throw.
2. Add its id to `LyricsProviders` (`DEFAULT_ORDER` and `KNOWN`).
3. Register it in `di/LyricsModule.kt` with `@IntoSet`.
4. Add a label to `LyricsSourceRows` and `res/values/strings.xml`.
5. Document the endpoint, terms and rate limits in this file.
6. Add a unit test for any parsing or ranking logic.
