# R8 full mode is enabled in gradle.properties.
# kotlinx.serialization, Retrofit, OkHttp, Room, Hilt, Tink and AppAuth ship their own
# consumer rules; only app-specific keeps belong here.

# Strip verbose/debug logging from release builds (defence in depth: never log secrets).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Type-safe navigation routes are serialized by name.
-keep @kotlinx.serialization.Serializable class io.github.tb18003.musicmania.**.navigation.** { *; }

# Retrofit service interfaces with suspend functions (generic signatures needed at runtime).
-keep,allowobfuscation,allowshrinking interface io.github.tb18003.musicmania.core.network.SpotifyApi
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
