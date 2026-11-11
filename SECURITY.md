# Security policy

## Supported versions

Spartial Music is pre-1.0. Only the latest commit on `main` receives fixes.

| Version | Supported |
| --- | --- |
| `main` (0.1.x) | ✅ |
| Older tags | ❌ |

## Reporting a vulnerability

Please report privately, not in a public issue:

1. Prefer GitHub's **Security → Report a vulnerability** on the repository.
2. If that is unavailable, email the maintainer address published on the
   repository profile with `[NODES SECURITY]` in the subject.

Include:

- affected version/commit,
- Android version and device,
- reproduction steps or a proof of concept,
- impact assessment (what an attacker gains),
- whether the issue is already public.

You can expect an acknowledgement within 72 hours and a status update within
7 days. Please give us 90 days before public disclosure, or less if we agree on a
faster timeline.

## In scope

- Leaked credentials or keys committed to the repository.
- Injection or path traversal in provider parsing, lyrics loading or Room queries.
- WebView / YouTube IFrame player misconfiguration (e.g. JavaScript bridges that
  expose app data).
- Local data exposure through exported components, backups or logs.
- Anything that bypasses the "legal sources only" boundary, such as code that
  extracts stream URLs.

## Out of scope

- Missing API keys in your own build, quota errors, or Google account setup.
- Issues that require a rooted device or physical access with unlocked
  bootloader.
- Denial of service by the upstream providers.
- Requests to add scraping or downloading of YouTube content — these are
  intentional non-features, see [YOUTUBE.md](YOUTUBE.md).

## Handling of secrets

Nodes never ships API keys. Keys are read at build time from `local.properties`
(git-ignored) or the environment. If you find a key in the repository or in build
output, treat it as compromised: revoke it in Google Cloud Console / the Jamendo
portal and open a private report.

OAuth tokens are held in memory only. If a token leaks through a crash report or
a log, revoke it at <https://myaccount.google.com/permissions>.
