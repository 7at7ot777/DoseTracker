# DoseTrack

Offline Android app for organising medications and remembering doses. English and Arabic (RTL), light and dark themes.

> DoseTrack is a personal reminder and tracking tool. It does not give medical advice, diagnose conditions, or recommend changing doses.

## Features

- **Medications** — name, generic name, dose amount + unit, strength, type, instructions (before/with/after food or custom text), notes.
- **Schedules** — once to four times daily or custom times; a separate note per dose time (e.g. 08:00 "after breakfast", 22:00 "before sleeping").
- **Duration** — ongoing, number of days ("7-day course · Ends Oct 4"), or an end date. Reminders stop after the end date.
- **Today** — progress ("3 / 4 doses completed"), doses grouped by time, large Take / Skip buttons, confirmation, undo, snooze, corrections.
- **Dose states** — Upcoming, Due, Taken (actual time recorded), Skipped, Missed (automatically, 2 hours after the scheduled or snoozed time).
- **Reminders** — local notifications at exact times with Taken / Snooze / Open App. Per medication, **alarm mode** rings with the alarm tone (even on silent) and shows a full-screen alarm until answered.
- **History** — any day's doses, correct past records, weekly adherence % with a 7-day chart.
- **Manage** — edit, pause/resume, delete (archived: dose history is kept).
- **Settings** — reminders on/off, default snooze, sound, vibration, theme, language, export / import / clear data, about, privacy.
- **Onboarding** — short, skippable first-launch introduction with permission requests.

All data is stored on the phone (Room database). No account, no server, no internet permission.

## Permissions

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | Show dose reminders (Android 13+ asks you). |
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | Fire reminders exactly at the dose time. |
| `USE_FULL_SCREEN_INTENT` | Alarm-mode reminders over the lock screen (Android 14+ may ask you to allow it). |
| `RECEIVE_BOOT_COMPLETED` | Restore reminders after the phone restarts. |
| `VIBRATE` | Vibrate with reminders. |

## Download and install

1. Open the **latest-debug** release: `https://github.com/7at7ot777/DoseTracker/releases/tag/latest-debug`
2. Download **`DoseTrack-debug.apk`** (a plain `.apk`, no zip). It is replaced on every push to `main`.
3. On the phone, open the file and allow "Install unknown apps" for your browser/file manager when asked.
4. Installing a newer APK over the old one keeps your data.

Each workflow run also has a zipped `DoseTrack-debug-apk` artifact and a `lint-report` artifact.

## Build (GitHub Actions only)

No Android Studio, SDK, Gradle or Java is needed locally. `.github/workflows/build.yml` runs on a clean `ubuntu-latest` runner:

1. Checkout → JDK 17 (Temurin) → Android SDK (`platform-tools`, `platforms;android-35`, `build-tools;35.0.0`, licenses accepted) → Gradle (cached, wrapper validated)
2. `./gradlew testDebugUnitTest` — unit tests (dose generation, duration, validation, adherence, backup format)
3. `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
4. `./gradlew lintDebug` — report only, never fails the build
5. Upload artifact + publish `DoseTrack-debug.apk` to the `latest-debug` release

Trigger: push / pull request to `main`, or **Actions → Android Build → Run workflow**.

## Toolchain

Gradle 8.11.1 (wrapper) · AGP 8.7.3 · Kotlin 2.1.0 · JDK 17 · compileSdk/targetSdk 35 · minSdk 26 · Jetpack Compose + Material 3 · Room · DataStore · WorkManager · kotlinx.serialization. Versions live in `gradle/libs.versions.toml`.

## Backup

Settings → **Export data** saves a JSON file (`dosetrack-backup-YYYY-MM-DD.json`) with medications, schedules and full dose history. **Import data** replaces everything on the phone with a backup (after confirmation) and re-creates reminders.

## Known limitations

- The APK is **debug-signed**; it is for personal installation, not the Play Store.
- Some phone makers stop background apps aggressively. If reminders arrive late, exclude DoseTrack from battery optimisation (onboarding has a shortcut).
- On Android 12 and older, notification text follows the in-app language only after the app has been opened once since a reboot.
- People upgrading from a build before 1.0.0 see the onboarding screens once.
