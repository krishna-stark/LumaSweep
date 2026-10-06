package com.example.photostorage

import android.app.Application
import com.example.photostorage.data.AppDatabase
import com.example.photostorage.data.LocalImageAnalyzer
import com.example.photostorage.data.MediaStorePhotoScanner
import com.example.photostorage.data.PhotoOptimizer
import com.example.photostorage.data.PhotoRepository
import com.example.photostorage.data.SearchableDocumentExporter

/**
 * Small dependency graph for process-wide services.
 * Constructor wiring stays centralized without coupling feature UI to implementations.
 */
class AppContainer(application: Application) {
    private val database = AppDatabase.create(application)
    private val analyzer = LocalImageAnalyzer(application)

    val photoOptimizer = PhotoOptimizer(application.contentResolver, analyzer)
    val searchableDocumentExporter = SearchableDocumentExporter(application.contentResolver)
    val photoRepository = PhotoRepository(
        MediaStorePhotoScanner(application.contentResolver, database.mediaItemDao(), analyzer)
    )
}
