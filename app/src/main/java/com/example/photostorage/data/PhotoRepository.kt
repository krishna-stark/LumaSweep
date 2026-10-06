package com.example.photostorage.data

import com.example.photostorage.domain.ScanSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

class PhotoRepository(
    private val scanner: MediaStorePhotoScanner,
) {
    private val mutableSummary = MutableStateFlow(ScanSummary())
    val summary: StateFlow<ScanSummary> = mutableSummary.asStateFlow()
    private val scanMutex = Mutex()

    suspend fun loadCachedResults() {
        if (mutableSummary.value.totalPhotos > 0 || mutableSummary.value.isScanning) return
        val cached = scanner.cachedSnapshot()
        if (cached.photos.isEmpty() || mutableSummary.value.isScanning) return
        mutableSummary.value = ScanSummary(
            totalPhotos = cached.totalPhotos,
            checkedPhotos = cached.checkedPhotos,
            totalPhotoBytes = cached.totalBytes,
            duplicateGroups = cached.groups,
            photos = cached.photos,
            scanPhase = cached.phase,
            isShowingCachedResults = true,
        )
    }

    suspend fun scan(): Boolean {
        if (!scanMutex.tryLock()) return true
        return try {
            val cached = scanner.cachedSnapshot()
            if (cached.photos.isNotEmpty()) {
                mutableSummary.value = ScanSummary(
                    totalPhotos = cached.totalPhotos,
                    checkedPhotos = cached.checkedPhotos,
                    totalPhotoBytes = cached.totalBytes,
                    duplicateGroups = cached.groups,
                    photos = cached.photos,
                    scanPhase = cached.phase,
                    isShowingCachedResults = true,
                )
            }
        val startedAt = System.currentTimeMillis()
        mutableSummary.value = mutableSummary.value.copy(
            isScanning = true,
            errorMessage = null,
            scanPhase = "Reading photo library",
            checkedPhotos = 0,
            scanStartedAtMillis = startedAt,
            scanPhaseStartedAtMillis = startedAt,
            isShowingCachedResults = cached.photos.isNotEmpty(),
        )
        var completed = false
        runCatching {
            scanner.scan { progress ->
                val previous = mutableSummary.value
                val phaseStartedAt = if (previous.scanPhase == progress.phase) {
                    previous.scanPhaseStartedAtMillis
                } else {
                    System.currentTimeMillis()
                }
                mutableSummary.value = ScanSummary(
                    totalPhotos = progress.totalPhotos,
                    checkedPhotos = progress.checkedPhotos,
                    totalPhotoBytes = progress.totalBytes,
                    duplicateGroups = progress.groups,
                    photos = progress.photos,
                    scanPhase = progress.phase,
                    scanStartedAtMillis = startedAt,
                    scanPhaseStartedAtMillis = phaseStartedAt,
                    isScanning = true,
                    refreshChanges = progress.refreshChanges,
                    isShowingCachedResults = false,
                )
            }
        }.onSuccess {
            completed = true
            mutableSummary.value = mutableSummary.value.copy(
                isScanning = false,
                scanPhase = if (mutableSummary.value.refreshChanges.hasChanges) {
                    "Library updated"
                } else {
                    "Everything is up to date"
                },
                isShowingCachedResults = false,
            )
        }.onFailure { error ->
            mutableSummary.value = mutableSummary.value.copy(
                isScanning = false,
                errorMessage = error.message ?: "The photo scan could not be completed.",
            )
        }
        completed
        } finally {
            scanMutex.unlock()
        }
    }
}
