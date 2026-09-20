/*
 * build.gradle.kts — lemuroid-metadata-libretro-db
 *
 * Vendored from Lemuroid (Swordfish90/Lemuroid).
 * GPLv3 — see COPYING in this directory.
 *
 * Provides LibretroDBMetadataProvider: metadata detection via
 * libretro-db.sqlite with cascade CRC → serial → filename → extension lookup.
 */

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.swordfish.lemuroid.metadata.libretrodb"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    resourcePrefix = "libretrodb_"
}

dependencies {
    implementation(project(":retrograde-app-shared"))
    implementation(project(":retrograde-util"))

    // Room — leitura do banco libretro-db.sqlite
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // Coroutines
    implementation(libs.coroutines.android)

    // Logging
    implementation(libs.timber)
}
