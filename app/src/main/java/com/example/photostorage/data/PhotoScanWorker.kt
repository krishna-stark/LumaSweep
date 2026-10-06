package com.example.photostorage.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.photostorage.PhotoStorageApplication
import com.example.photostorage.domain.ScanSummary
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class PhotoScanWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result = coroutineScope {
        if (!hasPhotoPermission(applicationContext)) return@coroutineScope Result.failure()
        setForeground(foregroundInfo())
        val app = applicationContext as PhotoStorageApplication
        val repository = app.appContainer.photoRepository
        val notificationJob = launch {
            repository.summary
                .distinctUntilChangedBy { Triple(it.scanPhase, it.checkedPhotos, it.totalPhotos) }
                .sample(NOTIFICATION_UPDATE_INTERVAL_MS)
                .collect { setForeground(foregroundInfo(it)) }
        }
        val result = runCatching {
            if (repository.scan()) Result.success() else Result.retry()
        }.getOrElse { Result.retry() }
        notificationJob.cancelAndJoin()
        result
    }

    private fun foregroundInfo(summary: ScanSummary? = null): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Library scan",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Keeps storage analysis running when LumaSweep is closed" }
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.example.photostorage.R.drawable.ic_launcher)
            .setContentTitle("LumaSweep is scanning your library")
            .setContentText(
                summary?.let {
                    if (it.totalPhotos > 0) "${it.scanPhase} • ${it.checkedPhotos} of ${it.totalPhotos}"
                    else it.scanPhase
                } ?: "Analysis continues safely in the background"
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(
                summary?.totalPhotos ?: 0,
                summary?.checkedPhotos ?: 0,
                summary?.totalPhotos?.let { it <= 0 } ?: true,
            )
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun hasPhotoPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ) == PackageManager.PERMISSION_GRANTED
        ) return true
        val fullPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(context, fullPermission) == PackageManager.PERMISSION_GRANTED
    }
}

object PhotoScanScheduler {
    private const val UNIQUE_SCAN = "lumasweep-photo-library-scan"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<PhotoScanWorker>()
            .addTag(UNIQUE_SCAN)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_SCAN,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}

private const val CHANNEL_ID = "lumasweep_library_scan"
private const val NOTIFICATION_ID = 4207
private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1_000L
