# YouTube in Nodes

Nodes integrates with YouTube in **one** way: the official YouTube Data API v3
for metadata, and the official embedded IFrame player for playback. This
document explains exactly what that means, what the limits are, and how to set
it up.

> Nodes is not affiliated with YouTube or Google. "YouTube" is a trademark of
> Google LLC. By using this integration you agree to the
> [YouTube Terms of Service](https://www.youtube.com/t/terms),
> the [YouTube API Services Terms of Service](https://developers.google.com/youtube/terms/api-services-terms-of-service)
> and the [Google Privacy Policy](https://policies.google.com/privacy).

## Mode 1 — Official (the default)

| Feature | API | Cost |
| --- | --- | --- |
| Search music | `search.list` (`type=video`, `videoCategoryId=10`) | **100 units** per call |
| Trending / Home chart | `videos.list` (`chart=mostPopular`, `videoCategoryId=10`) | 1 unit |
| Curated playlists | `playlists.list` (`chart=mostPopular`) | 1 unit |
| Playlist contents | `playlistItems.list` | 1 unit |
| Your playlists (signed in) | `playlists.list` (`mine=true`) | 1 unit |
| Liked videos (signed in) | `videos.list` (`myRating=like`) | 1 unit |
| Playback | Official embedded IFrame player | no quota |

The default daily allowance is **10,000 units**, reset at midnight Pacific Time.
Nodes counts units locally (`QuotaTracker`), caches responses for 15 minutes
(`RemoteCache`), and when the budget is gone it stops calling the API and shows:

> "YouTube API quota for today is used up. It resets at midnight Pacific Time.
> Cached and local content still works."

That is a 100-unit search budget of roughly 100 searches a day. Browsing Home
costs about 3 units.

### What Nodes deliberately does not do

- No downloading, caching or re-encoding of YouTube media.
- No ad blocking, no sponsor blocking, no cipher or PoToken bypass.
- No account cookie harvesting: this mode never touches your Google session.

Mode 1 tracks are always played by the embedded player, on screen, and pause
when that screen is disposed. Mode 2 (below) is a separate, opt-in source.

### Setup: API key

1. Open [Google Cloud Console](https://console.cloud.google.com/) and create (or
   pick) a project.
2. **APIs & Services → Library → YouTube Data API v3 → Enable**.
3. **APIs & Services → Credentials → Create credentials → API key**.
4. Restrict the key: *API restrictions* → YouTube Data API v3, and *Application
   restrictions* → Android apps → package `app.nodes` + your signing SHA-1.
5. Put it in `local.properties` (never commit this file):

   ```properties
   YOUTUBE_API_KEY=your_key_here
   ```

   or set the environment variable `YOUTUBE_API_KEY` in CI.
6. Rebuild. Settings → Sources should show "YouTube (official API)" as ready.

### Setup: optional Google Sign-In

Sign-in only adds your own YouTube music. Nodes works fully signed out.

What a Google sign-in unlocks on Home, once `YOUTUBE_API_KEY` and
`YOUTUBE_OAUTH_CLIENT_ID` are set:

- **Liked on YouTube** — the videos your account rated "like", newest first
  (`videos.list`, `myRating=like`).
- **Your playlist** — the tracks of your most recent own playlist
  (`playlists.list` + `playlistItems.list`). A rail of playlist cards would be
  unplayable in an embedded-player app, so the tracks are shown directly.

Both rails are best-effort: if the quota is spent or the token expires the
public charts still load. The token is never persisted, so after a restart the
app asks you to sign in again rather than silently losing your rails.

Sign-in does **not** turn Nodes into a YouTube Music client. There is no
YouTube Music API; its app talks to private endpoints. See Mode 2 below.

1. Get your signing SHA-1:

   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
     -storepass android -keypass android
   ```

2. **Credentials → Create credentials → OAuth client ID → Android**. Use package
   name `app.nodes` (debug builds use `app.nodes.debug` — add a second client if
   you sign in from debug) and the SHA-1 from step 1.
3. Add the client ID to `local.properties`:

   ```properties
   YOUTUBE_OAUTH_CLIENT_ID=1234567890-abc.apps.googleusercontent.com
   ```

4. The requested scope is exactly `https://www.googleapis.com/auth/youtube.readonly`.

Notes:

- The access token is kept **in memory** for the session and never written to
  disk, logged, or sent anywhere except `googleapis.com`.
- If sign-in fails with a `10`/`12501` code, the SHA-1 or package name does not
  match the OAuth client.
- Play Store distribution requires your own privacy policy link and the YouTube
  attribution below.

## Mode 2 — Unofficial InnerTube (the default source in this fork)

`core/source/youtubemusic/` talks to YouTube's private `youtubei` endpoints,
which is how YouTube's own apps talk to YouTube.

**Maintainer decision, 31 October 2026:** this fork ships Mode 2 as the default
source, because Mode 1 (Data API v3 + embedded player) cannot show a real YouTube
Music catalogue — its 100-unit search budget and video-only results left Home on
the demo catalogue, with no albums, artists or songs from the service. Mode 1 is
still built, registered and selectable in Settings → Sources; turning Mode 2 off
returns the app to the documented behaviour above. The legal position below is
unchanged and is accepted with that decision: it is outside the API Services
Terms, and it can break.

| Feature | Endpoint | Notes |
| --- | --- | --- |
| Search | `POST /youtubei/v1/search` (WEB client, songs filter) | no Data API quota used |
| Playlist contents | `POST /youtubei/v1/browse` (`VL<id>`) | |
| Playback | `POST /youtubei/v1/player` (several app identities) | plain `googlevideo` audio URL |

No single client identity serves every track any more, so playback asks several
in turn and takes the first usable answer:

Search sends YouTube Music's own "songs" filter in `params` and reads back only
music shelf rows. Without both, the endpoint answers as plain YouTube search, so
a query like "gaming" returns gameplay clips and reviews; with them, an ordinary
video cannot reach a music player's results at all.


| Order | Client | Version | Why it is in the chain |
| --- | --- | --- | --- |
| 1 | `VISIONOS` | 0.1 | the identity YouTube flags least; plain URLs |
| 2 | `ANDROID_VR` | 1.65.10 | plain URLs, full audio ladder |
| 3 | `ANDROID_VR` | 1.43.32 | older build with different limits |
| 4 | `IOS` | 21.03.1 | plain URLs, muxed formats |
| 5 | `IOS` (iPadOS) | 21.03.3 | last resort |

Each candidate URL is checked before Media3 sees it: a one-byte ranged `GET` must
answer `200`/`206`. (`HEAD` returns `403` from `googlevideo` for every candidate,
so it cannot be used as the probe.) If the check fails the next identity is
tried, and if all of them fail the player shows what each one said — for example
`ANDROID_VR: unplayable (Video unavailable)` — rather than a generic error.

Every client in that chain answers with a plain `url`, which is why Nodes
contains no signature deciphering and no PoToken generation: there is nothing
here to decipher. `WEB_REMIX`, `WEB_CREATOR` and the TV clients are deliberately
absent — they now demand a PoToken and an `n`-parameter transform. A track that
only those clients would serve is reported as unavailable rather than worked
around: adding attestation and cipher code is a maintainer decision, not a
side effect of a playback fix.

If YouTube closes the plain-URL door as well, playback stops and the source has
to be fixed or removed — not patched with a cipher solver.

What it still does not do: no downloading, no caching of media, no ad or sponsor
blocking, no attestation bypass, no cookie harvesting, no account scraping.

### The legal reality, stated plainly

- InnerTube is **not** covered by the YouTube API Services Terms. Using it is
  outside the agreement that Mode 1 relies on.
- Google can rate-limit, change or block it at any time. Nothing here is stable,
  and a broken build is not a bug report you can win.
- Shipping a client that does this may breach the YouTube Terms of Service, and
  some stores (Google Play among them) reject apps that bypass the official
  player. Distributing this mode is the maintainer's and your fork's risk.
- The mode exists because the maintainer decided to add it. A fork that disagrees
  can delete `core/source/youtubemusic/`, the `YOUTUBE_MUSIC` entry in
  `SourceId`, its binding in `di/SourceModule.kt` and the settings toggle; the
  rest of the app is unaffected.

### Attribution

The protocol details were learned from GPL-3.0 projects in this space —
[Metrolist](https://github.com/MetrolistGroup/Metrolist), InnerTune/OuterTune and
its fork [Glossy](https://github.com/izybro110-gif/Glossy). The client identities
in the playback chain (names, versions, user agents and device fields) are the
ones those projects ship, since they are what YouTube currently accepts; the
fallback order, the ranged-GET validation and every line of `InnerTubeClient.kt`
are Nodes' own. No file from those projects is copied into Nodes. See
[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
