# Referenced by app/build.gradle.kts. Minification is currently disabled for
# release, so these are only active once isMinifyEnabled is turned on.

# Keep Compose runtime metadata and the DataStore/serialization classes it
# reflects over.
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable *;
}

-keep class com.pomo.app.model.** { *; }

# Kotlin coroutines internals referenced reflectively by the debugger agent.
-dontwarn kotlinx.coroutines.**
