# Repository structure

```text
LumaSweep/
├── app/
│   ├── schemas/                         # Exported Room schema history
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Permissions, Activity, foreground service
│   │   ├── assets/                      # Bundled on-device model assets
│   │   ├── java/com/example/photostorage/
│   │   │   ├── MainActivity.kt          # Thin lifecycle entry point
│   │   │   ├── PhotoStorageApplication.kt
│   │   │   ├── AppContainer.kt          # Process dependency composition
│   │   │   ├── platform/
│   │   │   │   └── PhotoAccess.kt       # Version-aware permission policy
│   │   │   ├── domain/                  # Pure decisions and immutable models
│   │   │   │   ├── PhotoModels.kt
│   │   │   │   ├── ExactDuplicateEngine.kt
│   │   │   │   ├── OptimizationPolicy.kt
│   │   │   │   ├── ScreenshotClassifier.kt
│   │   │   │   └── SimilarityEngine.kt
│   │   │   ├── data/                    # Android data sources and local processing
│   │   │   │   ├── AppDatabase.java
│   │   │   │   ├── MediaItemDao.java
│   │   │   │   ├── MediaItemEntity.java
│   │   │   │   ├── MediaStorePhotoScanner.kt
│   │   │   │   ├── PhotoRepository.kt
│   │   │   │   ├── PhotoScanWorker.kt
│   │   │   │   ├── LocalImageAnalyzer.kt
│   │   │   │   ├── OnDeviceOcrAnalyzer.kt
│   │   │   │   ├── Yolo11SemanticAnalyzer.kt
│   │   │   │   ├── PhotoOptimizer.kt
│   │   │   │   └── SearchableDocumentExporter.kt
│   │   │   └── ui/                      # Compose presentation and app actions
│   │   │       ├── LumaSweepAppHost.kt  # Permissions, Trash, rollback, export
│   │   │       ├── MainViewModel.kt
│   │   │       ├── PhotoStorageApp.kt   # Navigation and top-level composition
│   │   │       ├── AppDestination.kt
│   │   │       ├── FindingKind.kt
│   │   │       ├── PhotoComponents.kt
│   │   │       ├── UiFormatters.kt
│   │   │       ├── SettingsScreen.kt
│   │   │       ├── DuplicateReviewScreen.kt
│   │   │       ├── OptimizationReviewScreen.kt
│   │   │       ├── SwipeReviewScreen.kt # Gallery review; legacy filename
│   │   │       └── theme/
│   │   └── res/                         # Icons, strings, XML and resources
│   ├── src/test/                         # JVM domain tests
│   └── build.gradle.kts                  # Android module configuration
├── gradle/wrapper/                       # Reproducible Gradle bootstrap
├── ARCHITECTURE.md                       # Detailed system design
├── README.md                             # Clone, build, test and run guide
├── THIRD_PARTY_NOTICES.md
├── build.gradle.kts                      # Root plugin versions
└── settings.gradle.kts                   # Repositories and modules
```

## Where new code belongs

- Put product rules and queue math in `domain/`; keep them independent from Compose and Android activities.
- Put MediaStore, Room, WorkManager, encoders, OCR, and on-device inference in `data/`.
- Put SDK-specific permission or operating-system policy in `platform/`.
- Put screen state and rendering in `ui/`; Activity Result workflows belong in `LumaSweepAppHost`, not feature screens.
- Add domain tests whenever category priority, verification, or savings math changes.

Generated build output, SDK settings, APKs, bundles, and signing material are excluded from version control.
