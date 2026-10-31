# Privacy

Nodes is a local-first music player. There is no Nodes account, no Nodes server,
no analytics, no crash reporting, no advertising SDK and no tracking of any kind.

Last updated: 2026-10-30.

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

If you turn on the unofficial YouTube Music source (Settings → YouTube Music),
Nodes also calls YouTube's private `youtubei` endpoints on `youtube.com` for
search, playlists and audio URLs. Those requests are anonymous: no account
cookie, no OAuth token, no identifier beyond what any request to youtube.com
carries (your IP address and the request itself). That mode is off by default and
is outside the YouTube API Services Terms — see YOUTUBE.md.

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
- Uninstalling, or clearing app data in Android settings, deletes everything.

## Children

Nodes is not directed at children and collects no personal data.

## Changes

Material changes to this policy will be made in this file in the repository, with
the date above updated.

## Contact

Open an issue in the repository, or use the security contact in
[SECURITY.md](SECURITY.md) for anything sensitive.
