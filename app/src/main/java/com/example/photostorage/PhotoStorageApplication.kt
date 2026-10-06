package com.example.photostorage

import android.app.Application

class PhotoStorageApplication : Application() {
    val appContainer by lazy { AppContainer(this) }
}
