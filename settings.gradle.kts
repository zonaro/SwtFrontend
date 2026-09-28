pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "SwtFrontend"
include(":app")
include(":libretrodroid")
include(":module-api")
include(":module-sdk")

// Módulos vendored do Lemuroid — Fase 1
include(":retrograde-util")
include(":retrograde-app-shared")
include(":lemuroid-metadata-libretro-db")
include(":lemuroid-touchinput")

// Módulo nativo RetroAchievements — Fase 5
include(":rcheevos")
