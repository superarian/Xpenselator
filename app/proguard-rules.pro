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

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ==========================================
# XpenseLator Security & Keep Rules
# ==========================================

# --- FIREBASE (THE CONTRACTOR) ---
# Prevents the shredder from renaming Firebase communication lines
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keepnames class com.google.firebase.** { *; }

# --- ROOM DATABASE (LOCAL STORAGE) ---
# Ensures your offline expenses aren't corrupted when reading/writing
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.Query *;
    @androidx.room.Insert *;
    @androidx.room.Delete *;
    @androidx.room.Update *;
}

# --- KOTLIN COROUTINES ---
# Keeps background tasks (like checking Firebase) from crashing
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}