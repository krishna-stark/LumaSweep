package com.example.photostorage.domain

object ExactDuplicateEngine {
    data class CandidateKey(
        val size: Long,
        val width: Int,
        val height: Int,
        val mimeType: String,
    )

    fun candidateGroups(items: List<PhotoRecord>): List<List<PhotoRecord>> = items
        .filter { it.fileSize > 0 }
        .groupBy { CandidateKey(it.fileSize, it.width, it.height, it.mimeType) }
        .values
        .filter { it.size > 1 }

    fun duplicateGroups(items: List<PhotoRecord>): List<DuplicateGroup> = items
        .filter { !it.sha256.isNullOrBlank() }
        .groupBy { it.sha256!! }
        .filterValues { it.size > 1 }
        .map { (hash, copies) ->
            DuplicateGroup(
                hash = hash,
                photos = copies.sortedWith(keepPreference),
                suggestedKeepId = copies.minWith(keepPreference).id,
            )
        }
        .sortedByDescending { it.recoverableBytes }

    private val keepPreference = compareByDescending<PhotoRecord> { metadataScore(it) }
        .thenByDescending { it.width.toLong() * it.height }
        .thenBy { it.dateTaken.takeIf { date -> date > 0 } ?: Long.MAX_VALUE }
        .thenByDescending { isCameraSource(it.relativePath) }
        .thenBy { it.id }

    private fun metadataScore(photo: PhotoRecord): Int =
        listOf(photo.dateTaken > 0, photo.width > 0, photo.height > 0, photo.mimeType.isNotBlank())
            .count { it }

    private fun isCameraSource(path: String): Boolean {
        val normalized = path.lowercase()
        return normalized.contains("dcim") || normalized.contains("camera")
    }
}
