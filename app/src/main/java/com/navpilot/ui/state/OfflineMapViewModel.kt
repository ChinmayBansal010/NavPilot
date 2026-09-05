package com.navpilot.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navpilot.data.repository.OsmOfflineMapRepository
import com.navpilot.domain.model.OfflineMapRegion
import com.navpilot.domain.model.OfflineMapStatus
import com.navpilot.domain.repository.OfflineMapRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OfflineMapState(
    val availableRegions: List<OfflineMapRegion> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

class OfflineMapViewModel(
    application: Application,
    private val repository: OfflineMapRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(OfflineMapState())
    val state: StateFlow<OfflineMapState> = _state.asStateFlow()

    init {
        refreshRegions()
        observeDownloadedRegions()
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            val results = repository.searchRegions(query)
            _state.update { it.copy(availableRegions = results) }
        }
    }

    fun downloadRegion(region: OfflineMapRegion) {
        viewModelScope.launch {
            repository.downloadRegion(region).collect { updated ->
                _state.update { current ->
                    current.copy(
                        availableRegions = current.availableRegions.map {
                            if (it.id == updated.id) updated else it
                        }
                    )
                }
            }
        }
    }

    fun deleteRegion(regionId: String) {
        viewModelScope.launch {
            repository.deleteRegion(regionId)
        }
    }

    private fun refreshRegions() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val regions = repository.getAvailableRegions()
            _state.update { it.copy(availableRegions = regions, isLoading = false) }
        }
    }

    private fun observeDownloadedRegions() {
        viewModelScope.launch {
            repository.getDownloadedRegions().collect { downloaded ->
                _state.update { current ->
                    current.copy(
                        availableRegions = current.availableRegions.map { available ->
                            downloaded.find { it.id == available.id } ?: available.copy(status = OfflineMapStatus.AVAILABLE)
                        }
                    )
                }
            }
        }
    }
}

class OfflineMapViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OfflineMapViewModel::class.java)) {
            val repository = OsmOfflineMapRepository(application.applicationContext)
            return OfflineMapViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
