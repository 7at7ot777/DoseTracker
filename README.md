# DoseTrack

Android app (Kotlin + Jetpack Compose + Material 3). Built entirely on GitHub Actions — no local Android tooling required.

## Toolchain

| Item | Version |
|---|---|
| Gradle (wrapper) | 8.11.1 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.1.0 |
| JDK | 17 (Temurin, CI) |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |

All dependency versions live in `gradle/libs.versions.toml`.

## Build

Every push / PR to `main` (or a manual "Run workflow") runs `.github/workflows/build.yml`, which:

1. Checks out the repo
2. Installs JDK 17
3. Installs the Android SDK, accepts licenses, installs `platforms;android-35` + `build-tools;35.0.0`
4. Runs `./gradlew assembleDebug`
5. Uploads `app/build/outputs/apk/debug/app-debug.apk` as artifact **DoseTrack-debug-apk**

## Download the APK

GitHub repo → **Actions** → latest **Android Build** run → **Artifacts** → `DoseTrack-debug-apk` (zip containing `app-debug.apk`).
