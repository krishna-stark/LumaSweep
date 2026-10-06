package com.example.photostorage.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photostorage.domain.ScanSummary
import com.example.photostorage.ui.theme.Mint

@Composable
internal fun SettingsScreen(
    summary: ScanSummary,
    padding: PaddingValues,
    listState: LazyListState,
    onScanAgain: () -> Unit,
) {
    val context = LocalContext.current
    var actionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().liquidBackdrop(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = padding.calculateTopPadding() + 28.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Settings", fontSize = 34.sp, fontWeight = FontWeight.Bold) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Mint.copy(alpha = .12f)),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Lock, null, tint = Mint)
                    Text("On-device by design", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Photo metadata and hashes stay on this phone. The app declares no internet permission and never uploads your library.",
                        color = Color(0xFFC5D0C8),
                    )
                }
            }
        }
        item {
            ActionSettingsRow(
                icon = { Icon(Icons.Default.Refresh, null) },
                title = if (summary.isScanning) "Scan in progress" else "Scan library now",
                detail = if (summary.isScanning) {
                    "${formatCount(summary.checkedPhotos)} of ${formatCount(summary.totalPhotos)} checked"
                } else {
                    "Refresh new, changed, and removed files immediately"
                },
                enabled = !summary.isScanning,
                onClick = {
                    actionMessage = "Refresh requested"
                    onScanAgain()
                },
            )
        }
        item {
            ActionSettingsRow(
                icon = { Icon(Icons.Default.Settings, null) },
                title = "System access & background use",
                detail = "Review photo permission, notifications, battery, and background access",
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
            )
        }
        actionMessage?.let { message ->
            item { Text(message, color = Mint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp)) }
        }
        item {
            Text(
                "HOW LUMASWEEP PROTECTS YOU",
                color = Color(0xFFAAAAAF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Deletion always uses Android system Trash approval. Exact duplicates require matching size, dimensions, type, and SHA-256. Text-heavy photos receive stricter optimization checks.",
                color = Color(0xFFB8B8BD),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ActionSettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    detail: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = if (enabled) .075f else .04f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = .09f)) {
                Box(Modifier.padding(10.dp)) { icon() }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) Color.White else Color(0xFF88888D))
                Text(detail, color = Color(0xFFAAAAAF), fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = if (enabled) Mint else Color(0xFF66666A))
        }
    }
}
