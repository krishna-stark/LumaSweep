package com.example.photostorage.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.photostorage.ui.theme.Amber
import com.example.photostorage.ui.theme.Blue
import com.example.photostorage.ui.theme.ErrorRed
import com.example.photostorage.ui.theme.Mint
import com.example.photostorage.ui.theme.Violet

/** One visual vocabulary shared by Home and Review. */
internal enum class FindingKind(val color: Color, val icon: ImageVector) {
    DUPLICATES(Mint, Icons.Default.ContentCopy),
    LOSSLESS(Blue, Icons.Default.Screenshot),
    OPTIMIZE(Amber, Icons.Default.Compress),
    SIMILAR(Violet, Icons.Default.Collections),
    SCREENSHOTS(Blue, Icons.Default.Screenshot),
    BLURRY(ErrorRed, Icons.Default.BlurOn),
}
