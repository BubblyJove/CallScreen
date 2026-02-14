# Perf: enable aggressive R8 optimizations
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# Perf: strip verbose/debug logging in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
}
# Perf: strip ScreenLog debug methods in release
-assumenosideeffects class com.callscreen.app.util.ScreenLog {
    public static void d(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep CallScreeningService (registered in manifest)
-keep class com.callscreen.app.screening.AppCallScreeningService { *; }

# Dagger 2
-dontwarn com.google.errorprone.annotations.**
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.internal.Binding
-keep class * extends dagger.internal.ModuleAdapter
-keep @dagger.Module class *
-keep @dagger.Component class * { *; }
-keep @dagger.Subcomponent class * { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}
# Perf: keep Dagger-generated factory/injector classes
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }

# Realm
-keep class io.realm.annotations.RealmModule
-keep @io.realm.annotations.RealmModule class *
-keep class io.realm.internal.Keep
-keep @io.realm.internal.Keep class *
-dontwarn io.realm.**
# Perf: keep Realm model proxy classes
-keep class * extends io.realm.RealmObject { *; }
-keep class * implements io.realm.RealmModel { *; }

# Conductor
-keep class com.bluelinelabs.conductor.** { *; }
# Perf: keep Controller constructors for Conductor's reflective instantiation
-keepclassmembers class * extends com.bluelinelabs.conductor.Controller {
    public <init>(android.os.Bundle);
}

# RxJava 2
-dontwarn io.reactivex.**
-keep class io.reactivex.plugins.RxJavaPlugins { *; }

# AutoDispose
-dontwarn com.uber.autodispose.**

# Moshi
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclassmembers @com.squareup.moshi.JsonClass class * extends java.lang.Enum {
    <fields>;
}
-keep class **JsonAdapter { *; }
-keep class com.squareup.moshi.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Web3j removed — crypto challenges use raw JSON-RPC via OkHttp
# -dontwarn org.web3j.**
# -keep class org.web3j.abi.** { *; }
# -keep class org.web3j.protocol.** { *; }

# ezvcard
-dontwarn ezvcard.**
-keep class ezvcard.** { *; }

# libphonenumber
-keep class io.michaelrocks.libphonenumber.** { *; }
-dontwarn io.michaelrocks.libphonenumber.**

# Broadcast receivers (must keep for manifest registration)
-keep class * extends android.content.BroadcastReceiver { *; }

# WorkManager workers (instantiated reflectively)
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# SLF4J (referenced by some libs but not shipped at runtime)
-dontwarn org.slf4j.**

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
