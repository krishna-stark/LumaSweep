package com.example.photostorage.domain

import android.net.Uri

enum class OptimizationMode {
    NONE,
    PHOTO_DOWNSIZE,
    LOSSLESS_SCREENSHOT,
}

enum class OptimizationStatus {
    UNKNOWN,
    ESTIMATED,
    VERIFYING,
    VERIFIED,
    REJECTED,
}

enum class ScreenshotCategory(val displayName: String) {
    CONVERSATIONS("Conversations"),
    RECEIPTS("Receipts"),
    TICKETS("Tickets"),
    DOCUMENTS("Documents"),
    MAPS("Maps"),
    CODES("Codes"),
    SHOPPING("Shopping"),
    SOCIAL("Social"),
    OTHER("Other"),
}

data class PhotoRecord(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val fileSize: Long,
    val dateTaken: Long,
    val dateModified: Long,
    val relativePath: String,
    val sha256: String? = null,
    val perceptualHash: String? = null,
    val blurScore: Float? = null,
    val isScreenshot: Boolean = false,
    val optimizedBytes: Long = 0,
    val optimizedWidth: Int = 0,
    val optimizedHeight: Int = 0,
    val analysisVersion: Int = 0,
    val textDensity: Float? = null,
    val semanticLabels: Set<String> = emptySet(),
    val isFavorite: Boolean = false,
    val protectionReason: String? = null,
    val optimizationQuality: Int = 0,
    val optimizationSimilarity: Float = 0f,
    val optimizationMode: OptimizationMode = OptimizationMode.NONE,
    val optimizedMimeType: String = "",
    val optimizedExtension: String = "",
    val screenshotCategory: ScreenshotCategory = ScreenshotCategory.OTHER,
    val optimizationStatus: OptimizationStatus = OptimizationStatus.VERIFIED,
    val optimizationSettingsFingerprint: String = "",
    val optimizationRequiresReview: Boolean = false,
)

data class DuplicateGroup(
    val hash: String,
    val photos: List<PhotoRecord>,
    val suggestedKeepId: Long,
) {
    val recoverableBytes: Long
        get() = photos.filterNot { it.id == suggestedKeepId }.sumOf { it.fileSize }
}

data class RefreshChanges(
    val newPhotos: Int = 0,
    val changedPhotos: Int = 0,
    val deletedPhotos: Int = 0,
) {
    val totalChanges: Int get() = newPhotos + changedPhotos + deletedPhotos
    val hasChanges: Boolean get() = totalChanges > 0
}

data class CleanupQueues(
    val screenshots: List<PhotoRecord> = emptyList(),
    val similarGroups: List<List<PhotoRecord>> = emptyList(),
    val blurry: List<PhotoRecord> = emptyList(),
) {
    val allPhotos: List<PhotoRecord> by lazy(LazyThreadSafetyMode.NONE) {
        (screenshots + similarGroups.flatten() + blurry).distinctBy { it.id }
    }
    val totalBytes: Long get() = allPhotos.sumOf { it.fileSize }
}

