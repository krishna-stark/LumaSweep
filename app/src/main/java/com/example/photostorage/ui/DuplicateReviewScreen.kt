package com.example.photostorage.ui

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photostorage.domain.DuplicateGroup
import com.example.photostorage.domain.PhotoRecord
import com.example.photostorage.domain.ScanSummary
import com.example.photostorage.ui.theme.ErrorRed
import com.example.photostorage.ui.theme.Forest
import com.example.photostorage.ui.theme.Mint


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DuplicateReviewScreen(
    summary: ScanSummary,
    onBack: () -> Unit,
    onMoveToTrash: (List<Uri>) -> Unit,
    listState: LazyListState,
) {
    val defaultSelection = remember(summary.duplicateGroups) {
        summary.duplicateGroups.flatMap { group ->
            group.photos.filterNot { it.id == group.suggestedKeepId }.map { it.id }
        }
    }
    val duplicateKey = summary.duplicateGroups.map { it.hash to it.photos.map(PhotoRecord::id) }
    var selected by rememberSaveable(duplicateKey) { mutableStateOf(defaultSelection) }
    val selectedPhotos = summary.duplicateGroups.flatMap { it.photos }.filter { it.id in selected }
    val selectedBytes = selectedPhotos.sumOf { it.fileSize }

    Scaffold(
        containerColor = Forest,
        bottomBar = {
            Surface(color = Color(0xFF101713), shadowElevation = 12.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${selectedPhotos.size} copies selected", color = Color(0xFFB6C0B8))
                        Text(formatBytes(selectedBytes), color = Mint, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onMoveToTrash(selectedPhotos.map { it.uri }) },
                        enabled = selectedPhotos.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                    ) {
                        Icon(Icons.Default.DeleteOutline, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Move copies to system Trash", fontWeight = FontWeight.Bold)
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        Text(
                            "Safe batch Trash requires Android 11 or newer. No files will be permanently deleted.",
                            color = Color(0xFFB6C0B8), fontSize = 11.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text("Exact duplicates", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Byte-for-byte matches only", color = Color(0xFFB8C1BB), fontSize = 12.sp)
                    }
                    TextButtonLike(
                        text = if (selected.toSet() == defaultSelection.toSet()) "Deselect all" else "Select all",
                        onClick = {
                            selected = if (selected.toSet() == defaultSelection.toSet()) emptyList() else defaultSelection
                        },
                    )
                }
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Mint.copy(alpha = .12f)),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, tint = Mint)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "One copy is always kept. You can change the selection before Android asks for final Trash approval.",
                            color = Color(0xFFD6F8E4), fontSize = 13.sp,
                        )
                    }
                }
            }
            if (summary.duplicateGroups.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(52.dp), tint = Mint)
                        Spacer(Modifier.height(18.dp))
                        Text("No exact duplicates", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Your unique photos are untouched.", color = Color(0xFFB6C0B8))
                    }
                }
            }
            items(summary.duplicateGroups, key = { it.hash }) { group ->
                DuplicateGroupCard(
                    group = group,
                    selected = selected,
                    onToggle = { photo ->
                        val selectedInGroup = group.photos.count { it.id in selected }
                        selected = if (photo.id in selected) {
                            selected - photo.id
                        } else if (selectedInGroup < group.photos.size - 1) {
                            (selected + photo.id).distinct()
                        } else {
                            selected
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    selected: Collection<Long>,
    onToggle: (PhotoRecord) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .075f)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Same photo", fontWeight = FontWeight.Bold)
                    Text("${group.photos.size} copies", color = Color(0xFFAFB9B1), fontSize = 12.sp)
                }
                Surface(shape = RoundedCornerShape(20.dp), color = Mint.copy(alpha = .14f)) {
                    Text(
                        "Save ${formatBytes(group.photos.filter { it.id in selected }.sumOf { it.fileSize })}",
                        color = Mint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(group.photos, key = { it.id }) { photo ->
                    val marked = photo.id in selected
                    Box(
                        modifier = Modifier
                            .width(104.dp)
                            .aspectRatio(.78f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                if (marked) 2.dp else 0.dp,
                                if (marked) ErrorRed else Color.Transparent,
                                RoundedCornerShape(14.dp),
                            )
                            .clickable { onToggle(photo) },
                    ) {
                        PhotoThumbnail(photo.uri, Modifier.fillMaxSize())
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                            shape = CircleShape,
                            color = if (marked) ErrorRed else Mint,
                        ) {
                            Icon(
                                if (marked) Icons.Default.DeleteOutline else Icons.Default.Check,
                                contentDescription = if (marked) "Selected for Trash" else "Keep",
                                tint = if (marked) Color.White else Forest,
                                modifier = Modifier.padding(5.dp).size(14.dp),
                            )
                        }
                        Text(
                            if (marked) "TRASH" else "KEEP",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = .64f))
                                .padding(vertical = 6.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
