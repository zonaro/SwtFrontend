// retrograde-util — vendored from Swordfish90/Lemuroid.
//
// Vendored from https://github.com/Swordfish90/Lemuroid
// master branch, SHA 53752bf29bc3f95c50f6c38f70cd4a53450a7098
// GPLv3 — see individual file headers.
//
// Adapted for SwtFrontend: AGP 8.11.1, Kotlin 2.1.20, no buildSrc deps object.
// Namespace preserved as com.swordfish.lemuroid.common for upstream diff compatibility.

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

android {
    namespace = "com.swordfish.lemuroid.common"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
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
    // Timber (not in catalog)
    api("com.jakewharton.timber:timber:5.0.1")

    // Compose BOM + required modules (not in catalog)
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-unit-android")

    // AndroidX — from catalog
    implementation(libs.androidx.appcompat)
    implementation(libs.recyclerview)
    implementation(libs.lifecycle.runtime)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)

    // AndroidX — not in catalog, explicit versions
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("androidx.paging:paging-common:3.2.1")
    implementation("androidx.paging:paging-runtime:3.2.1")
}
