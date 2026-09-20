/*
 * SwtFrontend - RetroAchievements module (:rcheevos)
 *
 * Vendored rcheevos C library (MIT, https://github.com/RetroAchievements/rcheevos)
 * SHA pin: 1433173220a7eaede6a9ed7a18e94117be1821e0 (v12.5.0)
 * with RC_DISABLE_LUA=1 and RC_CLIENT_SUPPORTS_HASH enabled.
 *
 * JNI bridge (libra_jni.so) + Kotlin API/bridge layer for RetroAchievements.
 * Derived from HylianBox RA integration (GPLv3).
 */

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.redclaw.swt.ra"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    buildTypes { release { isMinifyEnabled = false } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    externalNativeBuild {
        cmake {
            version = "3.22.1"
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.timber)

    // EncryptedSharedPreferences for secure token storage
    implementation(libs.security.crypto)
}
