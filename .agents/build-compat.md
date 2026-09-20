# Build & Compatibilidade — SwtFrontend

## Matriz

| Componente | Versão | Por que | Onde |
|---|---|---|---|
| **AGP** | `8.11.1` | HylianBox 8.11.x, Kotlin 2.1.20 compat | `gradle/libs.versions.toml` `[versions] agp` + `plugins android-application/library` |
| **Kotlin** | `2.1.20` | `libretrodroid` stdlib 2.1.20 + lifecycle 2.7.0 metadata `2.1.0` exigem `Incompatible classes` fix (era 1.9.22) | `libs.versions.toml` `kotlin` + `kotlin-android/jvm/serialization/compose` 2.1.20 + `ksp 2.1.20-1.0.32` |
| **compileSdk / targetSdk** | `35` | Android 15, `Switch` tokens Material3 DayNight, `Activity` 1.9.0 etc. | `app/build.gradle.kts` + `*.build.gradle.kts` (libretrodroid 34) + `deps.kt` HylianBox |
| **minSdk** | `24` | `libretrodroid` min 24 (oboe + EGL), `SAF`/`DocumentFile` 1.0.1, `EncryptedSharedPreferences` | `*.build.gradle.kts` `minSdk 24` |
| **JDK** | `17` (Temurin 17.0.20.1) | AGP 8.11 exige JDK 17, `compileOptions Java 17` + `kotlinOptions jvmTarget 17`, `org.gradle.java.home=~/android-setup/jdk17` | `gradle.properties` + `~/android-setup/jdk17` (`JAVA_HOME`) + `local.properties` não set `java.home` (usa `gradle.properties`) |
| **SDK** | `~/android-setup/sdk` | `platforms;android-35` + `build-tools;35.0.0` + `platform-tools` + `ndk;27.2.12479018` + `cmake;3.22.1` + `licenses` aceitas | `local.properties` `sdk.dir`, `android-setup/` criado em Fase 0 (era `/home/kaizonaro/Android/Sdk` inexistente) |
| **NDK** | `27.2.12479018` | `libretrodroid` + `rcheevos` CMake `ANDROID_STL=c++_static`, 4 ABIs | `sdk/ndk/27.2.12479018`, `app:mergeDebugNativeLibs` |
| **CMake** | `3.22.1` | `externalNativeBuild cmake -DANDROID_STL=c++_static` | `sdk/cmake/3.22.1` + `*/build.gradle.kts` `externalNativeBuild` |
| **Gradle** | `8.14` | Wrapper `8.14` → Daemon JVM JDK 17 | `~/android-setup/gradle/gradle-8.14` + `gradlew` (`distributionUrl` 8.14) + `Daemon JVM = JDK 17` |
| **Ktor** | `3.1.3` | Dashboard CIO (HylianBox 3.1.3, não Netty), pure Kotlin Android | `libs.versions.toml` `ktor`, `ktor-server-core/cio/cors/status.pages` |
| **Room** | `2.7.0` | `retrograde-app-shared` (KSP) + `lemuroid-metadata-libretro-db` (kapt) | `libs.versions.toml` `room`, `ksp` / `kapt` |
| **Lifecycle** | `2.7.0` | `lifecycle-runtime-ktx` | `libs.versions.toml` |
| **Paging** | `3.3.6` | `paging-common/runtime` + `room-paging` | `libs.versions.toml` |
| **OkHttp** | `4.12.0` | `CoreUpdaterImpl` + `RAApi`/`RaHttpClient` + scrapers + `logging-interceptor` | `libs.versions.toml` |
| **Coil** | `2.6.0` | `GamingCovers` covers + RA badges | `libs.versions.toml` |
| **Coroutines** | `1.8.0` | `CoreUpdater` `Dispatchers.IO`, `RaSessionManager` `SharedFlow`, scraping `delay` | `libs.versions.toml` |
| **Compose BOM** | `2024.12.01` | `lemuroid-touchinput` Compose (não app) + `padkit 1.0.0` | `libs.versions.toml` |

## Catalog (`gradle/libs.versions.toml`)

```toml
[versions] agp 8.11.1, kotlin 2.1.20, ksp 2.1.20-1.0.32, room 2.7.0, ktor 3.1.3, etc.
[libraries] androidx-core-ktx, appcompat, material, lifecycle-runtime, activity-ktx, recyclerview, coil, okhttp, coroutines-android, workmanager, nanohttpd (deprecated, mantido), gson, ktor-server-core/cio/content-negotiation/serialization-json/cors/status-pages, security-crypto, timber, room-runtime/ktx/compiler/paging/common, paging-common/runtime, retrofit, harmony 1.2.2, flow-preferences 1.8.0 (fix com.fredporciuncula 0.6.1→1.8.0), preference-ktx, documentfile, compose-bom/material3/icons/ui/geometry/runtime/foundation/animation, padkit, kotlinx-collections-immutable...
[plugins] android-application/library (agp), kotlin-android/jvm/serialization/compose (kotlin), ksp (ksp)
```

**Fixes Fase 1:** `flow-preferences` group `com.fredporciuncula.flow-preferences` → `com.fredporciuncula` (DAG `com.fredporciuncula.flow-preferences:flow-preferences:0.6.1` not found) + `harmony` já correto `com.frybits.harmony`.

