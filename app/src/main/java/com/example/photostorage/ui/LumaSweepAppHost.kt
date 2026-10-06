package com.example.photostorage.ui

import android.app.Activity
import android.app.PendingIntent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.photostorage.AppContainer
import com.example.photostorage.data.PhotoScanScheduler
import com.example.photostorage.platform.PhotoAccessLevel
import com.example.photostorage.platform.photoAccessLevel
import com.example.photostorage.platform.requiredPhotoPermissions
import kotlinx.coroutines.launch

/**
 * Composition root for UI and Android-mediated actions.
 *
 * The domain UI emits intents; this host translates them into permission requests,
 * safe MediaStore Trash transactions, optimization work, and document export.
 */
@Composable
fun LumaSweepAppHost(
    activity: ComponentActivity,
    container: AppContainer,
    viewModel: MainViewModel,
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scheduleScan = { PhotoScanScheduler.enqueue(activity) }
    var photoAccess by remember { mutableStateOf(activity.photoAccessLevel()) }
    var pendingGeneratedUris by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var pendingActualSavedBytes by rememberSaveable { mutableLongStateOf(0L) }
    var pendingPhotoCount by rememberSaveable { mutableIntStateOf(0) }
    var pendingAction by rememberSaveable { mutableStateOf(PendingMediaAction.NONE) }
    var lastOptimizationResult by rememberSaveable { mutableStateOf<String?>(null) }

    fun clearPendingAction() {
        pendingGeneratedUris = emptyList()
        pendingActualSavedBytes = 0L
        pendingPhotoCount = 0
        pendingAction = PendingMediaAction.NONE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        photoAccess = activity.photoAccessLevel()
        if (photoAccess != PhotoAccessLevel.NONE) scheduleScan()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        photoAccess = activity.photoAccessLevel()
    }

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val generated = pendingGeneratedUris.map(Uri::parse)
        val actualSaved = pendingActualSavedBytes
        val photoCount = pendingPhotoCount
        val action = pendingAction
        clearPendingAction()

        if (result.resultCode == Activity.RESULT_OK) {
            scheduleScan()
            scope.launch {
                if (action == PendingMediaAction.OPTIMIZE) {
                    lastOptimizationResult = optimizationResultMessage(activity, actualSaved, photoCount)
                    snackbar.showSnackbar(
                        "Actual result: optimized copies are ${formatBytes(actualSaved)} smaller across " +
                            photoCountLabel(photoCount) + ". Originals are in system Trash."
                    )
                } else {
                    snackbar.showSnackbar("Moved ${photoCountLabel(photoCount)} to system Trash.")
                }
            }
        } else if (action == PendingMediaAction.OPTIMIZE && generated.isNotEmpty()) {
            scope.launch {
                generated.forEach { container.photoOptimizer.removeOwnedCopy(it) }
                snackbar.showSnackbar("Optimization cancelled. Generated copies were removed.")
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(photoAccess) {
        if (photoAccess != PhotoAccessLevel.NONE && summary.totalPhotos == 0 && !summary.isScanning) {
            scheduleScan()
        }
    }

    fun launchTrashRequest(uris: List<Uri>, action: PendingMediaAction) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || uris.isEmpty()) return
        pendingPhotoCount = uris.size
        pendingAction = action
        runCatching {
            val request: PendingIntent = MediaStore.createTrashRequest(activity.contentResolver, uris, true)
            trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }.onFailure { error ->
            clearPendingAction()
            scope.launch { snackbar.showSnackbar(error.message ?: "Could not open system Trash safely.") }
        }
    }

    PhotoStorageApp(
        summary = summary,
        lastOptimizationResult = lastOptimizationResult,
        hasPermission = photoAccess != PhotoAccessLevel.NONE,
        hasLimitedPhotoAccess = photoAccess == PhotoAccessLevel.LIMITED,
        snackbarHostState = snackbar,
        onGrantPermission = { permissionLauncher.launch(requiredPhotoPermissions()) },
        onScanAgain = scheduleScan,
        onMoveToTrash = { uris ->
            pendingGeneratedUris = emptyList()
            pendingActualSavedBytes = 0L
            launchTrashRequest(uris, PendingMediaAction.DELETE)
        },
        onOptimize = { photo, candidate, settings ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) scope.launch {
                runCatching {
                    container.photoOptimizer.createVerifiedOptimizedCopy(photo, candidate, settings)
                }.onSuccess { result ->
                    pendingGeneratedUris = listOf(result.uri.toString())
                    pendingActualSavedBytes = result.savedBytes
                    pendingPhotoCount = 1
                    pendingAction = PendingMediaAction.OPTIMIZE
                    runCatching {
                        val request = MediaStore.createTrashRequest(
                            activity.contentResolver,
                            listOf(photo.uri),
                            true,
                        )
                        trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    }.onFailure { error ->
                        container.photoOptimizer.removeOwnedCopy(result.uri)
                        clearPendingAction()
                        snackbar.showSnackbar(error.message ?: "Could not open system Trash; generated copy removed.")
                    }
                }.onFailure { error ->
                    snackbar.showSnackbar(error.message ?: "Optimization failed safely.")
                }
            }
        },
        onOptimizeMany = { photos, settings ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && photos.isNotEmpty()) scope.launch {
                val generated = mutableListOf<Uri>()
                runCatching {
                    var actualSaved = 0L
                    photos.forEach { photo ->
                        val result = container.photoOptimizer.createVerifiedOptimizedCopy(photo, settings = settings)
                        generated += result.uri
                        actualSaved += result.savedBytes
                    }
                    pendingGeneratedUris = generated.map(Uri::toString)
                    pendingActualSavedBytes = actualSaved
                    pendingPhotoCount = photos.size
                    pendingAction = PendingMediaAction.OPTIMIZE
                    val request = MediaStore.createTrashRequest(
                        activity.contentResolver,
                        photos.map { it.uri },
                        true,
                    )
                    trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                }.onFailure { error ->
                    generated.forEach { container.photoOptimizer.removeOwnedCopy(it) }
                    clearPendingAction()
                    snackbar.showSnackbar(error.message ?: "Bulk optimization stopped safely.")
                }
            }
        },
        onCreateSearchablePdf = { photos ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) scope.launch {
                runCatching { container.searchableDocumentExporter.export(photos) }
                    .onSuccess { result ->
                        snackbar.showSnackbar(
                            "Created a searchable PDF with ${result.pageCount} " +
                                "page${if (result.pageCount == 1) "" else "s"} in Documents/LumaSweep."
                        )
                    }
                    .onFailure { error ->
                        snackbar.showSnackbar(error.message ?: "Could not create the searchable PDF.")
                    }
            } else {
                scope.launch { snackbar.showSnackbar("Searchable PDF export requires Android 10 or newer.") }
            }
        },
    )
}

private enum class PendingMediaAction { NONE, DELETE, OPTIMIZE }

private fun photoCountLabel(count: Int): String =
    "$count photo${if (count == 1) "" else "s"}"

private fun optimizationResultMessage(
    activity: ComponentActivity,
    savedBytes: Long,
    count: Int,
): String = "Optimized copies are ${android.text.format.Formatter.formatShortFileSize(activity, savedBytes)} " +
    "smaller across ${photoCountLabel(count)}. Originals remain recoverable in system Trash."
