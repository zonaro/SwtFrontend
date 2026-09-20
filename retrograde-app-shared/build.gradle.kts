plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.swordfish.lemuroid.lib"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // Project modules (other vendored Lemuroid modules)
    implementation(project(":retrograde-util"))
    implementation(project(":lemuroid-touchinput"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.ktx.collection)
    implementation(libs.fragment.ktx)
    implementation(libs.activity.ktx)

    // Lifecycle
    api(libs.lifecycle.runtime)

    // WorkManager
    implementation(libs.workmanager)

    // Room (with KSP)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.common)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // Paging
    implementation(libs.paging.common)
    implementation(libs.paging.runtime)

    // DocumentFile
    implementation(libs.documentfile)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okio)
    implementation(libs.retrofit)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.timber)

    // Coroutines
    implementation(libs.coroutines.android)

    // Preferences
    implementation(libs.preference.ktx)
    implementation(libs.harmony)
    implementation(libs.flow.preferences)
}
