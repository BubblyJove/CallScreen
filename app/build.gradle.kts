plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

apply(plugin = "realm-android")

android {
    namespace = "com.callscreen.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.callscreen.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Perf: R8 full-mode enabled via proguard-android-optimize.txt above
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            // Perf: skip PNG crunching in debug builds
            isCrunchPngs = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            // Perf: exclude unnecessary metadata to reduce APK size
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/NOTICE.md",
                "/META-INF/LICENSE.md",
                "/META-INF/*.kotlin_module",
                "/META-INF/versions/**",
                "/kotlin/**",
                "/DebugProbesKt.bin",
                "/*.txt",
                "/*.html"
            )
        }
    }
}

dependencies {
    // Project modules
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":libs:android-smsmms"))

    // Dagger
    implementation("com.google.dagger:dagger:2.48")
    implementation("com.google.dagger:dagger-android:2.48")
    implementation("com.google.dagger:dagger-android-support:2.48")
    kapt("com.google.dagger:dagger-compiler:2.48")
    kapt("com.google.dagger:dagger-android-processor:2.48")

    // RxJava 2
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("io.reactivex.rxjava2:rxkotlin:2.4.0")

    // RxBinding
    implementation("com.jakewharton.rxbinding2:rxbinding-kotlin:2.2.0")
    implementation("com.jakewharton.rxbinding2:rxbinding-support-v4-kotlin:2.2.0")

    // RxPreferences
    implementation("com.f2prateek.rx.preferences2:rx-preferences:2.0.1")

    // AutoDispose
    implementation("com.uber.autodispose:autodispose-android-archcomponents:1.4.0")
    // Perf: moved test artifact from implementation to debugImplementation
    debugImplementation("com.uber.autodispose:autodispose-android-archcomponents-test:1.4.0")
    implementation("com.uber.autodispose:autodispose-android:1.4.0")
    implementation("com.uber.autodispose:autodispose:1.4.0")
    implementation("com.uber.autodispose:autodispose-lifecycle:1.4.0")

    // RxDogTag
    implementation("com.uber.rxdogtag:rxdogtag:1.0.0")
    implementation("com.uber.rxdogtag:rxdogtag-autodispose:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:1.7.3")

    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    // Perf: lifecycle-extensions is deprecated and pulls in all lifecycle modules;
    // use only the specific modules needed (already declared above)
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Emoji
    implementation("androidx.emoji2:emoji2:1.4.0")
    implementation("androidx.emoji2:emoji2-bundled:1.4.0")

    // Material
    implementation("com.google.android.material:material:1.11.0")

    // Conductor
    implementation("com.bluelinelabs:conductor:2.1.5")
    implementation("com.bluelinelabs:conductor-archlifecycle:2.1.5")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // PhotoView
    implementation("com.github.chrisbanes:photoview:2.1.4")

    // Flexbox
    implementation("com.google.android.flexbox:flexbox:3.0.0")

    // ExoPlayer
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")

    // Moshi
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Phone number formatting
    implementation("io.michaelrocks:libphonenumber-android:8.13.27")

    // Perf: web3j removed — crypto challenge uses raw JSON-RPC via OkHttp WebSocket.
    // web3j pulled in ~5MB of unused classes (Solidity codegen, ABI, full Ethereum client).
    // implementation("org.web3j:core:4.10.3")

    // Realm Adapters
    implementation("com.github.realm:realm-android-adapters:3.1.0")

    // ShortcutBadger
    implementation("me.leolin:ShortcutBadger:1.1.22")

    // ezvcard
    implementation("com.googlecode.ez-vcard:ez-vcard:0.10.4") {
        exclude(group = "org.jsoup", module = "jsoup")
        exclude(group = "org.freemarker", module = "freemarker")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
    }

    // Timber
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.04.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // Perf: material-icons-extended adds ~30MB debug / ~5MB release APK size.
    // TODO: Replace with XML vector drawables for the ~15 icons actually used
    // (Shield, Forum, HourglassBottom, PhoneForwarded, BugReport, etc.) and remove
    // this dependency. R8 cannot tree-shake it effectively.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // DataStore (for our legacy code)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Room (for our legacy code, will be removed after Realm migration)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
