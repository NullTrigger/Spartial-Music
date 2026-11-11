<!--
  Copyright (C) 2026 The Nodes Authors.
  Licensed under GPL-3.0-or-later. See LICENSE.
-->

# Store images

F-Droid and Play need raster images. The launcher icon is committed; the feature
graphic and screenshots are not, because store screenshots must show a real
running build.

| File | Size | Content |
| --- | --- | --- |
| `icon.png` | 512×512 | official Spartial Music mark on #050506 (committed) |
| `featureGraphic.png` | 1024×500 | mark + "Spartial Music" wordmark on #050506, based on `branding/spartial-logo.png` |
| `phoneScreenshots/1.png` | 1080×2400 | Home |
| `phoneScreenshots/2.png` | 1080×2400 | Now Playing |
| `phoneScreenshots/3.png` | 1080×2400 | Library |
| `phoneScreenshots/4.png` | 1080×2400 | Settings |

Rules: dark theme only, no copyrighted album art in the frame (use the generated
placeholder artwork), no YouTube logos beyond the attribution text the app itself
shows. Design previews for the README live in `docs/screenshots/`; do not copy
those into the store listing, because they are mockups rather than device
captures.
