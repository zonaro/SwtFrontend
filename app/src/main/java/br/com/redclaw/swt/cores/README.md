# br.com.redclaw.swt.cores

Runtime core downloader for libretro cores. Pattern from Lemuroid free flavor.

## Flow

1. `CoreUpdaterImpl` checks if core `.so` exists at `filesDir/cores/<version>/`
2. If not found, downloads from `https://github.com/Swordfish90/LemuroidCores/raw/<version>/lemuroid_core_<name>/src/main/jniLibs/<abi>/<file>`
3. Uses device's primary ABI from `Build.SUPPORTED_ABIS[0]`
4. Cleans up outdated core versions after download
5. Reports progress via `ProgressCallback` interface

## Version

- `CORES_VERSION = "1.17.0"` — matches libretro 1.17 tag
- Stored in `lemuroid-cores/CORES_VERSION`
- Tracked in `SwtPrefs.getCoresInstalledVersion()` / `setCoresInstalledVersion()`

## Supported Cores

Defined in `retrograde-app-shared/.../CoreID.kt`:
Stella, FCEUmm, Snes9x, Genesis Plus GX, Gambatte, mGBA, Mupen64Plus,
PCSXReARMed, PPSSPP, FBNeo, MAME2003 Plus, DeSmuME, MelonDS, Handy,
PCEFast, ProSystem, Mednafen NGP, Mednafen WonderSwan, Citra, DosBox Pure

## ABIs

arm64-v8a, armeabi-v7a, x86_64, x86

## Dependencies

- `CoreUpdater` interface from `retrograde-app-shared`
- `CoreID` enum from `retrograde-app-shared`
- `DirectoriesManager` from `retrograde-app-shared`
- OkHttp 4.12.0 (from `gradle/libs.versions.toml`)

## GPLv3

Derived from Lemuroid `CoreUpdaterImpl`. See license header.
