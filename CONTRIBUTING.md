# Contributing to Nodes

Thanks for wanting to help. Nodes is GPL-3.0-or-later; by contributing you agree
that your work is licensed under the same terms.

## Ground rules

1. **Legal sources only.** No scraping, no stream extraction, no unofficial
   APIs, no downloading YouTube media, no background audio for YouTube content.
   PRs that add any of these are closed without discussion. See
   [YOUTUBE.md](YOUTUBE.md).
2. **No copyrighted assets.** No album covers, artist photos, logos or bundled
   tracks. Use the generated placeholder artwork or CC0 assets you can prove the
   licence of.
3. **Keep the notices.** New files start with:

   ```kotlin
   /*
    * Copyright (C) 2026 <Your Name>.
    * Licensed under GPL-3.0-or-later. See LICENSE.
    */
   ```

4. **No telemetry.** Do not add analytics, crash reporting or advertising SDKs.
   [PRIVACY.md](PRIVACY.md) is a promise to users.

## Getting set up

```bash
cp local.properties.example local.properties   # SDK path + optional API keys
./gradlew :app:assembleDebug            # both gms and foss debug APKs
./gradlew :app:testGmsDebugUnitTest :app:testFossDebugUnitTest
./gradlew :app:lintGmsDebug             # lintFossDebug for the other flavour
```

`local.properties` is git-ignored. Never paste API keys into an issue or a commit.

## What to work on

- Issues labelled `good first issue` are scoped for newcomers.
- For anything sizeable, open an issue first so we agree on the approach.
- Roadmap items live in [README.md](README.md#roadmap).

## Code style

- Kotlin, 4-space indent, trailing commas, no wildcard imports.
- Compose: small, stateless composables; hoist state; `Modifier` as the first
  optional parameter.
- Providers implement `MusicSource` and never talk to each other.
- Repository/ViewModel work stays off the main thread; UI state is immutable.
- Prefer `SourceResult` over exceptions across the source boundary.
- New strings go in `res/values/strings.xml` (no hardcoded user-facing text in
  new code).

## Commits and PRs

- One logical change per commit; imperative subject ("Add queue reordering").
- Describe *why* in the body, and note any manual setup a reviewer needs.
- PRs must pass CI: assemble, unit tests and lint.
- Attach a screenshot or short recording for UI changes.
- Do not bump dependency versions in unrelated PRs.

## Adding a music source

1. Implement `MusicSource` in `core/source/<provider>/`.
2. Add an id to `SourceId`, register it in `SourceModule`.
3. Document its licence situation in `THIRD_PARTY_LICENSES.md` and, if it needs
   keys, in the README.
4. Never return a direct audio URL if the provider's terms forbid it - return
   `PlaybackHandle.EmbeddedVideo` or `PlaybackHandle.Unavailable` instead.

## Reporting bugs

Use the issue template and include: app version, Android version, source
(local/Jamendo/YouTube), steps to reproduce, and `adb logcat` if the app crashes.
Redact API keys and tokens.

## Code of conduct

Participation is covered by [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