data class ScanSummary(
    val totalPhotos: Int = 0,
    val checkedPhotos: Int = 0,
    val totalPhotoBytes: Long = 0,
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val photos: List<PhotoRecord> = emptyList(),
    val scanPhase: String = "Ready",
    val scanStartedAtMillis: Long = 0,
    val scanPhaseStartedAtMillis: Long = 0,
    val isScanning: Boolean = false,
    val errorMessage: String? = null,
    val refreshChanges: RefreshChanges = RefreshChanges(),
    val isShowingCachedResults: Boolean = false,
) {
    val exactDuplicateBytes: Long get() = duplicateGroups.sumOf { it.recoverableBytes }
    val optimizationCandidates: List<PhotoRecord> by lazy(LazyThreadSafetyMode.NONE) {
        val redundantDuplicateIds = duplicateGroups.flatMapTo(hashSetOf()) { group ->
            group.photos.filterNot { it.id == group.suggestedKeepId }.map { it.id }
        }
        photos.filter {
            it.id !in redundantDuplicateIds &&
                it.optimizationStatus == OptimizationStatus.VERIFIED &&
                it.optimizedBytes > 0 &&
                it.fileSize > it.optimizedBytes
        }
            .sortedByDescending { it.fileSize - it.optimizedBytes }
    }
    val optimizationEstimates: List<PhotoRecord> by lazy(LazyThreadSafetyMode.NONE) {
        photos.filter {
            it.optimizationStatus == OptimizationStatus.ESTIMATED ||
                it.optimizationStatus == OptimizationStatus.VERIFYING
        }.sortedByDescending { it.fileSize - it.optimizedBytes }
    }
    val optimizationSavingBytes: Long
        get() = optimizationCandidates.sumOf { it.fileSize - it.optimizedBytes }
    val losslessOptimizationCandidates: List<PhotoRecord> by lazy(LazyThreadSafetyMode.NONE) {
        optimizationCandidates.filter { it.optimizationMode == OptimizationMode.LOSSLESS_SCREENSHOT }
    }
    val smallerCopyCandidates: List<PhotoRecord> by lazy(LazyThreadSafetyMode.NONE) {
        optimizationCandidates.filter { it.optimizationMode == OptimizationMode.PHOTO_DOWNSIZE }
    }
    val veryLargeCopyCandidates: List<PhotoRecord> by lazy(LazyThreadSafetyMode.NONE) {
        smallerCopyCandidates.filter { it.fileSize >= 12L * 1024L * 1024L }
    }
    val highResolutionCopyCandidates: List<PhotoRecord> by lazy(LazyThreadSafetyMode.NONE) {
        val veryLargeIds = veryLargeCopyCandidates.mapTo(hashSetOf()) { it.id }
        smallerCopyCandidates.filter {
            it.id !in veryLargeIds &&
                it.width.toLong() * it.height >= 16_000_000L
        }
    }
    val standardOversizedCopyCandidates: List<PhotoRecord> by lazy(LazyThreadSafetyMode.NONE) {
        val categorized = (veryLargeCopyCandidates + highResolutionCopyCandidates).mapTo(hashSetOf()) { it.id }
        smallerCopyCandidates.filter { it.id !in categorized }
    }
    val losslessOptimizationSavingBytes: Long
        get() = losslessOptimizationCandidates.sumOf { it.fileSize - it.optimizedBytes }
    val smallerCopySavingBytes: Long
        get() = smallerCopyCandidates.sumOf { it.fileSize - it.optimizedBytes }
    val verifiedTotalSavingBytes: Long get() = exactDuplicateBytes + optimizationSavingBytes
    val screenshotPhotos: List<PhotoRecord> by lazy(LazyThreadSafetyMode.NONE) { photos.filter { it.isScreenshot } }
    val screenshotCategories: Map<ScreenshotCategory, List<PhotoRecord>> by lazy(LazyThreadSafetyMode.NONE) {
        screenshotPhotos.groupBy { it.screenshotCategory }
    }
    val blurryPhotos: List<PhotoRecord> by lazy(LazyThreadSafetyMode.NONE) {
        photos.filter { !it.isScreenshot && it.blurScore != null && it.blurScore < 72f }
    }
    val similarGroups: List<List<PhotoRecord>> by lazy(LazyThreadSafetyMode.NONE) { SimilarityEngine.cluster(photos) }
    /**
     * Decision-first queues. A photo appears in at most one subjective queue and favorites,
     * duplicate-set members, and verified optimization candidates are never mixed into them.
     */
    val cleanupQueues: CleanupQueues by lazy(LazyThreadSafetyMode.NONE) {
        val duplicateIds = duplicateGroups.flatMapTo(hashSetOf()) { group -> group.photos.map { it.id } }
        val reservedIds = duplicateIds + optimizationCandidates.mapTo(hashSetOf()) { it.id }
        val eligible = photos.filter { !it.isFavorite && it.id !in reservedIds }
        val screenshots = eligible.filter { it.isScreenshot }
        val screenshotIds = screenshots.mapTo(hashSetOf()) { it.id }
        val nonScreenshots = eligible.filter { it.id !in screenshotIds }
        val similar = SimilarityEngine.cluster(nonScreenshots)
        val similarIds = similar.flatten().mapTo(hashSetOf()) { it.id }
        val blurry = nonScreenshots.filter {
            it.id !in similarIds && it.blurScore != null && it.blurScore < 72f
        }
        CleanupQueues(screenshots = screenshots, similarGroups = similar, blurry = blurry)
    }
    val mostlyTextPhotos: List<PhotoRecord> by lazy(LazyThreadSafetyMode.NONE) {
        photos.filter { (it.textDensity ?: 0f) >= .12f }
    }
    val semanticLabelCounts: List<Pair<String, Int>> by lazy(LazyThreadSafetyMode.NONE) {
        photos.flatMap { it.semanticLabels }.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.take(6).map { it.key to it.value }
    }
    val reviewCandidateBytes: Long
        get() = cleanupQueues.totalBytes
    val guaranteedSavingBytes: Long get() = exactDuplicateBytes
    val progress: Float
        get() = if (totalPhotos == 0) 0f else (checkedPhotos.toFloat() / totalPhotos).coerceIn(0f, 1f)
    val overallProgress: Float
        get() = when {
            !isScanning && totalPhotos > 0 -> 1f
            scanPhase.contains("Reading", ignoreCase = true) -> .05f
            scanPhase.contains("duplicate", ignoreCase = true) -> .08f + progress * .27f
            scanPhase.contains("quality", ignoreCase = true) -> .35f + progress * .40f
            scanPhase.contains("saving", ignoreCase = true) -> .75f + progress * .25f
            else -> progress
        }.coerceIn(0f, 1f)
}
