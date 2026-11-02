<!--
  Copyright (C) 2026 The Nodes Authors.
  Licensed under GPL-3.0-or-later. See LICENSE.
-->

# Nodes branding

The official Nodes mark is the bold white N on near-black. It replaces the
original thin-stroke N that lived in this folder before November 2026.

## Files

| File | Use |
| --- | --- |
| `nodes-logo.png` | 512×512 squircle logo, used by the README and anywhere a self-contained mark is needed |
| `nodes-logo.svg` | the same 512×512 logo wrapped in SVG (the artwork is embedded as a raster image) |
| `favicon.svg` | 64×64 favicon, same mark |
| `nodes-wordmark.svg` | mark + "Nodes" wordmark, 720×220 |
| `../app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` | Android adaptive-icon foreground (transparent) |
| `../app/src/main/res/drawable-nodpi/ic_launcher_monochrome.png` | Android themed-icon layer (transparent) |
| `../app/src/main/res/drawable-nodpi/ic_nodes_mark.png` | splash screen and onboarding mark (transparent) |
| `../fastlane/metadata/android/en-US/images/icon.png` | 512×512 Play/F-Droid store icon (opaque) |

The launcher background colour is `ic_launcher_background` in
`app/src/main/res/values/colors.xml` (`#050506`). The adaptive icon XML files in
`mipmap-anydpi-v26/` point at the foreground and monochrome layers.

## Provenance and licence

The mark was supplied to the project by a collaborator of the maintainer in 2026
and adopted as the official Nodes logo with the maintainer's approval. The exact
tool used to create it was not recorded and may have been an AI image generator.
See [NOTICE.md](../NOTICE.md).

The artwork is distributed with the project under GPL-3.0-or-later. If you reuse
the mark outside Nodes, check its provenance first.

## Regenerating the Android assets

Start from the square master artwork (the supplied image, centred on black) and
scale the mark into the safe areas:

- adaptive foreground/monochrome: 432×432, mark ink 46 dp wide so its square
  corners sit inside the 66 dp launcher safe circle (66/√2 = 46.7 dp);
- splash/onboarding: 512×512 transparent, mark ink 236 px so it sits inside the
  circle two-thirds the width of the drawable that Android 12 uses as a splash
  mask (512 × 2/3 / √2 = 241 px);
- store icon: 512×512 opaque on `#050506`, mark ink 430 px (8% padding).

Do not trace or redraw the mark by hand; keep the supplied curves.