## `settings.gradle.kts` + `*.build.gradle.kts`

- `pluginManagement` `google/mavenCentral/gradlePluginPortal`, `dependencyResolutionManagement` `FAIL_ON_PROJECT_REPOS` + `google/mavenCentral/jitpack`.
- `app` `plugins kotlin.android`, `android namespace br.com.redclaw.swt`, `compileSdk 35`, `defaultConfig applicationId br.com.redclaw.swt, minSdk 24, targetSdk 35, versionCode 1, versionName 0.1.0, testInstrumentationRunner`, `buildTypes release minify false`, `compileOptions Java 17`, `kotlinOptions jvmTarget 17`, `buildFeatures viewBinding true`, `packaging` excludes `META-INF AL2.0`, `jniLibs useLegacyPackaging false`, `dependencies` 5 `project(:...)` + `libs.*`.
- `libretrodroid` `compileSdk 34`, `kotlinOptions 17`, `externalNativeBuild cmake -DANDROID_STL`, `buildFeatures compose=false` (mas `ComposeUtils` em `retrograde-util`).
- `retrograde-util` `plugins kotlin.android/compose`, `buildFeatures compose true`, `kotlinOptions 17` + `freeCompilerArgs -Xcontext-receivers`.
- `retrograde-app-shared` `plugins android.library/kotlin.android/serialization/ksp`, `room.schemaLocation`, `dependencies` `ktx.collection` etc.

## Comandos

```bash
# Build debug (app + 5 libs + 2 nativos 4 ABIs → 176 tasks, ~26s incremental)
./gradlew :app:assembleDebug --console=plain
# Só nativo
./gradlew :rcheevos:assembleDebug :libretrodroid:assembleDebug
# Só kotlins
./gradlew :retrograde-util:assembleDebug :retrograde-app-shared:assembleDebug :lemuroid-touchinput:assembleDebug :lemuroid-metadata-libretro-db:assembleDebug
# Clean + build
./gradlew clean :app:assembleDebug
# Logs
adb logcat | grep -E "Swt|DashboardServer|rcheevos|CoreUpdater"
# APK
ls -lh app/build/outputs/apk/debug/app-debug.apk # 40 MB, minSdk 24
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## install.sh + VS Code task

- **`install.sh`** (POSIX `sh`, `chmod +x`, distro-agnostic, `~/android-setup/sdk` autodetect `PATH→~/android-setup/sdk/platform-tools/adb→~/Android/Sdk→$ANDROID_SDK_ROOT`):
  - `PROJECT_DIR` via `dirname $0`, `APK_PATH` `app/build/outputs/apk/debug/app-debug.apk`, `GRADLEW` `./gradlew` com `JAVA_HOME=~/android-setup/jdk17` se existir.
  - Flags: `--build-only` (só `assembleDebug`), `--install-only` (só `adb install -r`), `--launch` ( + `am start br.com.redclaw.swt/.views.LibraryActivity`), `--help`.
  - Checks: `adb` existe, `gradlew` executável, `BUILD` (`assembleDebug --console=plain`), `APK` existe (`ls -lh`), `adb devices -l` (header skip `awk NR>1`), `DEVICE_COUNT`, `unauthorized` → instrução “Permitir depuração USB” + `adb kill-server/start-server` + `Revogar autorizações`, `offline` → reconectar, `>1` → `ANDROID_SERIAL` warn; `adb install -r` + fallback ` -t`, `LAUNCH` via `am start`.
- **`.vscode/tasks.json`** (criado pelo install task, se não existir):
  ```json
  {
    "version": "2.0.0",
    "tasks": [
      { "label": "Swt: Build Debug", "type": "shell", "command": "./gradlew :app:assembleDebug --console=plain", "group": "build" },
      { "label": "Swt: Install via ADB", "type": "shell", "command": "./install.sh --install-only", "dependsOn": "Swt: Build Debug" },
      { "label": "Swt: Build & Install", "type": "shell", "command": "./install.sh", "group": {"kind": "build", "isDefault": true} },
      { "label": "Swt: Build & Install & Launch", "type": "shell", "command": "./install.sh --launch" }
    ]
  }
  ```
  Uso: `Ctrl+Shift+B` (default Build & Install) ou `F1 → Tasks: Run Task → Swt: ...`.

## Gotchas vistos

- `Kotlin 1.9.22 → 2.1.20` obrigatório para `stdlib 2.1.20` + `lifecycle 2.7.0` metadata `2.1.0` (`Incompatible classes` + `Unresolved reference: error` em `error()`).
- `themes.xml` precisou ser enxuto Fase 0 (12 overlays + `Theme.Swt` base) senão `bg_switch_button_focus` etc. faltantes → `error linking references`.
- `lemuroid-touchinput` tinha `common/` duplicado (`ComposeUtils/GraphicsUtils/GeometryUtils` já em `retrograde-util`) → `Type is defined multiple times` → `rm -rf common/` + `implementation(project(":retrograde-util"))`.
- `toLowerCase` → `lowercase` (deprecated) em `GameSystem`/`LibretroDBMetadataProvider`.
- `Dashboard` Ktor `CIO` vs `Netty`: `CIO` usa menos métodos e `~1 MB` a menos que Netty, aprovado em `momus OKAY`.
