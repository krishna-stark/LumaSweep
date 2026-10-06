package com.example.photostorage.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenshotClassifierTest {
    @Test fun `uses path and file hints without OCR`() {
        assertEquals(
            ScreenshotCategory.CONVERSATIONS,
            ScreenshotClassifier.classify("Pictures/WhatsApp/", "Screenshot_42.png"),
        )
        assertEquals(
            ScreenshotCategory.MAPS,
            ScreenshotClassifier.classify("Screenshots/", "Google Maps route.png"),
        )
    }

    @Test fun `refines generic screenshots using recognized text`() {
        assertEquals(
            ScreenshotCategory.RECEIPTS,
            ScreenshotClassifier.classify("Screenshots/", "Screenshot.png", "Tax invoice subtotal amount paid"),
        )
        assertEquals(
            ScreenshotCategory.TICKETS,
            ScreenshotClassifier.classify("Screenshots/", "Screenshot.png", "Boarding pass flight seat 18A"),
        )
        assertEquals(
            ScreenshotCategory.CODES,
            ScreenshotClassifier.classify("Screenshots/", "Screenshot.png", "Your verification code is 938201"),
        )
    }

    @Test fun `does not invent a category when evidence is absent`() {
        assertEquals(
            ScreenshotCategory.OTHER,
            ScreenshotClassifier.classify("Screenshots/", "Screenshot_2026.png"),
        )
    }
}
