# YouTube in Spartial Music

Spartial Music integrates with YouTube in **two** ways: the unofficial InnerTube
client (the default source), and the official YouTube Data API v3 provider with
the official embedded IFrame player. This document explains what each mode means,
what the limits are, and how to set it up.

> Spartial Music is not affiliated with YouTube or Google. "YouTube" is a trademark of
> Google LLC. By using this integration you agree to the
> [YouTube Terms of Service](https://www.youtube.com/t/terms),
> the [YouTube API Services Terms of Service](https://developers.google.com/youtube/terms/api-services-terms-of-service)
> and the [Google Privacy Policy](https://policies.google.com/privacy).

## Mode 1 - Official (the documented API path)

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
Spartial Music counts units locally (`QuotaTracker`), caches responses for 15 minutes
(`RemoteCache`), and when the budget is gone it stops calling the API and shows:

> "YouTube API quota for today is used up. It resets at midnight Pacific Time.
> Cached and local content still works."

That is a 100-unit search budget of roughly 100 searches a day. Browsing Home
costs about 3 units.

### What Mode 1 deliberately does not do

- No downloading or caching of media. Playback remains in the official
  embedded player.
- No ad blocking, no sponsor blocking, no cipher or PoToken bypass.
- No account cookie harvesting: this mode never touches your Google session.

Mode 1 tracks are always played by the embedded player, on screen, and pause
when that screen is disposed. Mode 2 (below) is the default source in this build.

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

Sign-in only adds your own YouTube music. Spartial Music works fully signed out.

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

Sign-in does **not** turn Spartial Music into a YouTube Music client. There is no
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
| Home | `POST /youtubei/v1/browse` (`FEmusic_home`) | WEB_REMIX client, real shelves |
| Search | `POST /youtubei/v1/search` (WEB_REMIX, songs filter) | no Data API quota used |
| Playlist contents | `POST /youtubei/v1/browse` (`VL<id>`) | |
| Playback | `POST /youtubei/v1/player` (several app identities) | plain `googlevideo` audio URL |

Every request is a `POST` to `https://music.youtube.com/youtubei/v1/<method>`
whose body carries `context.client` (`clientName` + a current `clientVersion`,
`hl`, `gl`) and whose headers include the matching `X-YouTube-Client-Name` and
`X-YouTube-Client-Version`, `X-Origin`/`Referer` for the music site, and
`X-Goog-Visitor-Id` once the anonymous visitor data has been fetched from
`music.youtube.com/sw.js_data`. Sending the body without the matching headers is
what makes YouTube answer with the wrong renderers (plain YouTube results, no
music shelves).

No single client identity serves every track any more, so playback asks several
in turn and takes the first usable answer:

Search sends YouTube Music's own "songs" filter in `params` and reads back only
music shelf rows. Without both, the endpoint answers as plain YouTube search, so
a query like "gaming" returns gameplay clips and reviews; with them, an ordinary
video cannot reach a music player's results at all.


| Order | Client | Version | Why it is in the chain |
| --- | --- | --- | --- |
| 1 | `WEB_REMIX` | 1.20260114.03.00 | the music web client, first; see the decision below |
| 2 | `VISIONOS` | 0.1 | the identity YouTube flags least; plain URLs |
| 3 | `ANDROID_VR` | 1.65.10 | plain URLs, full audio ladder |
| 4 | `ANDROID_VR` | 1.43.32 | older build with different limits |
| 5 | `IOS` | 21.03.1 | plain URLs, muxed formats |
| 6 | `IOS` (iPadOS) | 21.03.3 | last resort |
| 7 | `TVHTML5_SIMPLY_EMBEDDED_PLAYER` | 2.0 | embedded player, plain URLs |

The order above is the order on a cold start. The client that last produced a
stream this device could fetch is asked first from then on, so a play does not
pay a round trip to a client that is going to refuse before it reaches the one
that works. `WEB_REMIX` holds that place only while it is the one answering.

Each candidate URL is checked before Media3 sees it: a one-byte ranged `GET` must
answer `200`/`206`. (`HEAD` returns `403` from `googlevideo` for every candidate,
so it cannot be used as the probe.) If the check fails the next identity is
tried, and if all of them fail the player shows what each one said — for example
`ANDROID_VR: unplayable (Video unavailable)` — rather than a generic error.

Every client in that chain is asked for a plain `url`. `WEB_REMIX` is the music
web client and is also the metadata client everywhere else in Mode 2.

**Maintainer decision, 23 November 2026 (amended the same day):** `WEB_REMIX`
is the primary playback client, as it is the browsing/metadata client. It is
asked first, and the app identities above follow it as the fallback. The
maintainer asked for the music web client to lead the chain so that playback,
browsing and search all present one identity to YouTube; the earlier version of
this decision had it last.

What this decision does and does not change:

- Yes to `WEB_REMIX` first in the chain, and yes to it staying the browse/
  search/collection client it already is. The plain-URL identities below it are
  unchanged and remain the fallback for anything it will not serve.
- Still no PoToken minting and no `n`-parameter/cipher solver in this build. A
  `WEB_REMIX` player answer that only carries a ciphered format, or that is
  refused without an attestation token, is an unusable candidate: the chain
  moves on and, if nothing else works, the player reports what each identity
  said. Nothing is deciphered to rescue it.
- A track that only `WEB_REMIX`-with-PoToken would serve therefore remains
  unavailable. Minting tokens or solving the signature cipher is a separate,
  explicit maintainer decision — it changes what this project is, and must be
  recorded here first, together with the `AGENTS.md` rule it supersedes.

If YouTube closes the plain-URL door as well, playback stops and the source has
to be fixed or removed — not patched with a cipher solver.

**Maintainer decision, 12 November 2026:** Mode 2 may offer user-initiated
offline downloads and reuse downloaded bytes for offline playback, using
AndroidX Media3 `DownloadManager` / `SimpleCache` only inside the app-private
`context.filesDir`. This is an implementation decision, not a grant of rights
from YouTube and not a claim that downloading is permitted by YouTube's terms.
Mode 1 remains embedded-only and never downloads or caches media. The feature
must not export raw audio to public external storage, re-encode media, skip ads,
or add a PoToken/attestation bypass. Mode 2 remains unofficial, outside the
YouTube API Services Terms, and subject to the legal risks described below.

Mode 2 still does not do ad or sponsor blocking, attestation bypass, or account
scraping beyond the previously documented user-initiated sign-in decision.

### Signing in

**Maintainer decision, 1 November 2026:** Mode 2 may keep the user's own YouTube
Music session cookie. Personalised Home shelves, the user's Library and playback
of their uploads have no anonymous equivalent on InnerTube, and parity with the
reference player was the requirement. This is the cookie-harvesting exception
that rule 1 of `AGENTS.md` requires to be recorded here.

How it works, and what keeps it bounded:

- Settings → YouTube Music account opens Google's own login page in a WebView.
  Spartial Music never sees the password; it only reads the cookie the music site sets
  after the redirect, plus the page's `VISITOR_DATA` and `DATASYNC_ID`.
- The cookie is validated with `account/account_menu` before it is stored, so a
  cookie that is not a session is never kept.
- It is stored in `EncryptedSharedPreferences` (Android Keystore key), is sent
  only to `music.youtube.com`, and is never logged, backed up or cached.
- Authenticated requests carry `Cookie`, `Authorization: SAPISIDHASH
  <ts>_<sha1(ts + " " + SAPISID + " " + origin)>`, `X-Goog-AuthUser: 0` and
  `X-Origin`.
- **Sign out of YouTube Music** deletes the stored cookie and clears the WebView
  cookies.

### Writing to the account: the auto-sync

**Maintainer decision, 3 November 2026:** in addition to reading, Mode 2 may
push a locally liked YouTube Music track to the account's liked songs. This
started as the one-shot "Sync to YT" row in Settings → Content.

**Maintainer decision, 27 November 2026:** that row became Glossy's
*"Auto-sync with account"* switch, and the set of account writes grew to what
the switch promises. Everything below happens only while the switch is **on**
*and* an account is signed in. The switch is off by default, unlike the
reference player, because each of these writes lands on a real account.

What a sync does, and the endpoint behind each part:

- **Likes out.** Every locally liked YouTube Music track is replayed onto the
  account through `like/like`. Replaying a like that already exists is a no-op
  server-side, which is what makes the pass safe to repeat.
- **Likes in.** The account's own liked songs (`browse` with
  `FEmusic_liked_videos`) are added to the local library. Add-only: a like
  removed on YouTube never removes a local like, because the local library is
  the user's own record.
- **Copied playlists.** A local playlist copied with *Copy to YouTube Music*
  gets a private playlist on the account (`playlist/create`, then
  `browse/edit_playlist`). The link between the two is remembered locally, so
  every later sync appends only the tracks added since, to the same remote
  playlist, instead of creating a duplicate.
- **History.** A play is reported to the URL the `player` answer carries in
  `playbackTracking` (a GET to `s.youtube.com/api/stats/playback` with `c`,
  `cpn` and `ver=2`, the same ping the music site fires). That is what puts
  the track in the account's History; the app never reads its own history back
  from YouTube, and the ping is fire-and-forget so playback never waits on it.

The loop itself (`core/sync/YouTubeSync.kt`) runs for the life of the process,
looks at the clock every ten minutes, and syncs at most once every **thirty
minutes** (Glossy's cooldown). **Sync now** runs it on demand. A sync that
fails changes nothing locally and is simply retried; the *Last synced* line in
Settings reports the last pass that completed.

Bounds that did not change:

- Reads for personalised shelves are unchanged from the cookie decision above.
- Nothing is recorded per-user beyond what the like toggle already stores
  locally; no new data leaves the device except the calls listed here.
- Every call uses the session cookie and the same SAPISIDHASH authorization as
  the rest of Mode 2, and carries the same instability and legal position
  (above). No media, no PoToken, no attestation bypass, no ads touched.
- The infinite queue reads related tracks from YouTube Music's own `next` request (the radio / up-next list the site itself shows); it is read-only metadata, with no PoToken, cipher or additional client identity.

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

### Third-party servers and lyric sources

Two features in the app talk to hosts that are not YouTube, and neither is
part of either mode above:

- **Listen Together** connects to the app's built-in third-party relay after a
  one-time notice. The room message
  (room code, display name, current track) never includes a YouTube cookie,
  token or account. See [PRIVACY.md](PRIVACY.md).
- **Lyric lookups** ask the lyric services enabled in Settings with the track
  title and artist. The sources that reach commercial services' private web
  player endpoints (NetEase, KuGou, and Paxsenix's Apple token scrape) are off
  by default under "Additional lyric sources (third-party, unlicensed)" and
  run only when the user switches them on. Nothing from a YouTube session is
  sent to a lyric service.

These features do not add any media download path; only the explicit Mode 2
offline-download action described above may store audio in app-private storage.

### Distribution channels

The full build — including the unofficial InnerTube source described on this
page — is distributed through GitHub Releases and IzzyOnDroid. A separate
Google Play build **without the unofficial YouTube source** is planned; if and
when it ships, the unofficial source will not be in it, and this page will
still describe the full build only.

### Animated covers (Reel)

The Reel can loop a short video behind a track. Covers are **streamed and never
saved**: no cache, no download, no disk write, and the player is muted with its
audio track type disabled so it can never interrupt the music. Sources are the
user's own picked files and, in the full (foss) build only, YouTube Music if and
when the data the app already fetches carries an animated cover field — today it
does not, so the YouTube provider returns nothing and the generated cover shows.
The Google Play build ships an always-null stub: no animated-cover URL parsing
and no YouTube video loading exist in that APK. This adds no new endpoint, no
client identity and no media handling beyond what the policy above already
forbids and still does.

### Attribution

The protocol details were learned from GPL-3.0 projects in this space —
[Metrolist](https://github.com/MetrolistGroup/Metrolist), InnerTune/OuterTune and
its fork [Glossy](https://github.com/izybro110-gif/Glossy). The client identities
in the playback chain (names, versions, user agents and device fields) are the
ones those projects ship, since they are what YouTube currently accepts; the
fallback order, the ranged-GET validation and every line of `InnerTubeClient.kt`
are Spartial Music's own. No file from those projects is copied into Spartial Music. See
[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
