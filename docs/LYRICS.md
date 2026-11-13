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
| BetterLyrics | `better_lyrics` | TTML → word-synced LRC | none | `lyrics-api.boidu.dev`; server-side matching |
| LyricsPlus | `lyrics_plus` | line + word | none | Four community mirrors; word timing where available |
| Paxsenix | `paxsenix` | TTML → word-synced LRC | none (scraped web JWT) | Apple Music catalogue via `lyrics.paxsenix.org` |
| LRCLIB | `lrclib` | LRC or plain | none | Free index; asks clients to identify with a User-Agent |
| SimpMusic | `simp_music` | LRC or plain | none | Keyed by YouTube video id; public lyrics API |
| NetEase Cloud Music | `netease` | LRC | none | Public web search + lyric endpoints; unofficial and rate-limited |
| KuGou | `kugou` | LRC | none | Strong for Asian and international tracks |
| Lyrics.ovh | `lyrics_ovh` | plain | none | Last-resort plain-text fallback before captions |
| YouTube captions | `youtube_captions` | caption cues | none | The video's own captions; only for YouTube Music tracks, and last |

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
  text only. Near the end of the order because it cannot time a song.
- **BetterLyrics** — <https://lyrics-api.boidu.dev>. Answers an Apple-style
  TTML document, the richest format in general circulation. The endpoint does
  its own matching from title, artist and duration, so the client has no extra
  metadata to check against; a wrong answer there is the server's mistake.
- **LyricsPlus** — community mirrors (`lyricsplus.binimum.org`,
  `lyricsplus.atomix.one`, `lyricsplus.prjktla.my.id`,
  `lyricsplus-seven.vercel.app`). Volunteers run these, so some are down at any
  time; the provider remembers the last one that answered and tries it first.
- **Paxsenix** — <https://lyrics.paxsenix.org>, a community proxy for Apple
  Music's syllable-timed lyrics. The song is looked up on Apple's own
  catalogue first, which needs the web player's public JWT: the provider reads
  it from the page bundle once per session, keeps it until Apple answers 401,
  and never sends an account, cookie or purchase. No Apple account is involved.
- **KuGou** — `mobileservice.kugou.com` and `lyrics.kugou.com`, the same
  endpoints the KuGou mobile client uses. Song search, lyric search by hash or
  keyword, then a base64 lyric download. Line-synced LRC only.
- **YouTube captions** — the video's own caption track through the same
  InnerTube client playback already uses. No new host, no extra identifier.
  It is deliberately last: captions are whatever the uploader typed, so a real
  lyric sheet from any earlier provider beats them.

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
