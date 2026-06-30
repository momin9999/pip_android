# Media3 / ExoPlayer keeps reflection-based component discovery working.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Compose runtime metadata.
-keepclassmembers class androidx.compose.** { *; }
