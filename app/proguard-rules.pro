# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# Room rules
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>(...);
}

# ExoPlayer/Media3 rules
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }

# LibVLC rules
-keep class org.videolan.libvlc.** { *; }

# Kotlin Serialization (if used)
-keepattributes *Annotation*, EnclosingMethod, InnerClasses
-keepnames class kotlinx.serialization.internal.GeneratedSerializer { *; }

# Moshi rules
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}

# Preserve line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
