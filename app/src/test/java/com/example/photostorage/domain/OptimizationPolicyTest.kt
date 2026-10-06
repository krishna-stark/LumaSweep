package com.example.photostorage.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptimizationPolicyTest {
    @Test
    fun defaults_areConservativeAndPreserveMetadata() {
        val settings = OptimizationSettings()
        val policy = OptimizationPolicy.resolve(settings, "image/jpeg", 4032, 3024)

        assertEquals(QualityPreset.RECOMMENDED, settings.quality)
        assertEquals(2560, policy.longEdge)
        assertEquals(90, policy.minimumQuality)
        assertEquals(98, policy.maximumQuality)
        assertEquals(MetadataPreference.PRESERVE, policy.metadata)
        assertEquals(ResolvedOutputFormat.JPEG, policy.outputFormat)
        assertTrue(policy.minimumGlobalSimilarity >= .985f)
    }

    @Test
    fun maximumQuality_isStricterThanRecommended() {
        val recommended = OptimizationPolicy.resolve(OptimizationSettings(), "image/jpeg", 4000, 3000)
        val maximum = OptimizationPolicy.resolve(
            OptimizationSettings(quality = QualityPreset.MAXIMUM),
            "image/jpeg",
            4000,
            3000,
        )

        assertTrue(maximum.minimumQuality > recommended.minimumQuality)
        assertTrue(maximum.minimumGlobalSimilarity > recommended.minimumGlobalSimilarity)
        assertTrue(maximum.maximumOutputRatio > recommended.maximumOutputRatio)
    }

    @Test
    fun requestedResolution_neverUpscalesSource() {
        val original = OptimizationPolicy.resolve(
            OptimizationSettings(resolution = ResolutionPreset.ORIGINAL),
            "image/jpeg",
            1800,
            1200,
        )
        val custom = OptimizationPolicy.resolve(
            OptimizationSettings(resolution = ResolutionPreset.CUSTOM, customLongEdge = 6000),
            "image/jpeg",
            1800,
            1200,
        )

        assertEquals(1800, original.longEdge)
        assertEquals(1800, custom.longEdge)
    }

    @Test
    fun invalidCustomResolution_isClampedAndFingerprintIsStable() {
        val invalid = OptimizationSettings(
            resolution = ResolutionPreset.CUSTOM,
            customLongEdge = 100,
            metadata = MetadataPreference.REMOVE_OPTIONAL,
        )
        val normalized = invalid.normalized()

        assertEquals(OptimizationSettings.MIN_CUSTOM_LONG_EDGE, normalized.customLongEdge)
        assertEquals(normalized.fingerprint(), invalid.fingerprint())
    }

    @Test
    fun explicitWebp_changesResolvedOutput() {
        val policy = OptimizationPolicy.resolve(
            OptimizationSettings(outputFormat = OutputFormatPreference.WEBP),
            "image/jpeg",
            4000,
            3000,
        )

        assertEquals(ResolvedOutputFormat.WEBP, policy.outputFormat)
        assertEquals("image/webp", policy.outputFormat.mimeType)
        assertEquals("webp", policy.outputFormat.extension)
    }
}
