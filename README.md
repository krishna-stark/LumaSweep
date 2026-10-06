# LumaSweep

LumaSweep is a privacy-first Android photo cleanup app. It finds exact duplicates, verifies smaller-copy opportunities, and builds review-only queues for similar photos, screenshots, and possibly blurry images. Analysis stays on the device and every removal goes through Android's system Trash.

## Requirements

- macOS, Linux, or Windows
- Git and JDK 17
- Android Studio with Android SDK Platform 36 and SDK Build Tools
- Android 7.0/API 24 or newer device or emulator

The Gradle wrapper is committed, so a separate Gradle installation is not required.

## Clone

```bash
git clone https://github.com/krishna-stark/LumaSweep.git
cd LumaSweep
```

## Configure the Android SDK

Android Studio creates `local.properties` automatically. For command-line builds, create it locally (it is intentionally gitignored):

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

Typical SDK paths are `$HOME/Library/Android/sdk` on macOS and `$HOME/Android/Sdk` on Linux.

## Build

macOS/Linux:

```bash
./gradlew assembleDebug
```

Windows:

```powershell
.\gradlew.bat assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Test and verify

Run the complete local verification suite:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Reports are written under `app/build/reports/`.

## Run from Android Studio

1. Open the repository root in Android Studio.
2. Wait for Gradle sync to finish.
3. Select the `app` run configuration.
4. Connect an Android device or start an API 24+ emulator.
5. Press Run.
6. Grant photo access. Android 14+ can grant full or selected-photo access.

## Run from the command line

Enable USB or wireless debugging, then verify ADB sees the device:

```bash
adb devices
```

Build, install, and launch:

```bash
./gradlew installDebug
adb shell am start -n com.example.photostorage/.MainActivity
```

For wireless debugging, pair and connect using the values shown by Android before running the same commands:

```bash
adb pair DEVICE_IP:PAIRING_PORT
adb connect DEVICE_IP:DEBUG_PORT
```

## First run

After access is granted, LumaSweep queues one unique WorkManager scan. Large libraries continue in the background with a foreground progress notification. Completed batches are stored in Room, so Review uses saved results rather than scanning again.

No cleanup action is automatic. Select photos in a review screen and confirm the final system Trash request yourself.

## Documentation

- [System design](ARCHITECTURE.md)
- [Folder structure](docs/FOLDER_STRUCTURE.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

## Development commands

```bash
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew lintDebug           # Android lint
./gradlew assembleDebug       # debug APK
./gradlew clean               # remove generated build output
```

## Privacy model

The application does not declare the Internet permission. Media discovery uses Android MediaStore; analysis and ML inference run locally; metadata is persisted in the app-private Room database; and originals are only moved through Android's recoverable Trash flow.
