plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

apply(plugin = "realm-android")

android {
    namespace = "com.callscreen.app.data"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))

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

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:1.7.3")

    // Coroutines reactive
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.7.3")

    // AndroidX
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.work:work-rxjava2:2.9.0")

    // JDK9 compat
    implementation("com.github.pengrad:jdk9-deps:1ffe84c468")
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // RxPreferences
    implementation("com.f2prateek.rx.preferences2:rx-preferences:2.0.1")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Moshi
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")

    // Phone number formatting
    implementation("io.michaelrocks:libphonenumber-android:8.13.27")

    // Call Control datashare
    implementation("com.callcontrol:datashare:1.3.0")

    // Timber
    implementation("com.jakewharton.timber:timber:5.0.1")

    // SMS/MMS library
    implementation(project(":libs:android-smsmms"))

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.json:json:20231013")
}
