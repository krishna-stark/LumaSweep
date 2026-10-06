package com.example.photostorage.domain

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class ExactDuplicateEngineTest {
    @Test
    fun `only matching metadata becomes hash candidates`() {
        val sameA = photo(1, size = 1_000, width = 100, height = 100)
        val sameB = photo(2, size = 1_000, width = 100, height = 100)
        val differentDimensions = photo(3, size = 1_000, width = 200, height = 100)

        val groups = ExactDuplicateEngine.candidateGroups(listOf(sameA, sameB, differentDimensions))

        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L), groups.single().map { it.id }.toSet())
    }

    @Test
    fun `recoverable storage excludes exactly one kept copy`() {
        val items = listOf(
            photo(1, size = 5_000, hash = "same", path = "Download/"),
            photo(2, size = 5_000, hash = "same", path = "DCIM/Camera/"),
            photo(3, size = 5_000, hash = "same", path = "Pictures/"),
        )

        val group = ExactDuplicateEngine.duplicateGroups(items).single()

        assertEquals(10_000, group.recoverableBytes)
        assertEquals(2L, group.suggestedKeepId)
    }

    @Test
    fun `different hashes never form a duplicate group`() {
        val groups = ExactDuplicateEngine.duplicateGroups(
            listOf(photo(1, hash = "one"), photo(2, hash = "two"))
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `near visual hashes in the same moment form a review cluster`() {
        val first = photo(1, hash = "unique-1").copy(
            perceptualHash = "0000000000000000",
            dateTaken = 10_000,
        )
        val second = photo(2, hash = "unique-2").copy(
            perceptualHash = "0000000000000003",
            dateTaken = 12_000,
        )

        val groups = SimilarityEngine.cluster(listOf(first, second))

        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L), groups.single().map { it.id }.toSet())
    }

    @Test
    fun `estimated optimization is separate from guaranteed savings`() {
        val screenshot = photo(1, size = 8_000).copy(isScreenshot = true)
        val optimizable = photo(2, size = 10_000).copy(optimizedBytes = 6_000)
        val summary = ScanSummary(photos = listOf(screenshot, optimizable))

        assertEquals(0, summary.guaranteedSavingBytes)
        assertEquals(4_000, summary.verifiedTotalSavingBytes)
        assertEquals(8_000, summary.reviewCandidateBytes)
    }

    private fun photo(
        id: Long,
        size: Long = 1_000,
        width: Int = 100,
        height: Int = 100,
        hash: String? = null,
        path: String = "Pictures/",
    ) = PhotoRecord(
        id = id,
        uri = mock(Uri::class.java),
        displayName = "$id.jpg",
        mimeType = "image/jpeg",
        width = width,
        height = height,
        fileSize = size,
        dateTaken = 1_000,
        dateModified = 1_000,
        relativePath = path,
        sha256 = hash,
    )
}
