# Third-party licences

Every runtime dependency of Spartial Music, with its licence. Versions are pinned in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml); update this file when you
add or bump a dependency.

> All listed components are compatible with distributing Spartial Music under
> GPL-3.0-or-later. Apache-2.0 and BSD code can be combined with GPL-3.0; none of
> these libraries impose a copyleft weaker than, or incompatible with, the GPL.

## Build tooling (not distributed in the APK)

| Component | Version | Licence |
| --- | --- | --- |
| Android Gradle Plugin | 9.5.0 | Apache-2.0 |
| Gradle | 9.6.1 | Apache-2.0 |
| Kotlin Gradle Plugin | 2.2.10 (bundled with AGP) | Apache-2.0 |
| KSP (Kotlin Symbol Processing) | 2.2.10-2.0.2 | Apache-2.0 |
| Dagger Hilt Gradle plugin | 2.60.1 | Apache-2.0 |

## Runtime — AndroidX / Compose

| Component | Licence |
| --- | --- |
| androidx.core:core-ktx | Apache-2.0 |
| androidx.core:core-splashscreen | Apache-2.0 |
| androidx.activity:activity-compose | Apache-2.0 |
| androidx.lifecycle:{runtime-ktx, runtime-compose, viewmodel-compose} | Apache-2.0 |
| androidx.navigation:navigation-compose | Apache-2.0 |
| androidx.compose:compose-bom, ui, foundation, material3 | Apache-2.0 |
| androidx.compose.material:material-icons-extended | Apache-2.0 |
| androidx.media3:{exoplayer, session, common, datasource-okhttp} | Apache-2.0 |
| androidx.room:{runtime, ktx, compiler} | Apache-2.0 |
| androidx.datastore:datastore-preferences | Apache-2.0 |
| androidx.security:security-crypto (EncryptedSharedPreferences) | Apache-2.0 |
| androidx.hilt:hilt-navigation-compose | Apache-2.0 |
| androidx.annotation, androidx.collection, androidx.tracing, androidx.profileinstaller (transitive) | Apache-2.0 |

## Runtime — libraries

| Component | Version | Licence |
| --- | --- | --- |
| Dagger / Hilt (`com.google.dagger:hilt-android`) | 2.60.1 | Apache-2.0 |
| Kotlin standard library + kotlinx-coroutines | 2.2.x / 1.11.0 | Apache-2.0 |
| kotlinx-serialization-json | 1.12.0 | Apache-2.0 |
| OkHttp + logging-interceptor | 5.5.1 | Apache-2.0 |
| Retrofit + kotlinx-serialization converter | 2.11.0 | Apache-2.0 |
| Coil (`io.coil-kt:coil-compose`) | 2.7.0 | Apache-2.0 |
| Android YouTube Player (`com.pierfrancescosoffritti.androidyoutubeplayer:core`) | 12.1.2 | Apache-2.0 |
| Google Play Services Auth | 21.6.0 | Android Software Development Kit License (proprietary, redistributable in apps) |
| Google Play Services Cast Framework (`play-services-cast-framework`, gms flavour only) | 22.3.1 | Android Software Development Kit License (proprietary, redistributable in apps) |
| androidx.media3:media3-cast + androidx.mediarouter:mediarouter (gms flavour only) | 1.11.1 / 1.8.1 | Apache-2.0 |
| desugar_jdk_libs | 2.1.5 | GPL-2.0 with the Classpath Exception |

## Test-only

| Component | Licence |
| --- | --- |
| JUnit 4 | Eclipse Public License 1.0 |
| androidx.test.ext:junit, espresso-core | Apache-2.0 |
| kotlinx-coroutines-test | Apache-2.0 |

## Services (not bundled)

| Service | Terms |
| --- | --- |
| YouTube Data API v3 | [YouTube API Services Terms](https://developers.google.com/youtube/terms/api-services-terms-of-service), [Google Privacy Policy](https://policies.google.com/privacy) |
| YouTube InnerTube (`youtubei`), unofficial and opt-in | Not covered by the YouTube API Services Terms. See [YOUTUBE.md](YOUTUBE.md) |
| Jamendo API | [Jamendo terms](https://developer.jamendo.com/v3.0), per-track Creative Commons licence |

## Fonts and images

Spartial Music bundles **no** third-party fonts or artwork. Typography uses the
platform's sans and serif families; cover art is either fetched from the provider
or generated procedurally. The app icon and the logo, wordmark and banner in
`branding/` are the project's own branding, supplied for Spartial Music and
distributed under GPL-3.0-or-later; see [NOTICE.md](NOTICE.md) and
[branding/README.md](branding/README.md).

## Credits

Thanks to the Android open-source community, and in particular to the projects
that made modern Compose media apps practical: AndroidX Media3, Compose, Hilt,
Coil, Retrofit and kotlinx.serialization.

Spartial Music also implements YouTube's private InnerTube protocol in
`core/source/youtubemusic/`. That client was written for Spartial Music; the
protocol details — client names and versions, context fields, and the fact that
the `ANDROID_VR` client returns plain audio URLs — were learned from GPL-3.0
projects in this space, credited here:

| Project | Licence | What Spartial Music uses |
| --- | --- | --- |
| [Metrolist](https://github.com/mostafaalagamy/Metrolist) | GPL-3.0 | InnerTube protocol reference |
| InnerTune / OuterTune | GPL-3.0 | InnerTube protocol reference |
| [Glossy](https://github.com/izybro110-gif/Glossy) | GPL-3.0 | InnerTube protocol reference, WebView sign-in study (not copied, not used) |

No source file from those projects is copied into Spartial Music, so nothing here carries
their copyright headers. If you do port code from one of them, keep that file's
header, keep the GPL-3.0 licence, and add the project to this table.

InnerTube is not a licensed API. Bundling a client for it does not grant any
right to YouTube's content, and it does not put this project inside the YouTube
API Services Terms; the trade-offs are written down in [YOUTUBE.md](YOUTUBE.md).
