# Cores + LibretroDroid — SwtFrontend

## Por que runtime download e não bundled

- **rcheevos não exige copiar cores.** É lib C separada (`:rcheevos`) que lê memória via `libretrodroid.getMemoryRegion(int id) → ByteBuffer` (passthrough JNI `libretrodroidjni.cpp`, `LibretroDroid.java:146-159`). Dá para usar qualquer core baixado.
- **Sem flavors:** 1 APK universal, 20 cores (Swordfish90/LemuroidCores `1.17.0`) puxados por ABI do device (`arm64-v8a` etc.) → `filesDir/cores/1.17.0/`.
- **Legal/GPLv3:** core `.so` é GPL; baixar em runtime evita redistribuir binário fechado e facilita update sem release.

## LibretroDroid (vendored)

- **Origem:** HylianBox `libretrodroid` tag 0.13.2 (Swordfish90/LibretroDroid 0.13.2 + passthrough RA de HylianBox, `getMemoryData/getMemorySize` → `getMemoryRegion` ByteBuffer, EGL-backed video recording). Copiado via `rsync` 22 MB src, namespace `com.swordfish.libretrodroid` preservado, oboe 1.5 `b15f5e39` + `libretro/libretro-common` in-tree.
- **Build:** `libretrodroid/build.gradle.kts` `com.android.library` + `kotlin.android`, `compileSdk 34` (restante 35), `minSdk 24`, `externalNativeBuild cmake -DANDROID_STL=c++_static`, `compileOptions Java 1.8` (demais 17). Stdlib 2.1.20 + lifecycle 2.7.0 exigiram `Kotlin 2.1.20` no root (`Incompatible classes`).
- **API usada:** `GLRetroView` (criado programaticamente em `GameActivity`, `frameSpeed`, `sendKeyEvent/sendMotionEvent`), `LibretroDroid.getMemoryRegion(id)` para rcheevos.

## CoreUpdater (app/cores/)

- **Arquivo:** `app/src/main/java/br/com/redclaw/swt/cores/CoreUpdaterImpl.kt` (`class CoreUpdaterImpl implements CoreUpdater` de `retrograde-app-shared`).
- **Constantes:** `CORES_VERSION="1.17.0"`, `BASE_URL="https://github.com/Swordfish90/LemuroidCores/raw/$CORES_VERSION"`.
- **URL:** `BASE_URL/lemuroid_core_<core>/src/main/jniLibs/<abi>/lib<core>_libretro_android.so` (ex: `lemuroid_core_mgba/src/main/jniLibs/arm64-v8a/libmgba_libretro_android.so`), `OkHttp` com `connect 30s / read 60s`, `Dispatchers.IO`, `ProgressCallback` (`onCoreDownloadStart/Progress/Complete/Error`, `onAllCoresComplete`) + `DownloadResult` + `suspend downloadCores(context, coreIDs, callback)`.
- **Destino:** `context.filesDir/cores/<version>/<abi>/lib<core>.so` (via `DirectoriesManager` de `retrograde-app-shared`), limpeza de versões antigas (`filesDir/cores/` lista e delete `< version`), `OkHttpClient` singleton.
- **Fonte CoreID:** `GameSystem`/`CoreID` enum de `retrograde-app-shared` (25 sistemas, 20 cores; `CoreID.kt` mapeia `systemCoreConfigs`).
- **ABIs:** `Build.SUPPORTED_ABIS[0]` primeiro; fallback `arm64-v8a` → `armeabi-v7a` → `x86_64` → `x86` se `find` por ABI falhar.

## lemuroid-cores/ (referência)

- **Não é `include` no `settings.gradle.kts`.** Pasta `lemuroid-cores/` na raiz (não módulo) com `CORES_VERSION` (texto `1.17.0`) + `update_cores.sh` (POSIX sh, `set -euo pipefail`, `KNOWN_CORES` 20: `stella fceumm snes9x genesis_plus_gx gambatte mgba mupen64plus_next pcsx_rearmed ppsspp fbneo mame2003_plus desmume melonds handy mednafen_pce_fast mednafen_ngp mednafen_wswan prosystem stella citra` etc., `buildbot.libretro.com` nightly + `LemuroidCores` GitHub raw check via `curl`/`jq`, `SCRIPT_DIR` autodetect).
- **Uso:** `update_cores.sh [CORES_VERSION]` imprime `KNOWN_CORES` disponíveis e atualiza `CORES_VERSION` (manual, não CI). `CoreUpdaterImpl` lê `CORES_VERSION` hardcoded, não o arquivo (mas `update_cores.sh` mantém sincronizado).

## StorageProviderRegistry (ROMs)

- `SettingsActivity` → `ActivityResultContracts.OpenDocumentTree` → URIs `content://` persistidas em `StorageProviderRegistry` (SAF) + `SwtPrefs`. `LemuroidLibrary` WorkManager escaneia em background (não UI thread). Detecção via `LibretroDBMetadataProvider` (`.agents/files-structure.md`).

## Build nativo (perfil)

- `libretrodroid` CMake `3.22.1`, NDK `27.2.12479018` (4 ABIs → cada `buildCMakeDebug[abi]` ~5s, total `libretrodroid:mergeDebugNativeLibs`).
- `rcheevos` CMake similar (4 ABIs, `libra_jni.so` por ABI, `RC_DISABLE_LUA=1`).
- `app:mergeDebugNativeLibs` → `stripDebugDebugSymbols` → `packageDebug` (40 MB APK).
