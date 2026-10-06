package com.example.photostorage.ui

import android.content.ContentResolver
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.photostorage.domain.DuplicateGroup
import com.example.photostorage.domain.OptimizationMode
import com.example.photostorage.domain.OptimizationSettings
import com.example.photostorage.domain.PhotoRecord
import com.example.photostorage.domain.ScanSummary
import com.example.photostorage.data.LocalImageAnalyzer
import com.example.photostorage.ui.theme.Amber
import com.example.photostorage.ui.theme.Blue
import com.example.photostorage.ui.theme.ErrorRed
import com.example.photostorage.ui.theme.Forest
import com.example.photostorage.ui.theme.Mint
import com.example.photostorage.ui.theme.Violet
import com.example.photostorage.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

@Composable
fun PhotoStorageApp(
    summary: ScanSummary,
    lastOptimizationResult: String?,
    hasPermission: Boolean,
    hasLimitedPhotoAccess: Boolean,
    snackbarHostState: SnackbarHostState,
    onGrantPermission: () -> Unit,
    onScanAgain: () -> Unit,
    onMoveToTrash: (List<Uri>) -> Unit,
    onOptimize: (PhotoRecord, LocalImageAnalyzer.EncodedPhoto?, OptimizationSettings) -> Unit,
    onOptimizeMany: (List<PhotoRecord>, OptimizationSettings) -> Unit,
    onCreateSearchablePdf: (List<PhotoRecord>) -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var categoryParent by rememberSaveable { mutableStateOf(Destination.Review) }
    var showLaunch by rememberSaveable { mutableStateOf(true) }
    val homeListState = rememberLazyListState()
    val reviewListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val duplicatesListState = rememberLazyListState()
    val optimizeListState = rememberLazyListState()

    BackHandler(enabled = destination != Destination.Home) {
        destination = when (destination) {
            Destination.Duplicates,
            Destination.Optimize,
            Destination.Similar,
            Destination.Screenshots,
            Destination.Blurry,
            -> categoryParent
            Destination.Review,
            Destination.Settings,
            -> Destination.Home
            Destination.Home -> Destination.Home
        }
    }

    LaunchedEffect(Unit) {
        delay(1_550)
        showLaunch = false
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
        containerColor = Forest,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (destination in listOf(Destination.Home, Destination.Review, Destination.Settings)) {
                AppNavigation(destination = destination, onDestination = { destination = it })
            }
        },
        ) { padding ->
            AnimatedContent(destination, label = "destination") { screen ->
            when (screen) {
                Destination.Home -> HomeScreen(
                    summary = summary,
                    lastOptimizationResult = lastOptimizationResult,
                    hasPermission = hasPermission,
                    hasLimitedPhotoAccess = hasLimitedPhotoAccess,
                    padding = padding,
                    onGrantPermission = onGrantPermission,
                    onScanAgain = onScanAgain,
                    onReview = { destination = Destination.Review },
                    onDuplicates = { categoryParent = Destination.Home; destination = Destination.Duplicates },
                    onOptimize = { categoryParent = Destination.Home; destination = Destination.Optimize },
                    onSimilar = { categoryParent = Destination.Home; destination = Destination.Similar },
                    onScreenshots = { categoryParent = Destination.Home; destination = Destination.Screenshots },
                    onBlurry = { categoryParent = Destination.Home; destination = Destination.Blurry },
                    listState = homeListState,
                )
                Destination.Review -> ReviewHub(
                    summary = summary,
                    padding = padding,
                    onDuplicates = { categoryParent = Destination.Review; destination = Destination.Duplicates },
                    onOptimize = { categoryParent = Destination.Review; destination = Destination.Optimize },
                    onSimilar = { categoryParent = Destination.Review; destination = Destination.Similar },
                    onScreenshots = { categoryParent = Destination.Review; destination = Destination.Screenshots },
                    onBlurry = { categoryParent = Destination.Review; destination = Destination.Blurry },
                    listState = reviewListState,
                )
                Destination.Duplicates -> DuplicateReviewScreen(
                    summary = summary,
                    onBack = { destination = categoryParent },
                    onMoveToTrash = onMoveToTrash,
                    listState = duplicatesListState,
                )
                Destination.Optimize -> OptimizationReviewScreen(
                    summary = summary,
                    onBack = { destination = categoryParent },
                    onOptimize = onOptimize,
                    onOptimizeMany = onOptimizeMany,
                    listState = optimizeListState,
                )
                Destination.Similar -> GalleryReviewScreen(
                    title = "Similar moments",
                    subtitle = "Choose only the extras",
                    photos = summary.cleanupQueues.similarGroups.flatten().distinctBy { it.id },
                    groups = summary.cleanupQueues.similarGroups,
                    onBack = { destination = categoryParent },
                    onMoveToTrash = onMoveToTrash,
                )
                Destination.Screenshots -> GalleryReviewScreen(
                    title = "Screenshots",
                    subtitle = "Filter, compare, then select",
                    photos = summary.cleanupQueues.screenshots,
                    onBack = { destination = categoryParent },
                    onMoveToTrash = onMoveToTrash,
                    showScreenshotCategories = true,
                    onCreateSearchablePdf = onCreateSearchablePdf,
                )
                Destination.Blurry -> GalleryReviewScreen(
                    title = "Possibly blurry",
                    subtitle = "Inspect before selecting",
                    photos = summary.cleanupQueues.blurry,
                    onBack = { destination = categoryParent },
                    onMoveToTrash = onMoveToTrash,
                )
                Destination.Settings -> SettingsScreen(
                    summary = summary,
                    padding = padding,
                    listState = settingsListState,
                    onScanAgain = onScanAgain,
                )
            }
            }
        }
        AnimatedVisibility(
            visible = showLaunch,
            exit = fadeOut(tween(420)) + scaleOut(targetScale = .94f, animationSpec = tween(420)),
        ) {
            LumaSweepLaunch()
        }
    }
}

