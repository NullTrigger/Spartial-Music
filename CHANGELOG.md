# Changelog

All notable changes to Spartial Music are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Lyrics provider credit: the source that answered is always shown under the
  lyrics.
- "Additional lyric sources (third-party, unlicensed)" setting group for the
  NetEase, KuGou and Paxsenix sources, with a disclosure of what is sent and
  what is not licensed.
- User-imported `.txt` lyric sidecars next to `.lrc`, both tried before any
  network source.
- "Content and takedowns" link on the About screen.

### Changed

- NetEase, KuGou and Paxsenix lyrics are **off by default** and only queried
  when the user switches them on; a one-time settings migration turns them off
  on updated installs to match fresh ones. Community sources (BetterLyrics,
  LyricsPlus, LRCLIB, SimpMusic, Lyrics.ovh) and the video's own captions
  remain on.
- Lyrics sources are reordered within their group only, so the community and
  third-party lists stay separate.
- The "Show which source was used" lyric toggle is gone; the credit is always
  visible.

### Security

- Play services auth is `gmsImplementation` only; the `foss` flavour uses a
  stub and contains no Google binary.
- The encrypted YouTube Music session cookie (`innertube_session`) is excluded
  from cloud backup and device transfer.
- The login JavaScript bridge ignores calls unless the page host is
  `music.youtube.com` or `accounts.google.com`.
- `okhttp-logging` is no longer a dependency.

### Privacy

- Listen Together ships with no default server; the first connection shows a
  one-time notice of exactly what is sent and that the server is third-party.
- Library export/import: one versioned JSON file under the user's control,
  merged by id, with no credential fields.
