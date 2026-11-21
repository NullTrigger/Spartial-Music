# AGENTS.md

Instructions for AI coding agents (and humans who like explicit rules) working in
this repository. Read this before your first edit.

## What this project is

**Spartial Music** is an open-source Android music player: Kotlin, Jetpack Compose,
Material 3, Media3, Room, Hilt, Retrofit, kotlinx.serialization, Coil. minSdk 26,
targetSdk 36. Package stays `app.nodes` for upgrade compatibility. Licence
GPL-3.0-or-later.

The defining idea is the `MusicSource` interface: every provider of music is a
plugin behind one contract, so the player never knows where a track came from.

## Non-negotiable rules

1. **YouTube has exactly two modes.** Mode 1 is the official Data API v3 plus
   the embedded player. Mode 2 is the unofficial InnerTube client in
   `core/source/youtubemusic/`, which the maintainer added deliberately. Since
   31 October 2026 Mode 2 is the default source by maintainer decision (recorded
   in `YOUTUBE.md`), because Mode 1 cannot show a real music catalogue; Mode 1
   stays registered and selectable, and reversing the decision is a matter of
   flipping the defaults back. Mode 1 never downloads or caches media. In Mode 2,
   local app-private offline caching and user-initiated downloads via AndroidX
   Media3 (`SimpleCache` / `DownloadManager` inside `context.filesDir`) are
   permitted by maintainer decision recorded in `YOUTUBE.md`. In either mode:
   never export raw audio files to public external storage, never re-encode
   YouTube media, never skip ads, never bundle a PoToken or attestation bypass,
   and never harvest account cookies without a maintainer decision recorded in
   `YOUTUBE.md`. Mode 2 is outside the YouTube API Services Terms and can break
   at any time — say so anywhere it is exposed. If a task needs a third
   mechanism, stop and ask the maintainer.
2. **No third-party content.** No real artist names, album art, lyrics, audio or
   logos. Placeholder art is generated procedurally; sample data is fictional
   (`DemoCatalog.kt`).
3. **No secrets in the repo.** Keys go in git-ignored `local.properties` /
   `keystore.properties` or CI secrets. Never print, log or commit a key. Never
   ask a user to paste a key into a chat.
4. **Ask before licensing or legal decisions.** Changing the licence, adding a
   dependency with a non-permissive licence, or crediting borrowed code are all
   decisions for the maintainer.
5. **Licence headers.** Every new source file starts with:

   ```kotlin
   /*
    * Copyright (C) 2026 The Spartial Music Authors.
    * Licensed under GPL-3.0-or-later. See LICENSE.
    */