@Composable
private fun LumaSweepLaunch() {
    var animateIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateIn = true }
    val scale by animateFloatAsState(
        targetValue = if (animateIn) 1f else .72f,
        animationSpec = tween(720),
        label = "launch-logo-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(560),
        label = "launch-logo-alpha",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF29292D), Forest, Color.Black),
                    radius = 1_100f,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(190.dp)
                        .background(Mint.copy(alpha = .12f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = .16f), CircleShape)
                )
                Image(
                    painter = painterResource(R.drawable.ic_lumasweep_logo),
                    contentDescription = "LumaSweep",
                    modifier = Modifier.size(154.dp),
                )
            }
            Text("LumaSweep", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("More room for what matters", color = Mint, fontSize = 14.sp, letterSpacing = .5.sp)
        }
    }
}

@Composable
private fun AppNavigation(destination: Destination, onDestination: (Destination) -> Unit) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = Color(0xFF111113),
        tonalElevation = 0.dp,
    ) {
        listOf(
            Triple(Destination.Home, Icons.Default.Home, "Clean"),
            Triple(Destination.Review, Icons.Default.GridView, "Review"),
            Triple(Destination.Settings, Icons.Default.Settings, "Settings"),
        ).forEach { (item, icon, label) ->
            NavigationBarItem(
                selected = destination == item,
                onClick = { onDestination(item) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Forest,
                    selectedTextColor = Mint,
                    indicatorColor = Mint,
                    unselectedIconColor = Color(0xFF96A198),
                    unselectedTextColor = Color(0xFF96A198),
                ),
            )
        }
    }
}

