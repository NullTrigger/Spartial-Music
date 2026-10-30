# Third-party licences

Every runtime dependency of Nodes, with its licence. Versions are pinned in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml); update this file when you
add or bump a dependency.

> All listed components are compatible with distributing Nodes under
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
| Jamendo API | [Jamendo terms](https://developer.jamendo.com/v3.0), per-track Creative Commons licence |

## Fonts and images

Nodes bundles **no** fonts and **no** raster artwork. Typography uses the
platform's sans and serif families; cover art is either fetched from the provider
or generated procedurally; the logo, wordmark and favicon in `branding/` were
drawn for this project (GPL-3.0-or-later).

## Credits

Thanks to the Android open-source community, and in particular to the projects
that made modern Compose media apps practical: AndroidX Media3, Compose, Hilt,
Coil, Retrofit and kotlinx.serialization.

This project does not contain code from Metrolist, InnerTune, OuterTune or
Glossy. If you build on one of those GPL-3.0 projects instead, keep their
copyright headers and add them to this file.
