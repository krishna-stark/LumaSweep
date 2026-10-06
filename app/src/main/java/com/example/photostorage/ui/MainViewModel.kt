package com.example.photostorage.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.photostorage.data.PhotoRepository
import com.example.photostorage.domain.ScanSummary
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: PhotoRepository,
) : ViewModel() {
    val summary: StateFlow<ScanSummary> = repository.summary

    init {
        viewModelScope.launch { repository.loadCachedResults() }
    }

    class Factory(private val repository: PhotoRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(repository) as T
    }
}