@Composable
private fun HomeScreen(
    summary: ScanSummary,
    lastOptimizationResult: String?,
    hasPermission: Boolean,
    hasLimitedPhotoAccess: Boolean,
    padding: PaddingValues,
    onGrantPermission: () -> Unit,
    onScanAgain: () -> Unit,
    onReview: () -> Unit,
    onDuplicates: () -> Unit,
    onOptimize: () -> Unit,
    onSimilar: () -> Unit,
    onScreenshots: () -> Unit,
    onBlurry: () -> Unit,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().liquidBackdrop(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = padding.calculateTopPadding() + 24.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_lumasweep_logo),
                    contentDescription = "LumaSweep",
                    modifier = Modifier.size(46.dp),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onScanAgain, enabled = hasPermission && !summary.isScanning) {
                    Icon(Icons.Default.Refresh, contentDescription = "Scan again", tint = Color.White)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Photo cleanup", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                if (hasPermission) {
                    "${formatCount(summary.totalPhotos)} ${if (hasLimitedPhotoAccess) "accessible " else ""}photos  •  ${formatBytes(summary.totalPhotoBytes)}"
                } else "Private, local storage analysis",
                color = Color(0xFFC9D1CB),
            )
        }

        if (!hasPermission) {
            item { PermissionCard(onGrantPermission) }
        } else {
            if (hasLimitedPhotoAccess) {
                item {
                    RefreshStatusCard(
                        title = "Reviewing selected photos only",
                        detail = "Android currently gives LumaSweep access to part of your library. Tap to add or change selected photos.",
                        accent = Blue,
                        onClick = onGrantPermission,
                    )
                }
            }
            if (summary.isScanning) {
                item { ScanDashboard(summary) }
                if (summary.isShowingCachedResults) {
                    item {
                        RefreshStatusCard(
                            title = "Your last results stay available",
                            detail = "Checking the library for changes in the background. Finished batches are saved, so an interrupted scan can continue.",
                            accent = Blue,
                        )
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().glassBorder(),
                        colors = CardDefaults.cardColors(containerColor = Amber.copy(alpha = .10f)),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("EARLY ESTIMATE READY", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("${formatBytes(summary.verifiedTotalSavingBytes)} verified so far", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("You can review results now while duplicate and similarity checks continue.", color = Color(0xFFC6D0C9), fontSize = 12.sp)
                            Button(
                                onClick = onReview,
                                enabled = summary.verifiedTotalSavingBytes > 0,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF7F4EE), contentColor = Forest),
                            ) { Text("Review available results", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                if (summary.optimizationCandidates.isNotEmpty() || summary.optimizationEstimates.isNotEmpty()) {
                    item {
                        RecommendationCard(
                            kind = FindingKind.OPTIMIZE,
                            title = "Verified space savings",
                            subtitle = "${summary.optimizationCandidates.size} ready • ${summary.optimizationEstimates.size} being checked",
                            value = formatBytes(summary.optimizationSavingBytes),
                            enabled = true,
                            onClick = onOptimize,
                        )
                    }
                }
                item { PrivacyStrip() }
            } else {
                if (summary.totalPhotos > 0) {
                    item {
                        val changes = summary.refreshChanges
                        RefreshStatusCard(
                            title = if (changes.hasChanges) "Library updated" else "Everything is up to date",
                            detail = if (changes.hasChanges) {
                                "${changes.newPhotos} new • ${changes.changedPhotos} changed • ${changes.deletedPhotos} removed"
                            } else {
                                "No photo-library changes since the previous scan."
                            },
                            accent = if (changes.hasChanges) Amber else Mint,
                        )
                    }
                }
                item { SavingsHero(summary = summary, onReview = onReview) }
                lastOptimizationResult?.let { result ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().glassBorder(),
                            colors = CardDefaults.cardColors(containerColor = Mint.copy(alpha = .10f)),
                            shape = RoundedCornerShape(22.dp),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("LAST ACTUAL RESULT", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(result, color = Color(0xFFD5DFD8), fontSize = 12.sp)
                            }
                        }
                    }
                }
                summary.errorMessage?.let { message -> item { ErrorCard(message, onScanAgain) } }
                item {
                    Text("SAFE SPACE RECOVERY", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.2.sp)
                }
                item {
                    RecommendationCard(
                        kind = FindingKind.DUPLICATES,
                        title = "Exact duplicates",
                        subtitle = "${summary.duplicateGroups.sumOf { it.photos.size - 1 }} verified redundant copies",
                        value = formatBytes(summary.exactDuplicateBytes),
                        enabled = summary.duplicateGroups.isNotEmpty(),
                        onClick = onDuplicates,
                    )
                }
                item {
                    RecommendationCard(
                        kind = FindingKind.LOSSLESS,
                        title = "Lossless screenshot savings",
                        subtitle = "${summary.losslessOptimizationCandidates.size} pixel-perfect results",
                        value = formatBytes(summary.losslessOptimizationSavingBytes),
                        enabled = summary.losslessOptimizationCandidates.isNotEmpty(),
                        onClick = onOptimize,
                    )
                }
                item {
                    Text("SMALLER COPIES", color = Amber, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.2.sp)
                }
                item {
                    RecommendationCard(
                        kind = FindingKind.OPTIMIZE,
                        title = "Oversized camera photos",
                        subtitle = "${summary.veryLargeCopyCandidates.size} very large • ${summary.highResolutionCopyCandidates.size} high-resolution • ${summary.standardOversizedCopyCandidates.size} other",
                        value = formatBytes(summary.smallerCopySavingBytes),
                        enabled = summary.smallerCopyCandidates.isNotEmpty(),
                        onClick = onOptimize,
                    )
                }
                item {
                    Text("REVIEW YOURSELF", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.2.sp)
                    Text("These are never selected or counted as guaranteed savings.", color = Color(0xFF98A59C), fontSize = 11.sp)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniCategory(FindingKind.SIMILAR, "Similar", "${summary.cleanupQueues.similarGroups.size} groups", Modifier.weight(1f), onSimilar)
                        MiniCategory(FindingKind.SCREENSHOTS, "Screenshots", "${summary.cleanupQueues.screenshots.size} photos", Modifier.weight(1f), onScreenshots)
                        MiniCategory(FindingKind.BLURRY, "Blurry", "${summary.cleanupQueues.blurry.size} photos", Modifier.weight(1f), onBlurry)
                    }
                }
                item { PrivacyStrip() }
            }
        }
    }
}

