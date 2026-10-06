package com.example.photostorage.ui

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photostorage.domain.PhotoRecord
import com.example.photostorage.domain.ScreenshotCategory
import com.example.photostorage.ui.theme.ErrorRed
import com.example.photostorage.ui.theme.Forest
import com.example.photostorage.ui.theme.Mint

private data class GallerySection(val title: String?, val photos: List<PhotoRecord>)

/**
 * A batch-first review surface. Nothing is preselected: one tap marks an item for Trash,
 * a long press opens it, and Android still owns the final confirmation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GalleryReviewScreen(
    title: String,
    subtitle: String,
    photos: List<PhotoRecord>,
    onBack: () -> Unit,
    onMoveToTrash: (List<Uri>) -> Unit,
    groups: List<List<PhotoRecord>>? = null,
    showScreenshotCategories: Boolean = false,
    onCreateSearchablePdf: ((List<PhotoRecord>) -> Unit)? = null,
) {
    var selectedIds by rememberSaveable(title) { mutableStateOf<List<Long>>(emptyList()) }
    var previewPhoto by remember { mutableStateOf<PhotoRecord?>(null) }
    var category by rememberSaveable(title) { mutableStateOf<ScreenshotCategory?>(null) }
    val visibleIds = photos.mapTo(hashSetOf()) { it.id }
    val selectedPhotos = photos.filter { it.id in selectedIds && it.id in visibleIds }
    val selectedBytes = selectedPhotos.sumOf { it.fileSize }

    val filteredPhotos = if (category == null) photos else photos.filter { it.screenshotCategory == category }
    val filteredIds = filteredPhotos.map { it.id }
    val allFilteredSelected = filteredIds.isNotEmpty() && filteredIds.all { it in selectedIds }
    val sections = remember(filteredPhotos, groups, category) {
        if (groups != null && category == null) {
            groups.mapIndexed { index, group -> GallerySection("Moment ${index + 1}  •  ${group.size} photos", group) }
        } else {
            listOf(GallerySection(null, filteredPhotos))
        }
    }
    val categories = remember(photos, showScreenshotCategories) {
        if (!showScreenshotCategories) emptyList()
        else photos.groupBy { it.screenshotCategory }.entries.sortedByDescending { it.value.size }
    }

    previewPhoto?.let { photo -> PhotoPeekDialog(photo) { previewPhoto = null } }

    Scaffold(
        containerColor = Forest,
        bottomBar = {
            if (selectedPhotos.isNotEmpty()) {
                Surface(color = Color(0xFF121416), tonalElevation = 8.dp, shadowElevation = 18.dp) {
                    Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${selectedPhotos.size} selected", fontWeight = FontWeight.SemiBold)
                                Text(formatBytes(selectedBytes), color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onMoveToTrash(selectedPhotos.map { it.uri }) },
                                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Move to Trash", fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            "Android will ask once before moving anything.",
                            color = Color(0xFF949B97),
                            fontSize = 10.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            modifier = Modifier.fillMaxSize().liquidBackdrop(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 20.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${photos.size} photos  •  $subtitle", color = Color(0xFFADB4B0), fontSize = 12.sp)
                    }
                    TextButtonLike(if (allFilteredSelected) "Deselect all" else "Select all") {
                        selectedIds = if (allFilteredSelected) {
                            selectedIds - filteredIds.toSet()
                        } else {
                            (selectedIds + filteredIds).distinct()
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .07f)),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Mint.copy(alpha = .16f)) {
                            Icon(Icons.Default.PhotoLibrary, null, tint = Mint, modifier = Modifier.padding(8.dp).size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Tap to select", fontWeight = FontWeight.SemiBold)
                            Text("Long press any photo to inspect it. Nothing is selected by default.", color = Color(0xFFA9B0AC), fontSize = 11.sp)
                        }
                    }
                }
            }

            if (categories.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        contentPadding = PaddingValues(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All ${photos.size}") })
                        }
                        items(categories, key = { it.key.name }) { entry ->
                            FilterChip(
                                selected = category == entry.key,
                                onClick = { category = entry.key },
                                label = { Text("${entry.key.displayName} ${entry.value.size}") },
                            )
                        }
                    }
                }
            }

            if (filteredPhotos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(42.dp), tint = Mint)
                        Spacer(Modifier.height(12.dp))
                        Text("Nothing waiting here", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Protected and previously handled photos stay out of this queue.", color = Color(0xFFA9B0AC), textAlign = TextAlign.Center)
                    }
                }
            }

            sections.forEach { section ->
                if (section.title != null) {
                    item(key = "section-${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            section.title,
                            color = Mint,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp, start = 2.dp),
                        )
                    }
                }
                items(section.photos, key = { it.id }) { photo ->
                    val selected = photo.id in selectedIds
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                            .combinedClickable(
                                onClick = {
                                    selectedIds = if (selected) selectedIds - photo.id else selectedIds + photo.id
                                },
                                onLongClick = { previewPhoto = photo },
                            ),
                    ) {
                        PhotoThumbnail(photo.uri, Modifier.fillMaxSize())
                        if (selected) {
                            Box(Modifier.fillMaxSize().background(ErrorRed.copy(alpha = .25f)))
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                                shape = CircleShape,
                                color = ErrorRed,
                            ) {
                                Icon(Icons.Default.Check, "Selected for Trash", tint = Color.White, modifier = Modifier.padding(5.dp).size(14.dp))
                            }
                        }
                        Text(
                            formatBytes(photo.fileSize),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.BottomStart)
                                .background(Color.Black.copy(alpha = .62f), RoundedCornerShape(topEnd = 8.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            if (onCreateSearchablePdf != null && filteredPhotos.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TextButtonLike("Create searchable PDF from this view") { onCreateSearchablePdf(filteredPhotos) }
                }
            }
        }
    }
}
