package com.example.photostorage.ui

import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photostorage.data.LocalImageAnalyzer
import com.example.photostorage.domain.OptimizationMode
import com.example.photostorage.domain.OptimizationSettings
import com.example.photostorage.domain.PhotoRecord
import com.example.photostorage.domain.QualityPreset
import com.example.photostorage.domain.ScanSummary
import com.example.photostorage.ui.theme.Amber
import com.example.photostorage.ui.theme.ErrorRed
import com.example.photostorage.ui.theme.Forest
import com.example.photostorage.ui.theme.Mint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun OptimizationReviewScreen(
    summary: ScanSummary,
    onBack: () -> Unit,
    onOptimize: (PhotoRecord, LocalImageAnalyzer.EncodedPhoto?, OptimizationSettings) -> Unit,
    onOptimizeMany: (List<PhotoRecord>, OptimizationSettings) -> Unit,
    listState: LazyListState,
) {
    var qualityName by rememberSaveable { mutableStateOf(QualityPreset.RECOMMENDED.name) }
    val quality = QualityPreset.valueOf(qualityName)
    val settings = remember(quality) { OptimizationSettings(quality = quality) }
    val verified = summary.optimizationCandidates
    val resultKey = verified.map { it.id to it.dateModified }
    val recommendedIds = verified.filterNot { it.optimizationRequiresReview }.map { it.id }
    var selectedIds by rememberSaveable(resultKey) { mutableStateOf(recommendedIds) }
    var individuallyReviewedIds by rememberSaveable(resultKey) { mutableStateOf<List<Long>>(emptyList()) }
    var preview by remember { mutableStateOf<PhotoRecord?>(null) }
    val selectableIds = verified.filter {
        !it.optimizationRequiresReview || it.id in individuallyReviewedIds
    }.map { it.id }
    val allSelectableSelected = selectableIds.isNotEmpty() && selectableIds.all { it in selectedIds }

    preview?.let { photo ->
        BackHandler { preview = null }
        OptimizationDetail(
            photo = photo,
            settings = settings,
            onBack = { preview = null },
            onOptimize = { candidate, chosenSettings -> onOptimize(photo, candidate, chosenSettings) },
        )
        return
    }

    val selected = verified.filter { it.id in selectedIds }
    Scaffold(
        containerColor = Forest,
        bottomBar = {
            if (verified.isNotEmpty() && !summary.isScanning) {
                Surface(color = Color(0xEE101713)) {
                    Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${selected.size} selected", color = Color(0xFFB6C0B8))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    formatBytes(selected.sumOf { it.fileSize - it.optimizedBytes }),
                                    color = Mint,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text("measured baseline", color = Color(0xFF89948D), fontSize = 9.sp)
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Button(
                            onClick = { onOptimizeMany(selected, settings) },
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && selected.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF7F4EE), contentColor = Forest),
                        ) {
                            Text(
                                if (selected.isEmpty()) "Select a result to continue"
                                else "Create ${selected.size} • ${quality.title}",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            "One final action. Originals move to system Trash only after Android asks for confirmation.",
                            color = Color(0xFF99A69D),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().liquidBackdrop(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 12.dp, 16.dp, padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DetailHeader(
                    "Safe space recovery",
                    if (summary.isScanning) summary.scanPhase else "Measured results, ready for one decision",
                    onBack,
                )
            }
            item {
                GlassMetric(
                    if (summary.isScanning) "Verified so far" else "Ready to recover",
                    formatBytes(summary.optimizationSavingBytes),
                    if (summary.isScanning) {
                        "${verified.size} verified • ${summary.optimizationEstimates.size} still being checked"
                    } else {
                        "${verified.size} photo${if (verified.size == 1) "" else "s"} passed real encoding and quality checks"
                    },
                )
            }
            if (verified.isNotEmpty()) {
                item {
                    QualityControls(
                        selected = quality,
                        onSelected = { qualityName = it.name },
                    )
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .055f)),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Selection", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (selectableIds.size == verified.size) "${selected.size} of ${verified.size} selected"
                                    else "${selected.size} selected • ${verified.size - selectableIds.size} need a quick preview",
                                    color = Color(0xFFAAB4AD),
                                    fontSize = 11.sp,
                                )
                            }
                            TextButtonLike(if (allSelectableSelected) "Deselect all" else "Select all") {
                                selectedIds = if (allSelectableSelected) {
                                    selectedIds - selectableIds.toSet()
                                } else {
                                    (selectedIds + selectableIds).distinct()
                                }
                            }
                        }
                    }
                }
            }
            if (summary.isScanning) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().glassBorder(),
                        colors = CardDefaults.cardColors(containerColor = Amber.copy(alpha = .09f)),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Results appear as the scan verifies them", fontWeight = FontWeight.Bold)
                            LinearProgressIndicator(
                                progress = { summary.overallProgress },
                                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                                color = Amber,
                                trackColor = Color.White.copy(alpha = .10f),
                            )
                            Text("You can leave this page. Opening it again will not restart or repeat the work.", color = Color(0xFFB9C4BC), fontSize = 12.sp)
                        }
                    }
                }
            }
            if (!summary.isScanning && verified.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().glassBorder(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .07f)),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Nothing safe to shrink", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Estimated candidates that failed the real size or quality check were removed automatically.", color = Color(0xFFB9C4BC), fontSize = 12.sp)
                        }
                    }
                }
            }
            items(verified, key = { it.id }) { photo ->
                val isSelected = photo.id in selectedIds
                Card(
                    modifier = Modifier.fillMaxWidth().glassBorder().clickable {
                        individuallyReviewedIds = (individuallyReviewedIds + photo.id).distinct()
                        preview = photo
                    },
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .075f)),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PhotoThumbnail(photo.uri, Modifier.size(82.dp).clip(RoundedCornerShape(16.dp)))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(photo.displayName, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (photo.optimizationMode == OptimizationMode.LOSSLESS_SCREENSHOT) {
                                    "Lossless • original dimensions"
                                } else {
                                    "Smaller copy • ${photo.optimizedWidth} × ${photo.optimizedHeight}"
                                },
                                color = Color(0xFFADB9B0),
                                fontSize = 11.sp,
                            )
                            Text(
                                "${formatBytes(photo.fileSize)} → ${formatBytes(photo.optimizedBytes)} measured",
                                color = Color(0xFFADB9B0),
                                fontSize = 11.sp,
                            )
                            if (photo.optimizationRequiresReview) {
                                Text("People or pets • tap to compare first", color = Amber, fontSize = 10.sp)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Save\n${formatBytes(photo.fileSize - photo.optimizedBytes)}", color = Mint, textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(Modifier.height(7.dp))
                            Surface(
                                modifier = Modifier.clickable {
                                    if (photo.optimizationRequiresReview && photo.id !in individuallyReviewedIds) {
                                        individuallyReviewedIds = (individuallyReviewedIds + photo.id).distinct()
                                        preview = photo
                                    } else {
                                        selectedIds = if (isSelected) selectedIds - photo.id else (selectedIds + photo.id).distinct()
                                    }
                                },
                                shape = CircleShape,
                                color = if (isSelected) Mint else Color.White.copy(alpha = .10f),
                            ) {
                                Icon(
                                    if (isSelected) Icons.Default.Check else Icons.Default.Photo,
                                    contentDescription = if (isSelected) "Deselect" else "Select",
                                    tint = if (isSelected) Forest else Color.White,
                                    modifier = Modifier.padding(7.dp).size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityControls(
    selected: QualityPreset,
    onSelected: (QualityPreset) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().glassBorder(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .07f)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Output quality", fontWeight = FontWeight.Bold)
                    Text("Recommended balances detail and useful savings", color = Color(0xFFAAB4AD), fontSize = 11.sp)
                }
                if (selected == QualityPreset.RECOMMENDED) {
                    Text("RECOMMENDED", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            QualitySlider(selected = selected, onSelected = onSelected)
            Text(
                selected.description + if (selected == QualityPreset.MORE_SAVINGS) {
                    " More visible change is possible."
                } else "",
                color = if (selected == QualityPreset.MORE_SAVINGS) Amber else Color(0xFFB9C4BC),
                fontSize = 12.sp,
            )
            Text(
                "Every selected photo is encoded and checked again before anything is saved. Lossless screenshots remain pixel-identical.",
                color = Color(0xFF8F9A93),
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun QualitySlider(
    selected: QualityPreset,
    onSelected: (QualityPreset) -> Unit,
) {
    val value = when (selected) {
        QualityPreset.MORE_SAVINGS -> 0f
        QualityPreset.RECOMMENDED -> 1f
        QualityPreset.MAXIMUM -> 2f
    }
    Slider(
        value = value,
        onValueChange = { position ->
            onSelected(
                when {
                    position < .5f -> QualityPreset.MORE_SAVINGS
                    position < 1.5f -> QualityPreset.RECOMMENDED
                    else -> QualityPreset.MAXIMUM
                }
            )
        },
        valueRange = 0f..2f,
        steps = 1,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("LOW\nMore savings", color = Amber, fontSize = 10.sp)
        Text("RECOMMENDED", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("HIGH\nMore detail", color = Color(0xFFB9C4BC), fontSize = 10.sp, textAlign = TextAlign.End)
    }
}

@Composable
private fun OptimizationDetail(
    photo: PhotoRecord,
    preverifiedCandidate: LocalImageAnalyzer.EncodedPhoto? = null,
    settings: OptimizationSettings = OptimizationSettings(),
    onBack: () -> Unit,
    onOptimize: (LocalImageAnalyzer.EncodedPhoto, OptimizationSettings) -> Unit,
) {
    val context = LocalContext.current
    var qualityName by rememberSaveable(photo.id) { mutableStateOf(settings.quality.name) }
    val selectedQuality = QualityPreset.valueOf(qualityName)
    val activeSettings = remember(settings, selectedQuality) { settings.copy(quality = selectedQuality) }
    val originalBitmap by produceState<android.graphics.Bitmap?>(null, photo.id) {
        value = withContext(Dispatchers.IO) {
            context.contentResolver.fullPreview(photo)
        }
    }
    val candidateResult by produceState<Pair<Boolean, LocalImageAnalyzer.EncodedPhoto?>>(false to null, photo.id, preverifiedCandidate, activeSettings.fingerprint()) {
        value = if (preverifiedCandidate?.settingsFingerprint == activeSettings.fingerprint()) {
            true to preverifiedCandidate
        } else withContext(Dispatchers.IO) {
            val analyzer = LocalImageAnalyzer(context)
            true to (analyzer.materializeVerifiedCandidate(photo, activeSettings)
                ?: analyzer.encodeCandidate(photo = photo, settings = activeSettings))
        }
    }
    val verificationFinished = candidateResult.first
    val actualCandidate = candidateResult.second
    val optimizedBitmap = remember(actualCandidate) {
        actualCandidate?.let { BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size) }
    }
    val actualSaving = actualCandidate?.let { photo.fileSize - it.size } ?: 0L
    var split by remember { mutableFloatStateOf(.5f) }
    var detailZoom by remember(photo.id) { mutableFloatStateOf(3f) }
    var detailPan by remember(photo.id) { mutableStateOf(Offset.Zero) }
    Scaffold(
        containerColor = Forest,
        bottomBar = {
            Surface(color = Color(0xCC101713)) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    Button(
                        onClick = { actualCandidate?.let { onOptimize(it, activeSettings) } },
                        enabled = optimizedBitmap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF7F4EE), contentColor = Forest),
                    ) {
                        Text(
                            when {
                                !verificationFinished -> "Preparing preview…"
                                actualCandidate == null -> "This photo cannot be safely optimized"
                                else -> "Use this checked copy • Save ${formatBytes(actualSaving)}"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text("After the smaller copy is saved and checked, Android asks to move the original to system Trash.", color = Color(0xFF9FAAA2), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().liquidBackdrop().verticalScroll(rememberScrollState()).padding(16.dp)
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DetailHeader("Before / After", photo.displayName, onBack)
            when {
                !verificationFinished -> Text("Preparing a preview from the saved verification result…", color = Amber, fontSize = 12.sp)
                actualCandidate == null -> Text("The source changed since the scan, so it must be scanned again before optimization.", color = ErrorRed, fontSize = 12.sp)
                else -> Text("Actual result verified • ${formatBytes(actualSaving)} can be saved", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            BoxWithConstraints(
                Modifier.fillMaxWidth()
                    .aspectRatio((photo.width.toFloat() / photo.height.coerceAtLeast(1)).coerceIn(.65f, 1.5f))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black)
                    .pointerInput(photo.id) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            val nextZoom = (detailZoom * zoomChange).coerceIn(1f, 6f)
                            val maxPanX = size.width * (nextZoom - 1f) / 2f
                            val maxPanY = size.height * (nextZoom - 1f) / 2f
                            detailZoom = nextZoom
                            detailPan = Offset(
                                (detailPan.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                (detailPan.y + panChange.y).coerceIn(-maxPanY, maxPanY),
                            )
                        }
                    },
            ) {
                val previewWidth = maxWidth
                if (optimizedBitmap != null && originalBitmap != null) {
                    val originalImage = originalBitmap!!.asImageBitmap()
                    val optimizedImage = optimizedBitmap!!.asImageBitmap()
                    Canvas(Modifier.fillMaxSize()) {
                        fun fittedSize(imageWidth: Int, imageHeight: Int): Pair<IntOffset, IntSize> {
                            val scale = minOf(size.width / imageWidth, size.height / imageHeight)
                            val width = (imageWidth * scale).toInt().coerceAtLeast(1)
                            val height = (imageHeight * scale).toInt().coerceAtLeast(1)
                            return IntOffset(((size.width - width) / 2f).toInt(), ((size.height - height) / 2f).toInt()) to IntSize(width, height)
                        }
                        val (originalOffset, originalSize) = fittedSize(originalImage.width, originalImage.height)
                        withTransform({
                            translate(detailPan.x, detailPan.y)
                            scale(detailZoom, detailZoom, pivot = center)
                        }) {
                            drawImage(originalImage, dstOffset = originalOffset, dstSize = originalSize)
                        }
                        clipRect(right = size.width * split) {
                            val (optimizedOffset, optimizedSize) = fittedSize(optimizedImage.width, optimizedImage.height)
                            withTransform({
                                translate(detailPan.x, detailPan.y)
                                scale(detailZoom, detailZoom, pivot = center)
                            }) {
                                drawImage(optimizedImage, dstOffset = optimizedOffset, dstSize = optimizedSize)
                            }
                        }
                    }
                    Box(
                        Modifier.graphicsLayer { translationX = previewWidth.toPx() * split }
                            .fillMaxHeight().width(2.dp).background(Color.White)
                    )
                    Text("ORIGINAL", Modifier.align(Alignment.TopStart).padding(14.dp).background(Color.Black.copy(.55f), RoundedCornerShape(12.dp)).padding(8.dp), color = Color.White, fontSize = 10.sp)
                    Text("OPTIMIZED", Modifier.align(Alignment.TopEnd).padding(14.dp).background(Color.Black.copy(.55f), RoundedCornerShape(12.dp)).padding(8.dp), color = Color.White, fontSize = 10.sp)
                    Text(
                        "${detailZoom.toInt()}× DETAIL • pinch to zoom • drag to inspect",
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                            .background(Color.Black.copy(.68f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.White,
                        fontSize = 9.sp,
                    )
                }
                else if (originalBitmap != null) {
                    Image(originalBitmap!!.asImageBitmap(), "Original", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
            if (optimizedBitmap != null && originalBitmap != null) {
                Slider(value = split, onValueChange = { split = it })
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .07f)),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Output quality", fontWeight = FontWeight.Bold)
                        Text(
                            when (selectedQuality) {
                                QualityPreset.MORE_SAVINGS -> "More savings"
                                QualityPreset.RECOMMENDED -> "Recommended"
                                QualityPreset.MAXIMUM -> "Higher quality"
                            },
                            color = if (selectedQuality == QualityPreset.MORE_SAVINGS) Amber else Mint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    QualitySlider(
                        selected = selectedQuality,
                        onSelected = { qualityName = it.name },
                    )
                    Text(
                        "Changing this regenerates and verifies the preview above.",
                        color = Color(0xFF8F9A93),
                        fontSize = 10.sp,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Original", fontWeight = FontWeight.Bold); Text("${photo.width} × ${photo.height}\n${formatBytes(photo.fileSize)}", color = Color(0xFFAFBBB2), fontSize = 12.sp) }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Optimized", fontWeight = FontWeight.Bold, color = Mint)
                    Text(
                        if (actualCandidate != null) "${actualCandidate.width} × ${actualCandidate.height}\n${formatBytes(actualCandidate.size.toLong())} actual"
                        else "${photo.optimizedWidth} × ${photo.optimizedHeight}\nabout ${formatBytes(photo.optimizedBytes)} estimated",
                        color = Color(0xFFAFBBB2),
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                    )
                }
            }
            Text(
                if (actualCandidate != null) {
                    if (actualCandidate.mode == OptimizationMode.LOSSLESS_SCREENSHOT) {
                        "This copy keeps the original dimensions and passed exact pixel and text checks. Only the file storage is smaller."
                    } else {
                        "The actual smaller copy passed LumaSweep’s checks for fine detail, local areas, and clean edges. Compare it above before choosing it."
                    }
                } else {
                    "The scan used dimensions, file size and format to estimate the opportunity. No photo is replaced unless the actual copy passes every check."
                },
                color = Color(0xFFAFBBB2),
                fontSize = 11.sp,
            )
        }
    }
}