@Composable
private fun RefreshStatusCard(
    title: String,
    detail: String,
    accent: Color,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().glassBorder().then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = .085f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = accent.copy(alpha = .20f)) {
                Icon(Icons.Default.Refresh, null, tint = accent, modifier = Modifier.padding(8.dp).size(17.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(detail, color = Color(0xFFB3BFB6), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun PermissionCard(onGrantPermission: () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF7F0)),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = CircleShape, color = Mint) {
                Icon(Icons.Default.Lock, null, Modifier.padding(12.dp), tint = Forest)
            }
            Text("See what’s taking up space", color = Forest, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(
                "Allow photo access to calculate real file sizes and find byte-identical copies. Nothing is uploaded.",
                color = Color(0xFF405047),
            )
            Button(
                onClick = onGrantPermission,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Color.White),
            ) {
                Text("Allow photo access", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SavingsHero(summary: ScanSummary, onReview: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onReview),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171B19)),
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mint.copy(alpha = .24f)),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Violet.copy(alpha = .16f)) {
                    Icon(Icons.Default.GridView, null, tint = Violet, modifier = Modifier.padding(9.dp).size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("REVIEW OPPORTUNITY", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            }
            Text(
                "Up to ${formatBytes(summary.reviewCandidateBytes)}",
                color = Mint,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${formatCount(summary.cleanupQueues.allPhotos.size)} photos are worth a quick look. You decide what goes.",
                color = Color(0xFFB9C2BC),
                fontSize = 13.sp,
            )
            HorizontalDivider(color = Color.White.copy(alpha = .08f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Verified safe savings", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatBytes(summary.verifiedTotalSavingBytes)} • ${summary.duplicateGroups.size} duplicate groups • ${summary.optimizationCandidates.size} smaller copies",
                        color = Mint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Surface(shape = CircleShape, color = Mint) {
                    Icon(Icons.Default.ChevronRight, "Open review", tint = Forest, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ScanStatus(summary: ScanSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .08f)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                progress = { summary.progress },
                modifier = Modifier.size(38.dp),
                color = Mint,
                trackColor = Color.White.copy(alpha = .15f),
                strokeWidth = 4.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(summary.scanPhase, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatCount(summary.checkedPhotos)} of ${formatCount(summary.totalPhotos)} checked",
                    color = Color(0xFFB8C1BA),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun ScanDashboard(summary: ScanSummary) {
    val percent = (summary.overallProgress * 100).toInt().coerceIn(0, 99)
    val now by produceState(System.currentTimeMillis(), summary.scanStartedAtMillis) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val eta = estimatedTimeRemaining(summary, now)
    val activeStage = when {
        summary.scanPhase.contains("duplicate", ignoreCase = true) -> 1
        summary.scanPhase.contains("quality", ignoreCase = true) -> 2
        else -> 0
    }
    Card(
        modifier = Modifier.fillMaxWidth().glassBorder(),
        colors = CardDefaults.cardColors(containerColor = Color(0xE218181B)),
        shape = RoundedCornerShape(30.dp),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).clip(CircleShape).background(Mint)
                )
                Spacer(Modifier.width(9.dp))
                Text("SCANNING ON THIS PHONE", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$percent%", fontSize = 56.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatBytes(summary.verifiedTotalSavingBytes), color = Mint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("verified so far", color = Color(0xFFAAB6AD), fontSize = 11.sp)
                }
            }
            LinearProgressIndicator(
                progress = { summary.overallProgress },
                modifier = Modifier.fillMaxWidth().height(9.dp).clip(CircleShape),
                color = Mint,
                trackColor = Color.White.copy(alpha = .10f),
            )
            Column {
                Text(summary.scanPhase, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${formatCount(summary.checkedPhotos)} of ${formatCount(summary.totalPhotos)} photos",
                        color = Color(0xFFAAB6AD),
                        fontSize = 12.sp,
                    )
                    Text(eta, color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = .10f))
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                ScanStageRow("Read photo sizes and metadata", 0, activeStage)
                ScanStageRow("Verify byte-identical copies", 1, activeStage)
                ScanStageRow("Check photo quality and content", 2, activeStage)
            }
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .055f)) {
                Text(
                    "You can leave this screen. Results appear only after they are measured; nothing is deleted during scanning.",
                    color = Color(0xFFBBC6BE),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ScanStageRow(label: String, stage: Int, activeStage: Int) {
    val complete = stage < activeStage
    val active = stage == activeStage
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = when {
                complete -> Mint
                active -> Blue
                else -> Color.White.copy(alpha = .09f)
            },
        ) {
            Icon(
                if (complete) Icons.Default.Check else Icons.Default.HourglassTop,
                contentDescription = null,
                tint = if (complete || active) Forest else Color(0xFF829087),
                modifier = Modifier.padding(6.dp).size(14.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (active || complete) Color.White else Color(0xFF7F8C84),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            when {
                complete -> "Done"
                active -> "Now"
                else -> "Waiting"
            },
            color = if (active) Blue else if (complete) Mint else Color(0xFF7F8C84),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RecommendationCard(
    kind: FindingKind,
    title: String,
    subtitle: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().glassBorder().clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .075f)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = kind.color) {
                Icon(kind.icon, null, tint = Forest, modifier = Modifier.padding(11.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(subtitle, color = Color(0xFFB5BEB7), fontSize = 12.sp)
                Text(value, color = kind.color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFB5BEB7))
        }
    }
}

@Composable
private fun MiniCategory(
    kind: FindingKind,
    label: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .06f)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = kind.color.copy(alpha = .18f)) {
                Icon(
                    kind.icon,
                    contentDescription = null,
                    tint = kind.color,
                    modifier = Modifier.padding(7.dp).size(18.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, fontSize = 10.sp, color = Color(0xFF9EAAA2))
        }
    }
}

@Composable
private fun PrivacyStrip() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Lock, null, tint = Mint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text("Your photos never leave your phone", color = Color(0xFFB9C3BC), fontSize = 12.sp)
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = .18f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), color = Color(0xFFFFDAD5))
            IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, "Retry") }
        }
    }
}

