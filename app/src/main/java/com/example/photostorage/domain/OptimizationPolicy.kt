package com.example.photostorage.domain

import kotlin.math.max

/**
 * User-controlled optimization settings. Defaults deliberately match the conservative,
 * high-confidence path used by the app today.
 */
data class OptimizationSettings(
    val quality: QualityPreset = QualityPreset.RECOMMENDED,
    val resolution: ResolutionPreset = ResolutionPreset.RECOMMENDED,
    val customLongEdge: Int = RECOMMENDED_LONG_EDGE,
    val metadata: MetadataPreference = MetadataPreference.PRESERVE,
    val outputFormat: OutputFormatPreference = OutputFormatPreference.AUTOMATIC,
) {
    fun normalized(): OptimizationSettings = copy(
        customLongEdge = customLongEdge.coerceIn(MIN_CUSTOM_LONG_EDGE, MAX_CUSTOM_LONG_EDGE),
    )

    /** Stable identifier carried by previews so a copy cannot be saved under changed settings. */
    fun fingerprint(): String = normalized().let {
        "${it.quality.name}:${it.resolution.name}:${it.customLongEdge}:${it.metadata.name}:${it.outputFormat.name}"
    }

    companion object {
        const val RECOMMENDED_LONG_EDGE = 2560
        const val MIN_CUSTOM_LONG_EDGE = 1600
        const val MAX_CUSTOM_LONG_EDGE = 8192
    }
}

enum class QualityPreset(
    val title: String,
    val description: String,
) {
    MAXIMUM(
        title = "Maximum quality",
        description = "Keeps more fine detail. Savings will usually be smaller.",
    ),
    RECOMMENDED(
        title = "Recommended",
        description = "A careful balance of detail and useful storage savings.",
    ),
    MORE_SAVINGS(
        title = "More savings",
        description = "Creates a smaller copy with more visible change possible.",
    ),
}

enum class ResolutionPreset(
    val title: String,
    val description: String,
) {
    ORIGINAL(
        title = "Original resolution",
        description = "Keeps every pixel and only improves file efficiency.",
    ),
    RECOMMENDED(
        title = "Recommended size",
        description = "Keeps enough detail for phones, sharing and common displays.",
    ),
    CUSTOM(
        title = "Custom size",
        description = "Choose the longest side. Smaller sizes save more space.",
    ),
}

enum class MetadataPreference(
    val title: String,
    val description: String,
) {
    PRESERVE(
        title = "Preserve photo details",
        description = "Keeps date, location and camera information.",
    ),
    REMOVE_OPTIONAL(
        title = "Remove optional details",
        description = "Removes location and camera information but keeps the capture date.",
    ),
}

enum class OutputFormatPreference(
    val title: String,
    val description: String,
) {
    AUTOMATIC(
        title = "Automatic",
        description = "Uses the safest useful format for this photo.",
    ),
    KEEP_SOURCE(
        title = "Keep current format",
        description = "Avoids changing the photo's file type.",
    ),
    JPEG(
        title = "JPEG",
        description = "Compatible with almost every photo app and device.",
    ),
    WEBP(
        title = "WebP",
        description = "May be smaller, but some older apps may not support it.",
    ),
}

/** Encoder-facing values derived from the friendly controls above. */
data class ResolvedOptimizationPolicy(
    val longEdge: Int,
    val minimumQuality: Int,
    val maximumQuality: Int,
    val minimumGlobalSimilarity: Float,
    val minimumTileSimilarity: Float,
    val minimumEdgeSimilarity: Float,
    val minimumPersonalGlobalSimilarity: Float,
    val minimumPersonalTileSimilarity: Float,
    val minimumPersonalEdgeSimilarity: Float,
    val minimumSavingBytes: Long,
    val maximumOutputRatio: Double,
    val metadata: MetadataPreference,
    val outputFormat: ResolvedOutputFormat,
    val settingsFingerprint: String,
)

enum class ResolvedOutputFormat(
    val extension: String,
    val mimeType: String,
) {
    JPEG("jpg", "image/jpeg"),
    WEBP("webp", "image/webp"),
}

object OptimizationPolicy {
    private const val ONE_MIB = 1024L * 1024L

    fun resolve(
        settings: OptimizationSettings,
        sourceMimeType: String,
        sourceWidth: Int,
        sourceHeight: Int,
    ): ResolvedOptimizationPolicy {
        val safeSettings = settings.normalized()
        val sourceLongEdge = max(sourceWidth, sourceHeight).coerceAtLeast(1)
        val requestedLongEdge = when (safeSettings.resolution) {
            ResolutionPreset.ORIGINAL -> sourceLongEdge
            ResolutionPreset.RECOMMENDED -> OptimizationSettings.RECOMMENDED_LONG_EDGE
            ResolutionPreset.CUSTOM -> safeSettings.customLongEdge
        }
        val qualityValues = when (safeSettings.quality) {
            QualityPreset.MAXIMUM -> QualityValues(96, 100, .990f, .970f, .960f, .994f, .980f, .970f, .72)
            QualityPreset.RECOMMENDED -> QualityValues(90, 98, .985f, .950f, .940f, .990f, .970f, .960f, .60)
            QualityPreset.MORE_SAVINGS -> QualityValues(84, 94, .980f, .940f, .920f, .987f, .960f, .945f, .52)
        }
        val format = when (safeSettings.outputFormat) {
            OutputFormatPreference.WEBP -> ResolvedOutputFormat.WEBP
            OutputFormatPreference.JPEG -> ResolvedOutputFormat.JPEG
            OutputFormatPreference.KEEP_SOURCE ->
                if (sourceMimeType.equals("image/webp", true)) ResolvedOutputFormat.WEBP else ResolvedOutputFormat.JPEG
            OutputFormatPreference.AUTOMATIC ->
                if (sourceMimeType.equals("image/webp", true)) ResolvedOutputFormat.WEBP else ResolvedOutputFormat.JPEG
        }
        return ResolvedOptimizationPolicy(
            longEdge = requestedLongEdge.coerceAtMost(sourceLongEdge),
            minimumQuality = qualityValues.minimumQuality,
            maximumQuality = qualityValues.maximumQuality,
            minimumGlobalSimilarity = qualityValues.global,
            minimumTileSimilarity = qualityValues.tile,
            minimumEdgeSimilarity = qualityValues.edge,
            minimumPersonalGlobalSimilarity = qualityValues.personalGlobal,
            minimumPersonalTileSimilarity = qualityValues.personalTile,
            minimumPersonalEdgeSimilarity = qualityValues.personalEdge,
            minimumSavingBytes = ONE_MIB,
            maximumOutputRatio = qualityValues.maximumOutputRatio,
            metadata = safeSettings.metadata,
            outputFormat = format,
            settingsFingerprint = safeSettings.fingerprint(),
        )
    }

    private data class QualityValues(
        val minimumQuality: Int,
        val maximumQuality: Int,
        val global: Float,
        val tile: Float,
        val edge: Float,
        val personalGlobal: Float,
        val personalTile: Float,
        val personalEdge: Float,
        val maximumOutputRatio: Double,
    )
}
