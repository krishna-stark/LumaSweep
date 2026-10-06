package com.example.photostorage.domain

object SimilarityEngine {
    private const val MAX_HASH_DISTANCE = 8
    private const val MAX_TIME_DISTANCE_MS = 15_000L

    fun cluster(photos: List<PhotoRecord>): List<List<PhotoRecord>> {
        val exactHashes = photos.filter { !it.sha256.isNullOrBlank() }
            .groupingBy { it.sha256 }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        val candidates = photos
            .filter {
                !it.perceptualHash.isNullOrBlank() && !it.isScreenshot &&
                    it.sha256 !in exactHashes && it.dateTaken > 0
            }
            .sortedBy { it.dateTaken }
        val used = hashSetOf<Long>()
        val groups = mutableListOf<List<PhotoRecord>>()

        candidates.forEachIndexed { index, anchor ->
            if (anchor.id in used) return@forEachIndexed
            val matches = mutableListOf(anchor)
            for (nextIndex in index + 1 until candidates.size) {
                val candidate = candidates[nextIndex]
                if (candidate.dateTaken > 0 && anchor.dateTaken > 0 &&
                    candidate.dateTaken - anchor.dateTaken > MAX_TIME_DISTANCE_MS) break
                if (compatible(anchor, candidate)) matches += candidate
            }
            if (matches.size > 1) {
                matches.forEach { used += it.id }
                groups += matches
            }
        }
        return groups.sortedByDescending { it.sumOf(PhotoRecord::fileSize) }
    }

    private fun compatible(first: PhotoRecord, second: PhotoRecord): Boolean {
        val timeClose = first.dateTaken <= 0 || second.dateTaken <= 0 ||
            kotlin.math.abs(first.dateTaken - second.dateTaken) <= MAX_TIME_DISTANCE_MS
        val aspectFirst = first.width.toFloat() / first.height.coerceAtLeast(1)
        val aspectSecond = second.width.toFloat() / second.height.coerceAtLeast(1)
        val aspectClose = kotlin.math.abs(aspectFirst - aspectSecond) < .08f
        return timeClose && aspectClose && hamming(first.perceptualHash!!, second.perceptualHash!!) <= MAX_HASH_DISTANCE
    }

    private fun hamming(first: String, second: String): Int = runCatching {
        java.lang.Long.bitCount(first.toULong(16).toLong() xor second.toULong(16).toLong())
    }.getOrDefault(Int.MAX_VALUE)
}
