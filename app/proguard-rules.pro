# kotlinx.serialization
-keepclasseswithmembers class * {
    @kotlinx.serialization.* <methods>;
}
-keep class **$$serializer { *; }

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
