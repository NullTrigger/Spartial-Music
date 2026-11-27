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
- Glossy's queue model: **Play next** inserts after the playing track, **Add to
  queue** appends, and the queue is no longer rebuilt from the list a track came
  from; the optional duplicate guard drops the older copy of a track instead of
  queueing it twice.
- The queue continues from the playing track's own up-next radio when it runs
  low, instead of from a stale related list.
- Home "From your searches" turns each recent search into an artist, album or
  track suggestion, ranked the way Glossy ranks its search suggestions.
- Lyrics are fetched from up to three sources at once and the first synced
  answer wins within a short window; the next track's lyrics are fetched in the
  background so they are ready when it starts.
- Settings → Content: **Auto-sync with account** (off by default) with **Sync
  now**, keeping the signed-in account's likes, listening history and copied
  playlists in step at most every 30 minutes.

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
- Autoplay continuation ("keep the queue going") is **on by default**; it can
  still be switched off in Playback settings.
- Unlike Glossy, the account sync starts off: every part of it writes to a real
  account, so it waits for the switch.

### Fixed

- Home's banner led with the provider's front page, so a promotional video row
  filled a third of the screen with the song's name burnt into the artwork. The
  carousel now starts from the artists this device listens to, then recent
  plays, and only pads from the feed.
- "From your searches" answered the query instead of continuing it: searching
  "one love" filled the row with songs called One Love. It is now the radio
  around the best match, minus every row that answers the query by name.
- The infinite queue mixed the home feed's first rows into each batch, which is
  how unrelated trending and devotional videos ended up after one Punjabi song.
  Queue growth is the playing song's own radio only - no home-feed picks, no
  song name twice, no artist the queue just played, one song per artist and per
  album in a batch.
- "Up next" now asks YouTube Music for the song's own autoplay radio
  (`RDAMVM…`) rather than a bare video id, which could answer with the watch
  page's related-video layout and parse to nothing.

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
