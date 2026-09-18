# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified in the
# Android SDK's proguard-android-optimize.txt

# Keep MediaCodec and MediaExtractor names (Android media framework)
-keep class android.media.** { *; }

# FFmpeg-Kit — keep all native bindings
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep data classes for domain models (Gson/serialization safety)
-keepclassmembers class dev.vimal.utl.core.domain.model.** {
    *;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
