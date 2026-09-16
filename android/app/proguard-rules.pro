# R8 is on for release. Keep app types readable; strip unused library code.
-keep class com.clintmaples.broadcastifyscanner.** { *; }

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlinx.coroutines.**
