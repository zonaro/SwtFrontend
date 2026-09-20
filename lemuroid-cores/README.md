# lemuroid-cores

Reference directory for libretro core versions used by SwtFrontend's `CoreUpdater`.

This directory does **NOT** contain binary `.so` files. Cores are downloaded at
runtime by `CoreUpdaterImpl` from GitHub:
`https://github.com/Swordfish90/LemuroidCores/raw/<CORES_VERSION>/lemuroid_core_<core>/src/main/jniLibs/<abi>/...`

## Version

The current cores version is defined in `CORES_VERSION` (currently `1.17.0`).

## Updating cores

Run `update_cores.sh` to regenerate the available cores list from
buildbot.libretro.com nightly builds. This script:

1. Downloads the LibretroDB metadata to identify available cores
2. Generates the list of core names matching `lemuroid_core_*` naming convention
3. Validates against `buildbot.libretro.com` for Android `.so` availability

## Supported ABIs

- `arm64-v8a` (primary, most modern devices)
- `armeabi-v7a` (legacy 32-bit ARM)
- `x86_64` (emulators, ChromeOS)
- `x86` (legacy 32-bit x86)

## Integration

This directory is a **git submodule reference** pointing to
`https://github.com/Swordfish90/LemuroidCores`. It is NOT included in
`settings.gradle.kts` as a Gradle module (cores are binaries, not compiled source).
