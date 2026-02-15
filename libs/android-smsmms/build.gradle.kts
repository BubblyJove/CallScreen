plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.klinker.android.send_message"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
    }

    // Point source sets to the Quik submodule's android-smsmms source
    val quikSmsmms = rootProject.file("quik/android-smsmms/src/main")
    sourceSets {
        getByName("main") {
            java.srcDirs(quikSmsmms.resolve("java"))
            res.srcDirs(quikSmsmms.resolve("res"))
            manifest.srcFile(quikSmsmms.resolve("AndroidManifest.xml"))
        }
    }

    useLibrary("org.apache.http.legacy")

    lint {
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("com.squareup.okhttp:okhttp:2.5.0")
    implementation("com.squareup.okhttp:okhttp-urlconnection:2.5.0")
}
