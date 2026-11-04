package com.example.feature.background.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.background.BackgroundImageEntity
import com.example.core.datastore.BackgroundPreferencesRepository
import com.example.core.model.BackgroundMode
import com.example.core.model.BackgroundPreferences
import com.example.core.model.CustomFolderOrder
import com.example.core.model.GlucoseReading
import com.example.core.model.GlucoseZone
import com.example.data.background.AnalysisProgress
import com.example.data.background.BackgroundRepository
import com.example.data.background.FolderScanResult
import com.example.data.repository.GlucoseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class BackgroundSettingsUiState(
    val preferences: BackgroundPreferences = BackgroundPreferences(),
    val allImages: List<BackgroundImageEntity> = emptyList(),
    val analysisProgress: AnalysisProgress = AnalysisProgress(),
    val customFolderPermissionLost: Boolean = false,
    val folderScan: FolderScanResult = FolderScanResult(),
    val previewZone: GlucoseZone = GlucoseZone.IN_RANGE,
    val latestReading: GlucoseReading? = null,
    val colorCounts: Map<String, Int> = emptyMap(),
    val activeColorEditZone: GlucoseZone? = null
)

class BackgroundSettingsViewModel(
    private val context: Context,
    private val backgroundRepository: BackgroundRepository,
    private val backgroundPreferencesRepository: BackgroundPreferencesRepository,
    private val glucoseRepository: GlucoseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackgroundSettingsUiState())
    val uiState: StateFlow<BackgroundSettingsUiState> = _uiState.asStateFlow()

    init {
        // Observe preferences
        viewModelScope.launch {
            backgroundPreferencesRepository.backgroundPreferencesFlow.collect { prefs ->
                val hasPerm = backgroundRepository.checkCustomFolderPermission(prefs.customFolderUri)
                val lost = !hasPerm && !prefs.customFolderUri.isNullOrBlank()
                _uiState.value = _uiState.value.copy(
                    preferences = prefs,
                    customFolderPermissionLost = lost
                )
            }
        }

        // Observe images from Room
        viewModelScope.launch {
            backgroundRepository.getAllImagesFlow().collect { images ->
                val counts = images.groupBy { it.primaryClassification }.mapValues { it.value.size }
                _uiState.value = _uiState.value.copy(
                    allImages = images,
                    colorCounts = counts
                )
            }
        }

        // Observe the result of the last custom-folder scan
        viewModelScope.launch {
            backgroundRepository.folderScan.collect { scan ->
                _uiState.value = _uiState.value.copy(folderScan = scan)
            }
        }

        // Observe color analysis progress
        viewModelScope.launch {
            backgroundRepository.analysisProgress.collect { progress ->
                _uiState.value = _uiState.value.copy(analysisProgress = progress)
            }
        }

        // Observe latest reading for real-time preview
        viewModelScope.launch {
            glucoseRepository.getLatestReading().collect { reading ->
                _uiState.value = _uiState.value.copy(latestReading = reading)
            }
        }
    }

    fun setMode(mode: BackgroundMode) = viewModelScope.launch {
        backgroundPreferencesRepository.updateMode(mode)
    }

    fun setPresetCategory(category: String) = viewModelScope.launch {
        backgroundPreferencesRepository.updatePresetCategory(category)
    }

    fun onCustomFolderSelected(treeUri: Uri) = viewModelScope.launch {
        val uriStr = treeUri.toString()
        // A fixed picture overrides every folder/library choice, and an old partial selection could hide the
        // new images: clear both so the freshly chosen folder is actually used.
        backgroundPreferencesRepository.updateFixedImageUri(null)
        backgroundPreferencesRepository.updateCustomFolderSelection(selectAll = true, selectedIds = emptySet())
        backgroundPreferencesRepository.updateCustomFolderUri(uriStr)
        backgroundPreferencesRepository.updateMode(BackgroundMode.CUSTOM_FOLDER)
        backgroundRepository.scanAndSyncImages(uriStr)
    }

    fun rescanCustomFolder() = viewModelScope.launch {
        val uriStr = _uiState.value.preferences.customFolderUri
        backgroundRepository.scanAndSyncImages(uriStr)
    }

    fun setCustomFolderOrder(order: CustomFolderOrder) = viewModelScope.launch {
        backgroundPreferencesRepository.updateCustomFolderOrder(order)
    }

    fun toggleCustomFolderSelectAll(selectAll: Boolean) = viewModelScope.launch {
        backgroundPreferencesRepository.updateCustomFolderSelection(
            selectAll = selectAll,
            selectedIds = _uiState.value.preferences.customFolderSelectedIds
        )
    }

    fun toggleCustomImageSelection(uriString: String) = viewModelScope.launch {
        val cur = _uiState.value.preferences.customFolderSelectedIds.toMutableSet()
        if (cur.contains(uriString)) {
            cur.remove(uriString)
        } else {
            cur.add(uriString)
        }
        backgroundPreferencesRepository.updateCustomFolderSelection(selectAll = false, selectedIds = cur)
    }

    fun setFixedImage(uriString: String?) = viewModelScope.launch {
        backgroundPreferencesRepository.updateFixedImageUri(uriString)
    }

    fun toggleFavorite(id: Long, isFavorite: Boolean) = viewModelScope.launch {
        backgroundRepository.toggleFavorite(id, isFavorite)
    }

    fun toggleDisabled(id: Long, isDisabled: Boolean) = viewModelScope.launch {
        backgroundRepository.toggleDisabled(id, isDisabled)
    }

    fun setChangeInterval(seconds: Int) = viewModelScope.launch {
        backgroundPreferencesRepository.updateChangeInterval(seconds)
    }

    fun setAutoChangeEnabled(enabled: Boolean) = viewModelScope.launch {
        backgroundPreferencesRepository.updateAutoChangeEnabled(enabled)
    }

    fun setCrossfadeDuration(seconds: Float) = viewModelScope.launch {
        backgroundPreferencesRepository.updateCrossfadeDuration(seconds)
    }

    fun setDarkLayerOpacity(opacity: Float) = viewModelScope.launch {
        backgroundPreferencesRepository.updateDarkLayerOpacity(opacity)
    }

    fun setBlurRadius(radius: Float) = viewModelScope.launch {
        backgroundPreferencesRepository.updateBlurRadius(radius)
    }

    fun setSaturation(saturation: Float) = viewModelScope.launch {
        backgroundPreferencesRepository.updateSaturation(saturation)
    }

    fun setBrightness(brightness: Float) = viewModelScope.launch {
        backgroundPreferencesRepository.updateBrightness(brightness)
    }

    fun setContrast(contrast: Float) = viewModelScope.launch {
        backgroundPreferencesRepository.updateContrast(contrast)
    }

    fun setKenBurns(enabled: Boolean) = viewModelScope.launch {
        backgroundPreferencesRepository.updateKenBurns(enabled)
    }

    fun setShowPhotoName(enabled: Boolean) = viewModelScope.launch {
        backgroundPreferencesRepository.updateShowPhotoName(enabled)
    }

    fun setPhotoDuration(uriString: String, seconds: Int?) = viewModelScope.launch {
        backgroundPreferencesRepository.updatePhotoDuration(uriString, seconds)
    }

    fun setAutoReadingProtection(enabled: Boolean) = viewModelScope.launch {
        backgroundPreferencesRepository.updateAutoReadingProtection(enabled)
    }

    fun openColorDialog(zone: GlucoseZone) {
        _uiState.value = _uiState.value.copy(activeColorEditZone = zone)
    }

    fun dismissColorDialog() {
        _uiState.value = _uiState.value.copy(activeColorEditZone = null)
    }

    fun saveZoneColor(zone: GlucoseZone, hexColor: String) = viewModelScope.launch {
        backgroundPreferencesRepository.updateZoneColor(zone, hexColor)
        dismissColorDialog()
    }

    fun restoreDefaultZoneColors() = viewModelScope.launch {
        backgroundPreferencesRepository.restoreDefaultZoneColors()
    }

    fun setPreviewZone(zone: GlucoseZone) {
        _uiState.value = _uiState.value.copy(previewZone = zone)
    }
}
