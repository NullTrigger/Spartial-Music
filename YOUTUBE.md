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

## Mode 1 — Official (the only mode shipped)

| Feature | API | Cost |
| --- | --- | --- |
| Search music | `search.list` (`type=video`, `videoCategoryId=10`) | **100 units** per call |
| Trending / Home chart | `videos.list` (`chart=mostPopular`, `videoCategoryId=10`) | 1 unit |
| Curated playlists | `playlists.list` (`chart=mostPopular`) | 1 unit |
| Playlist contents | `playlistItems.list` | 1 unit |
| Your playlists (signed in) | `playlists.list` (`mine=true`) | 1 unit |
| Playback | Official embedded IFrame player | no quota |

The default daily allowance is **10,000 units**, reset at midnight Pacific Time.
Nodes counts units locally (`QuotaTracker`), caches responses for 15 minutes
(`RemoteCache`), and when the budget is gone it stops calling the API and shows:

> "YouTube API quota for today is used up. It resets at midnight Pacific Time.
> Cached and local content still works."

That is a 100-unit search budget of roughly 100 searches a day. Browsing Home
costs about 3 units.

### What Nodes deliberately does not do

- No stream URL extraction. YouTube tracks resolve to an `EmbeddedVideo` handle.
- No downloading, caching or re-encoding of YouTube media.
- No background or audio-only playback of YouTube content. The player is always
  rendered on the Now Playing screen and pauses when that screen is disposed.
- No ad blocking, no sponsor blocking, no cipher or PoToken bypass.
- No unofficial endpoints. Under this project's clean-room path there is **no**
  InnerTube module, no experimental toggle, and no scraping code to enable.

These are product and legal boundaries, not missing features. Pull requests that
add any of them will be closed.

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

Sign-in only adds your own playlists. Nodes works fully signed out.

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

## Attribution

Nodes shows, in Settings and in the README:

- "Powered by YouTube"
- links to the YouTube Terms of Service and Google Privacy Policy
- the statement that Nodes is not affiliated with YouTube or Google

If you fork and ship this app, keep that attribution. It is part of the API
Services Terms, not decoration.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| "Add a YouTube Data API key…" | `YOUTUBE_API_KEY` empty at build time | add the key, rebuild (Gradle caches `BuildConfig`) |
| Quota exceeded on first launch | key shared with another app, or quota already spent | wait for the Pacific midnight reset or use another project |
| `keyInvalid` / 400 | key restricted to a different package or API | fix API/application restrictions |
| Sign-in error 10 | SHA-1/package mismatch | register the debug SHA-1 too |
| Video will not play | embedding disabled by the uploader | expected; Nodes will not work around it |
| Nothing renders in the player card | WebView disabled or no network | check WebView updates/connection |
