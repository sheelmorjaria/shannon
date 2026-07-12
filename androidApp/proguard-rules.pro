# Shannon Messenger - ProGuard/R8 Configuration
# Ensures Reticulum cryptography and serialization survive code shrinking

# Keep Reticulum cryptographic classes
-keep class network.reticulum.** { *; }
-keep class network.lxmf.** { *; }

# Keep SQLDelight generated classes
-keep class com.shannon.db.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*
-keepclassmembers class * {
    @kotlinx.serialization.SerialName ** <fields>;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable **;
}

# Keep native method calls (for audio/Reticulum)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enum classes
-keepclassmembers enum com.shannon.** {
    **[] $VALUES;
    public *;
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep reflection-based serialization
-keepclassmembers class * {
    public <init>(...);
}

# Keep Reticulum identity and key material
-keep class network.reticulum.identity.** { *; }
-keep class network.reticulum.cryptography.** { *; }

# Preserve line numbers for debugging (remove in production release)
-keepattributes SourceFile,LineNumberTable

# Remove logging in production builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}