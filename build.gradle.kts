plugins {
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}

// Ext properties required by quik/android-smsmms module
subprojects {
    ext {
        set("androidx_core_version", "1.12.0")
        set("timber_version", "5.0.1")
        set("kotlin_version", "1.9.22")
    }
}
