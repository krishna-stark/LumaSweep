package com.example.photostorage.domain

/**
 * A deliberately small, deterministic classifier. It is safe to run during discovery using
 * file/path hints, then refine after on-device OCR has already been requested for verification.
 * It never sends image or text data off device.
 */
object ScreenshotClassifier {
    fun classify(relativePath: String, displayName: String, recognizedText: String = ""): ScreenshotCategory {
        val source = "$relativePath $displayName $recognizedText".lowercase()
        return when {
            containsAny(source, "qr code", "barcode", "scan code", "verification code", "otp", "one time password") -> ScreenshotCategory.CODES
            containsAny(source, "boarding pass", "flight", "train", "bus ticket", "event ticket", "seat", "booking id", "pnr") -> ScreenshotCategory.TICKETS
            containsAny(source, "receipt", "invoice", "subtotal", "grand total", "tax invoice", "amount paid") -> ScreenshotCategory.RECEIPTS
            containsAny(source, "google maps", "maps", "directions", "navigate", " km", " miles", "latitude", "longitude") -> ScreenshotCategory.MAPS
            containsAny(source, "whatsapp", "telegram", "signal", "messenger", "messages", " imessage", " chat") -> ScreenshotCategory.CONVERSATIONS
            containsAny(source, "amazon", "flipkart", "myntra", "shopping", "cart", "checkout", "order total", "add to cart") -> ScreenshotCategory.SHOPPING
            containsAny(source, "instagram", "facebook", "twitter", "x.com", "reddit", "snapchat", "linkedin", "tiktok") -> ScreenshotCategory.SOCIAL
            containsAny(source, "document", "certificate", "statement", "application", "agreement", "page ", "signed by") -> ScreenshotCategory.DOCUMENTS
            else -> ScreenshotCategory.OTHER
        }
    }

    private fun containsAny(source: String, vararg signals: String): Boolean = signals.any(source::contains)
}