@Composable
private fun ReviewHub(
    summary: ScanSummary,
    padding: PaddingValues,
    onDuplicates: () -> Unit,
    onOptimize: () -> Unit,
    onSimilar: () -> Unit,
    onScreenshots: () -> Unit,
    onBlurry: () -> Unit,
    listState: LazyListState,
) {
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
        item {
            Text("Review", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("Machine-found opportunities. You make every decision.", color = Color(0xFFB8C4BC))
        }
        item {
            GlassMetric(
                title = "Verified opportunity",
                value = formatBytes(summary.verifiedTotalSavingBytes),
                detail = "Duplicates are exact and smaller-copy savings were measured during the scan",
            )
        }
        item { Text("FREE SPACE", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp) }
        item {
            RecommendationCard(
                FindingKind.DUPLICATES,
                "Exact duplicates",
                "${summary.duplicateGroups.size} groups of identical photos",
                formatBytes(summary.exactDuplicateBytes),
                summary.duplicateGroups.isNotEmpty(),
                onDuplicates,
            )
        }
        item {
            RecommendationCard(
                FindingKind.LOSSLESS,
                "Lossless screenshot savings",
                "${summary.losslessOptimizationCandidates.size} pixel-perfect results",
                formatBytes(summary.losslessOptimizationSavingBytes),
                summary.losslessOptimizationCandidates.isNotEmpty(),
                onOptimize,
            )
        }
        item {
            RecommendationCard(
                FindingKind.OPTIMIZE,
                "Oversized camera photos",
                "${summary.veryLargeCopyCandidates.size} very large • ${summary.highResolutionCopyCandidates.size} high-resolution • ${summary.standardOversizedCopyCandidates.size} other",
                formatBytes(summary.smallerCopySavingBytes),
                summary.smallerCopyCandidates.isNotEmpty(),
                onOptimize,
            )
        }
        item { Text("REVIEW ONLY", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp) }
        item {
            RecommendationCard(FindingKind.SIMILAR, "Similar moments", "Photos taken together that look alike", "${summary.cleanupQueues.similarGroups.size} groups", summary.cleanupQueues.similarGroups.isNotEmpty(), onSimilar)
        }
        item {
            RecommendationCard(FindingKind.SCREENSHOTS, "Screenshots", "Grouped by what they appear to contain", "${summary.cleanupQueues.screenshots.size}", summary.cleanupQueues.screenshots.isNotEmpty(), onScreenshots)
        }
        item {
            RecommendationCard(FindingKind.BLURRY, "Possibly blurry", "These photos may be out of focus", "${summary.cleanupQueues.blurry.size}", summary.cleanupQueues.blurry.isNotEmpty(), onBlurry)
        }
        item {
            Text(
                "Additional review: ${formatBytes(summary.reviewCandidateBytes)}. This is never counted as guaranteed savings.",
                color = Color(0xFF9FAAA2),
                fontSize = 12.sp,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
