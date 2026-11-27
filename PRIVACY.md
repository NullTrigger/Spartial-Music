# Privacy

**Last Updated: November 2026**

Spartial Music is a free, open-source Android media client. The maintainers do
not collect personal information through the app. It has no Spartial account
system, advertising, analytics, ad-ID collection, or remote crash-reporting
service. The app has no developer-operated backend. Most library and settings
data is stored on the device. The optional Listen Together feature is an exception:
it connects to a fixed relay operated by a third party, and only after you
accept its one-time notice.

This policy describes the app's current behavior. It does not replace the privacy
policies or terms of the third-party services you choose to use.

## Data stored on the device

The app stores its Room database and DataStore preferences in Android's
app-specific storage. These may contain playlists, liked tracks, play history,
search history, profile name and avatar selection, playback settings, and cached
catalogue metadata. Fetched lyrics are cached under the app's private files
(directory `files/lyrics_cache`); user-provided lyric files and video-loop covers
are also stored in app-private files. Artwork and thumbnails use Coil's app
cache. The YouTube Music playback URL cache is held in memory only. Media3's
download index and downloaded media are kept in app-private storage and excluded
from Android backup and device transfer.

Mode 2 supports user-initiated offline downloads kept in app-private storage.
Mode 1 media is never downloaded. The app does not export downloaded audio to
shared storage. It plays streamed media from its provider and reads local audio
files already on your device. Catalogue metadata, lyrics, artwork, and
user-selected cover loops
are not offline audio copies. The app does not upload media or library data to a
Spartial Music server, and it does not run a content-hosting server.

Crash diagnostics are local by default and can be disabled in Settings. Crash
reports are kept in the app-specific `Android/data/<package>/files/crash_logs`
directory (with an internal-storage fallback); the app log is also stored in
app-specific storage. Reports are not sent automatically. They may contain
technical details about an error; review a report before sharing it. If you
choose to share one, the Android share sheet sends it to the destination you
select. If you choose **Save to Downloads**, a text report is explicitly copied
to the shared Downloads folder. These actions apply only to diagnostic text,
never to audio.

## Network connections and third-party services

The app makes network requests when you use the related feature. The following
services are present in the app code:

- **Google / YouTube official services.** The official YouTube Data API v3 uses
  `www.googleapis.com` for search and catalogue requests when that source is
  configured. Official embedded playback loads YouTube's player and related
  YouTube resources in a WebView. Optional Google Sign-In is present only in
