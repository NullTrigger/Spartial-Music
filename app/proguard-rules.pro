# Copyright (C) 2026 The Nodes Authors.
# Licensed under GPL-3.0-or-later. See LICENSE.

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.nodes.** {
    *** Companion;
}
-keepclasseswithmembers class app.nodes.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.nodes.**$$serializer { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Media3
-dontwarn androidx.media3.**

# Room
-keep class androidx.room.** { *; }

# YouTube IFrame player wrapper (WebView bridge)
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.** { *; }
