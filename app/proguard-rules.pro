# kotlinx.serialization
-keepclasseswithmembers class * {
    @kotlinx.serialization.* <methods>;
}
-keep class **$$serializer { *; }

# Navigation Compose type-safe args: enum route arguments are resolved via
# Class.forName(serialName) at runtime, so their original name must survive R8.
-keep class com.kshavrin.mymoney.core.ui.navigation.** extends java.lang.Enum { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Hilt-generated
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory
-keep class hilt_aggregated_deps.** { *; }

# Sentry
-keep class io.sentry.** { *; }

# Dropbox SDK reflectively loads response classes
-keep class com.dropbox.core.** { *; }

# Google API client uses GenericData reflection
-keep class com.google.api.client.** { *; }

# Compose stability
-dontwarn androidx.compose.runtime.**

# Optional transitive APIs not exercised by the app
-dontwarn com.google.appengine.api.urlfetch.**
-dontwarn com.squareup.okhttp.**
-dontwarn jakarta.servlet.http.HttpSession
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
