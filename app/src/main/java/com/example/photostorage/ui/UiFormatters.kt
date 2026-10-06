package com.example.photostorage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.photostorage.domain.ScanSummary
import com.example.photostorage.ui.theme.ErrorRed
import com.example.photostorage.ui.theme.Forest
import java.text.DecimalFormat
import kotlin.math.ceil

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    val pattern = if (value >= 100 || unitIndex == 0) "0" else if (value >= 10) "0.0" else "0.00"
    return "${DecimalFormat(pattern).format(value)} ${units[unitIndex]}"
}

internal fun formatCount(value: Int): String = DecimalFormat("#,###").format(value)

internal fun estimatedTimeRemaining(summary: ScanSummary, now: Long): String {
    if (!summary.scanPhase.contains("Analyzing", ignoreCase = true)) {
        return if (summary.scanPhase.contains("duplicate", ignoreCase = true)) {
            "Verifying copies…"
        } else {
            "Preparing scan…"
        }
    }
    val checked = summary.checkedPhotos
    val remaining = (summary.totalPhotos - checked).coerceAtLeast(0)
    val elapsed = (now - summary.scanPhaseStartedAtMillis).coerceAtLeast(0)
    if (summary.scanPhaseStartedAtMillis == 0L || elapsed < 8_000 || checked < 8 || remaining == 0) {
        return if (remaining == 0) "Finishing…" else "Learning scan speed…"
    }
    val remainingMillis = ((elapsed.toDouble() / checked) * remaining)
        .toLong()
        .coerceAtMost(24 * 60 * 60 * 1_000L)
    val minutes = ceil(remainingMillis / 60_000.0).toInt()
    return when {
        minutes <= 1 -> "About a minute left"
        minutes < 60 -> "About $minutes min left"
        minutes % 60 == 0 -> "About ${minutes / 60} hr left"
        else -> "About ${minutes / 60} hr ${minutes % 60} min left"
    }
}

internal fun Modifier.liquidBackdrop(): Modifier = background(
    Brush.radialGradient(
        colors = listOf(Color(0xFF303035), Color(0xFF171719), Forest),
        center = Offset(180f, 40f),
        radius = 1050f,
    )
)

internal fun Modifier.glassBorder(): Modifier = border(
    width = 1.dp,
    brush = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = .30f),
            ErrorRed.copy(alpha = .10f),
            Color.White.copy(alpha = .04f),
        )
    ),
    shape = RoundedCornerShape(22.dp),
)