the `gms` build and requests the YouTube read-only scope; its short-lived OAuth
token is held in memory and sent to Google's API, not persisted by Spartial
Music. The account email is stored locally in app preferences. Google's handling
is covered by its own
  [Privacy Policy](https://policies.google.com/privacy) and YouTube terms.
- **YouTube Music (unofficial InnerTube client).** This source contacts
  `music.youtube.com` for catalogue, account and playback-resolution requests.
  Streamed playback and images may also contact the media or image hosts returned
  by YouTube. When signed out, requests do not include the user's account cookie.
  This unofficial integration is not covered by the YouTube API Services Terms;
  see [YOUTUBE.md](YOUTUBE.md). If you explicitly save a Mode 2 track offline,
  the app resolves a stream and downloads it directly from the returned media
  host into app-private Media3 storage. Ads are not skipped and media is not
  re-encoded.
- **Google Account / YouTube Music sign-in.** If you choose to sign in to the
  unofficial YouTube Music source, a Google login page is opened in a WebView.
  After validation, the resulting YouTube Music site cookie is stored in
  encrypted preferences using a key protected by Android Keystore. It is sent
  to `music.youtube.com` for that unofficial service's requests; it is not sent
  to a Spartial Music server or included in backups. Signing out removes the
  stored session and WebView cookies. This differs from the official API OAuth
  flow above; the unofficial InnerTube requests are not official YouTube API
  calls.
- **Artwork and media CDNs.** Artwork URLs returned by a provider are loaded by
  Coil. Audio streams are played directly from the provider's returned stream
  URL. The app does not operate a proxy or relay for these requests.
- **Lyrics services.** When lyrics are requested, enabled providers can receive
  the track title, artist, album, duration, and, for some providers, the
  YouTube Music video ID. The built-in providers include LRCLIB (`lrclib.net`),
  SimpMusic (`api-lyrics.simpmusic.org`), NetEase (`music.163.com`), KuGou
  (`mobileservice.kugou.com` / `lyrics.kugou.com`), Lyrics.ovh (`api.lyrics.ovh`),
  BetterLyrics (`lyrics-api.boidu.dev`), LyricsPlus community mirrors, and
  Paxsenix (`lyrics.paxsenix.org`); YouTube captions may also be requested from
  YouTube. Local lyric files are checked on-device. NetEase, KuGou, and Paxsenix
  are grouped as additional third-party sources and are off by default; enabling
  one in Settings permits its requests. Paxsenix also reads a public token from
  Apple's public Music web-player page and uses its lyrics service; no Apple
  account is used. No app account cookie or listening history is sent to lyrics
  providers. See [docs/LYRICS.md](docs/LYRICS.md) for details.
- **Listen Together relay.** Shared listening is optional and does not connect
  until you accept its one-time notice. Every session uses the same third-party
  relay address built into the app, so both ends of a room meet on one server.
  While connected, that relay receives the room code, the nickname you enter,
  and session events. Playback events can include the current track's title,
  artist, album, duration, artwork URL and YouTube video ID, plus playback
  position/state. The audio itself, account credentials, library, search
  history, and general play history are not sent. The built-in relay uses
  `wss://`, so session data is encrypted in transit. The relay is not operated
  by Spartial Music; its own operator's retention and privacy practices apply.
  Leaving the room closes the connection.
- **GitHub.** The app links to GitHub-hosted source, documentation, and releases.
  If you tap **Check for updates** in About, the app requests the latest public
  release metadata from `api.github.com` and selects the APK matching the
  installed `gms` or `foss` build. Tapping the update link opens GitHub's APK
  asset in your browser; Android handles installation. No periodic update check
  or usage report runs in the background.

The app includes no Firebase Crashlytics, Sentry, analytics, advertising, or
ad-ID SDK. There is no automatic telemetry or automatic crash upload.

## Backups and deletion

Android backup is enabled for the app's Room database. That database includes
both library tables and the `remote_cache` catalogue-response table, so Android
backup or device transfer may copy that metadata when enabled in your device
settings. The app's explicit backup rules do not include app-private files or
DataStore preferences. The separate encrypted YouTube Music session preferences
are explicitly excluded. Android's backup/transfer service is controlled by
your OS and account settings, not by a Spartial Music server.

Settings → Data → Backup lets you export a JSON library backup using Android's
document picker. It contains library information (including playlists, liked
tracks, and optionally play history) and selected non-secret profile settings.
It does not include audio, cached media files, API keys, OAuth tokens, or the
YouTube Music session cookie. This user-selected export is separate from
Android's automatic app-data backup. The file is saved to the location you choose; if you
choose a cloud-backed document provider, that provider may sync it under its own
terms. Import reads and merges the selected file locally.

You can delete selected disposable data in Settings → Data: artwork/thumbnails,
fetched lyrics, catalogue responses, search history, or all caches (including
explicitly saved offline audio). Clearing all caches does not delete playlists,
likes, or library data. Use **Settings → Data → Clear search history** for
searches. Use the account controls in Settings to sign out and remove a YouTube
Music session.
Android Settings → Apps → Spartial Music → Storage → Clear storage/data removes
the app's local data. Uninstalling also removes app-specific local data; files
you explicitly exported to shared storage or a cloud-backed provider are not
removed by uninstalling.

## Permissions

The app requests Internet and network-state access for streaming and metadata,
local audio-read access to browse audio already on the device, and foreground
media playback/notification and wake-lock permissions to continue playback.
It does not request broad all-files access or permission to write streamed audio
to the public Music directory.

## Non-affiliation and trademarks

Spartial Music is not affiliated with, funded by, authorized by, endorsed by, or
in any way associated with YouTube, Google LLC, Metrolist Group LLC, or any of
their affiliates and subsidiaries. All trademarks, service marks, trade names,
product names, and intellectual property rights referenced in this project
belong to their respective owners. Mention of a service or project does not
imply endorsement or affiliation.

## Contact and policy changes

For privacy or general questions, use the repository's public GitHub issue
tracker. Do not post private personal information in a public issue. For
copyright concerns, use [GitHub's copyright and DMCA removal process](https://docs.github.com/en/site-policy/content-removal-policies/dmca-takedown-policy).
For security vulnerabilities, follow [SECURITY.md](SECURITY.md).

Material changes to this policy will be recorded here and the date above updated.
