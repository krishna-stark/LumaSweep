package com.example.photostorage.domain

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class ScanSummaryTest {
    @Test
    fun `guaranteed savings contain duplicates only while total also contains estimates`() {
        val keptDuplicate = photo(id = 1, size = 10_000, optimizedBytes = 6_000)
        val redundantDuplicate = photo(id = 2, size = 10_000, optimizedBytes = 6_000)
        val estimatedCandidate = photo(id = 3, size = 8_000, optimizedBytes = 5_000)
        val duplicateGroup = DuplicateGroup(
            hash = "same",
            photos = listOf(keptDuplicate, redundantDuplicate),
            suggestedKeepId = keptDuplicate.id,
        )

        val summary = ScanSummary(
            duplicateGroups = listOf(duplicateGroup),
            photos = listOf(keptDuplicate, redundantDuplicate, estimatedCandidate),
        )

        assertEquals(10_000, summary.guaranteedSavingBytes)
        assertEquals(7_000, summary.optimizationSavingBytes)
        assertEquals(17_000, summary.verifiedTotalSavingBytes)
    }

    @Test
    fun `redundant duplicate is excluded from optimization candidates`() {
        val keptDuplicate = photo(id = 1, size = 10_000, optimizedBytes = 6_000)
        val redundantDuplicate = photo(id = 2, size = 10_000, optimizedBytes = 5_000)
        val duplicateGroup = DuplicateGroup(
            hash = "same",
            photos = listOf(keptDuplicate, redundantDuplicate),
            suggestedKeepId = keptDuplicate.id,
        )

        val summary = ScanSummary(
            duplicateGroups = listOf(duplicateGroup),
            photos = listOf(keptDuplicate, redundantDuplicate),
        )

        assertEquals(listOf(keptDuplicate.id), summary.optimizationCandidates.map { it.id })
        assertFalse(summary.optimizationCandidates.any { it.id == redundantDuplicate.id })
        assertEquals(4_000, summary.optimizationSavingBytes)
    }

    @Test
    fun `invalid estimates never become optimization candidates`() {
        val noEstimate = photo(id = 1, size = 10_000, optimizedBytes = 0)
        val noSaving = photo(id = 2, size = 10_000, optimizedBytes = 10_000)
        val largerOutput = photo(id = 3, size = 10_000, optimizedBytes = 12_000)

        val summary = ScanSummary(photos = listOf(noEstimate, noSaving, largerOutput))

        assertTrue(summary.optimizationCandidates.isEmpty())
        assertEquals(0, summary.optimizationSavingBytes)
        assertEquals(0, summary.verifiedTotalSavingBytes)
    }

    @Test
    fun `optimization candidates are ordered by largest estimated saving`() {
        val smallSaving = photo(id = 1, size = 10_000, optimizedBytes = 8_000)
        val largestSaving = photo(id = 2, size = 12_000, optimizedBytes = 5_000)
        val mediumSaving = photo(id = 3, size = 9_000, optimizedBytes = 5_000)

        val summary = ScanSummary(photos = listOf(smallSaving, largestSaving, mediumSaving))

        assertEquals(listOf(2L, 3L, 1L), summary.optimizationCandidates.map { it.id })
    }

    @Test
    fun `unverified estimates never appear as actionable candidates`() {
        val estimate = photo(id = 1, size = 10_000, optimizedBytes = 5_000)
            .copy(optimizationStatus = OptimizationStatus.ESTIMATED)

        val summary = ScanSummary(photos = listOf(estimate))

        assertTrue(summary.optimizationCandidates.isEmpty())
        assertEquals(listOf(estimate.id), summary.optimizationEstimates.map { it.id })
    }

    @Test
    fun `verified savings are separated into lossless and smaller copy categories`() {
        val lossless = photo(id = 1, size = 10_000, optimizedBytes = 6_000)
            .copy(optimizationMode = OptimizationMode.LOSSLESS_SCREENSHOT)
        val smallerCopy = photo(id = 2, size = 12_000, optimizedBytes = 7_000)
            .copy(optimizationMode = OptimizationMode.PHOTO_DOWNSIZE)

        val summary = ScanSummary(photos = listOf(lossless, smallerCopy))

        assertEquals(listOf(lossless.id), summary.losslessOptimizationCandidates.map { it.id })
        assertEquals(listOf(smallerCopy.id), summary.smallerCopyCandidates.map { it.id })
        assertEquals(listOf(smallerCopy.id), summary.standardOversizedCopyCandidates.map { it.id })
        assertEquals(4_000, summary.losslessOptimizationSavingBytes)
        assertEquals(5_000, summary.smallerCopySavingBytes)
    }

    @Test
    fun `subjective cleanup queues are exclusive and protect prior decisions`() {
        val screenshot = photo(id = 1, size = 8_000).copy(isScreenshot = true, blurScore = 10f)
        val blurry = photo(id = 2, size = 7_000).copy(blurScore = 10f)
        val favorite = photo(id = 3, size = 6_000).copy(blurScore = 10f, isFavorite = true)
        val optimized = photo(id = 4, size = 10_000, optimizedBytes = 5_000).copy(blurScore = 10f)
        val keptDuplicate = photo(id = 5, size = 9_000).copy(isScreenshot = true)
        val redundantDuplicate = photo(id = 6, size = 9_000).copy(isScreenshot = true)
        val duplicates = DuplicateGroup("same", listOf(keptDuplicate, redundantDuplicate), keptDuplicate.id)

        val queues = ScanSummary(
            photos = listOf(screenshot, blurry, favorite, optimized, keptDuplicate, redundantDuplicate),
            duplicateGroups = listOf(duplicates),
        ).cleanupQueues

        assertEquals(listOf(screenshot.id), queues.screenshots.map { it.id })
        assertEquals(listOf(blurry.id), queues.blurry.map { it.id })
        assertEquals(2, queues.allPhotos.size)
    }

    @Test
    fun `review opportunity bytes count a photo once across overlapping categories`() {
        val screenshotAndBlurry = photo(id = 1, size = 8_000).copy(
            isScreenshot = true,
            blurScore = 10f,
        )
        val blurry = photo(id = 2, size = 6_000).copy(blurScore = 10f)

        val summary = ScanSummary(photos = listOf(screenshotAndBlurry, blurry))

        assertEquals(14_000, summary.reviewCandidateBytes)
    }

    @Test
    fun `refresh changes include additions modifications and removals`() {
        val changes = RefreshChanges(
            newPhotos = 34,
            changedPhotos = 12,
            deletedPhotos = 3,
        )

        assertTrue(changes.hasChanges)
        assertEquals(49, changes.totalChanges)
    }

    @Test
    fun `empty refresh changes report library as unchanged`() {
        val changes = RefreshChanges()

        assertFalse(changes.hasChanges)
        assertEquals(0, changes.totalChanges)
    }

    private fun photo(
        id: Long,
        size: Long,
        optimizedBytes: Long = 0,
    ) = PhotoRecord(
        id = id,
        uri = mock(Uri::class.java),
        displayName = "$id.jpg",
        mimeType = "image/jpeg",
        width = 4_000,
        height = 3_000,
        fileSize = size,
        dateTaken = 1_000,
        dateModified = 1_000,
        relativePath = "Pictures/",
        optimizedBytes = optimizedBytes,
    )
}
