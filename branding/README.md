<!--
  Copyright (C) 2026 The Nodes Authors.
  Licensed under GPL-3.0-or-later. See LICENSE.
-->

# Spartial Music branding

The official Spartial Music mark is a white waveform — four rounded bars — on
near-black. It replaces the old "N" mark that lived in this folder before
November 2026.

## Files

| File | Use |
| --- | --- |
| `spartial-logo.png` | 512×512 square logo, used by the README and anywhere a self-contained mark is needed |
| `spartial-logo.svg` | the same 512×512 logo wrapped in SVG (the artwork is embedded as a raster image) |
| `spartial-mark-transparent.png` | the mark alone on a transparent background, cropped from the supplied master |
| `spartial-wordmark.png` | mark + "Spartial Music" wordmark, 760×180 |
| `spartial-favicon.png` | 64×64 favicon |
| `favicon.svg` | 64×64 SVG favicon with the raster artwork embedded |
| `telegram-banner.png` | 1200×260 community banner used at the top of the README |
| `../app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` | Android adaptive-icon foreground (transparent, 432×432) |
| `../app/src/main/res/drawable-nodpi/ic_launcher_monochrome.png` | Android themed-icon layer (transparent, 432×432) |
| `../app/src/main/res/drawable-nodpi/ic_spartial_mark.png` | splash screen and onboarding mark (transparent, 512×512) |
| `../fastlane/metadata/android/en-US/images/icon.png` | 512×512 Play/F-Droid store icon (opaque) |

The launcher background colour is `ic_launcher_background` in
`app/src/main/res/values/colors.xml` (`#050506`). The adaptive icon XML files in
`mipmap-anydpi-v26/` point at the foreground and monochrome layers.

## Provenance and licence

The waveform mark was supplied to the project by the maintainer in November 2026
as the official Spartial Music logo. The exact tool used to create it was not
recorded and may have been an AI image generator. See [NOTICE.md](../NOTICE.md).

The artwork is distributed with the project under GPL-3.0-or-later. If you reuse
the mark outside Spartial Music, check its provenance first. The name and mark
identify this project; forks should use their own branding.

## Regenerating the Android assets

Start from the square master artwork (the supplied image, centred on black) and
scale the mark into the safe areas:

- adaptive foreground/monochrome: 432×432, mark ink 184 px wide so its square
  corners sit inside the 66 dp launcher safe circle;
- splash/onboarding: 512×512 transparent, mark ink 236 px so it sits inside the
  circle Android 12 uses as a splash mask;
- store icon: 512×512 opaque on `#050506`, mark ink 430 px (8% padding).

Do not trace or redraw the mark by hand; keep the supplied curves.
