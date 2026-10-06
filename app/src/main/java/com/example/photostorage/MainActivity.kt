package com.example.photostorage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.photostorage.ui.LumaSweepAppHost
import com.example.photostorage.ui.MainViewModel
import com.example.photostorage.ui.theme.PhotoStorageTheme

/** Android entry point; platform workflows and product UI live outside the Activity. */
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val app = application as PhotoStorageApplication
        MainViewModel.Factory(app.appContainer.photoRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoStorageTheme(darkTheme = true) {
                LumaSweepAppHost(
                    activity = this,
                    container = (application as PhotoStorageApplication).appContainer,
                    viewModel = viewModel,
                )
            }
        }
    }
}
