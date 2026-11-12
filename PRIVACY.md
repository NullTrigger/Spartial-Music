# Privacy

Spartial Music is a local-first music player. There is no Spartial Music account,
no analytics, no remote crash reporting, no advertising SDK and no tracking of
any kind.

The one exception is **Sessions**: shared listening needs a relay, because two
phones have no other way to agree on what is playing and where in the track they
are. It is opt-in, off until you create or join a room, and it is described in
full below rather than buried.

Last updated: 2026-11-12.

## What stays on your device

| Data | Where | Leaves the device? |
| --- | --- | --- |
| Liked songs, playlists, play history | Room database (`nodes.db`) | No |
| Search history | DataStore (`nodes_settings`) | No |
| Settings, chosen source, quota counter | DataStore | No |
| Cached provider responses | Room (`remote_cache`) | No |
| Lyrics (`files/lyrics/*.lrc`) | App-private files | No |
| Google account email (if you sign in) | DataStore | No |
| OAuth access token | Memory only, for the session | No |
| YouTube Music session cookie (if you sign in to it) | `EncryptedSharedPreferences`, key in the Android Keystore | Only to `music.youtube.com` |
| Audio files on your device | Never copied or uploaded | No |

Android's own backup may include the app database if you have device backup
enabled; that is your OS-level choice, and cached responses are excluded.

## Network requests Nodes makes

Nodes only talks to a provider when you browse or play something:

1. **YouTube Data API v3** (`googleapis.com`) — search, charts, playlists.
   Requests include your API key (yours, from `local.properties`) and, if you
   signed in, an OAuth bearer token. Google's handling is described by the
   [Google Privacy Policy](https://policies.google.com/privacy) and the
   [YouTube API Services Terms](https://developers.google.com/youtube/terms/api-services-terms-of-service).
2. **YouTube embedded player** (`youtube.com`, `ytimg.com`) — when you play a
   YouTube track. This is Google's player, running in a WebView; it may set
   cookies and collect playback data under Google's policies.
3. **Jamendo** (`api.jamendo.com`) — Creative Commons catalogue search and
   streaming, including artwork and audio URLs.
4. **Artwork CDNs** — cover images are fetched by Coil from the URL the provider
   returned.
5. **Lyrics providers** (`music.163.com`, `lrclib.net`,
   `api-lyrics.simpmusic.org`, `api.lyrics.ovh`) — when a track starts and no
   local `.lrc` file exists, Nodes asks these services in the order chosen in
   Settings. A lookup contains the track title, artist, album and duration (and
   the YouTube video id for SimpMusic). No account cookie, email or listening
   history is sent. See [docs/LYRICS.md](docs/LYRICS.md).
6. **Session relay** (the room server shown on the Sessions screen; by default
   a Cloudflare Worker) — **only while you are hosting or listening in a room.**
   A room message carries the room code, the display name you typed, and for the
   host the track the room is playing (title, artist, album, duration, artwork
   URL and, for YouTube Music tracks, the video id) plus a position in the song.
   It carries no account cookie, no OAuth token, no e-mail, no device
   identifier and no play history, and it sends nothing about tracks you never
   start. The relay holds room state only for as long as the room has members;
   there is no user account to build a profile against and nothing is written to
   a database you could later be identified from. Leaving a session stops all of
   it, and the rest of the app never touches this server.

The unofficial YouTube Music source (Settings → YouTube Music) is on by default
in this build. It calls YouTube's private `youtubei` endpoints on
`music.youtube.com` for home, search, playlists and audio URLs. Signed out, those
requests are anonymous: no account cookie, no OAuth token, no identifier beyond
what any request to youtube.com carries (your IP address and the request itself).

Signing in to YouTube Music (Settings → YouTube Music account) opens Google's own
login page in a WebView and keeps the resulting site cookie, which is what the
music site itself uses. The cookie is stored encrypted, is sent only to
`music.youtube.com` with the `SAPISIDHASH` header the site expects, and is never
logged, backed up or shared with any other host. This mode is outside the YouTube
API Services Terms — see YOUTUBE.md.

Nodes sends no identifiers of its own, no device fingerprint, no advertising ID,
and no usage events. Uninstalling the app removes all local data.

## Permissions and why

| Permission | Why |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | stream and load metadata |
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` (≤32) | list audio already on the device |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | keep playback alive with a media notification |
| `POST_NOTIFICATIONS` | show that media notification |
| `WAKE_LOCK` | keep playing with the screen off |

Declining audio permission does not break the app: local browsing is disabled and
remote sources still work.

## Your controls

- **Settings → Cache → Clear cached responses** deletes cached provider payloads
  and search history.
- **Settings → YouTube → Sign out** drops the in-memory token and forgets the
  email.
- **Settings → YouTube Music account → Sign out of YouTube Music** deletes the
  stored cookie and clears the WebView cookies, so the next request is anonymous.
- Uninstalling, or clearing app data in Android settings, deletes everything.

## Children

Nodes is not directed at children and collects no personal data.

## Changes

Material changes to this policy will be made in this file in the repository, with
the date above updated.

## Contact

Open an issue in the repository, or use the security contact in
[SECURITY.md](SECURITY.md) for anything sensitive.
