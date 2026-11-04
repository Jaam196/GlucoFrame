package com.example.feature.displaystyle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.datastore.BackgroundPreferencesRepository
import com.example.core.datastore.DisplayStyleRepository
import com.example.core.datastore.PreferencesRepository
import com.example.core.model.BackgroundPreferences
import com.example.core.model.DisplayStyle
import com.example.core.model.GlucoseUnit
import com.example.core.model.GlucoseZone
import com.example.core.model.ThresholdSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DisplayStyleUiState(
    val style: DisplayStyle = DisplayStyle(),
    /** Holds the colour chosen for every glucose level. */
    val zoneColors: BackgroundPreferences = BackgroundPreferences(),
    val thresholds: ThresholdSettings = ThresholdSettings(),
    val unit: GlucoseUnit = GlucoseUnit.MG_DL
)

class DisplayStyleViewModel(
    private val styleRepository: DisplayStyleRepository,
    private val backgroundPreferencesRepository: BackgroundPreferencesRepository,
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<DisplayStyleUiState> = combine(
        styleRepository.styleFlow,
        backgroundPreferencesRepository.backgroundPreferencesFlow,
        preferencesRepository.userPreferencesFlow
    ) { style, zoneColors, userPrefs ->
        DisplayStyleUiState(
            style = style,
            zoneColors = zoneColors,
            thresholds = userPrefs.thresholds,
            unit = userPrefs.unit
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DisplayStyleUiState())

    fun update(transform: (DisplayStyle) -> DisplayStyle) {
        viewModelScope.launch { styleRepository.update(transform) }
    }

    fun resetAll() {
        viewModelScope.launch { styleRepository.resetAll() }
    }

    fun setZoneColor(zone: GlucoseZone, hex: String) {
        viewModelScope.launch { backgroundPreferencesRepository.updateZoneColor(zone, hex) }
    }

    fun restoreZoneColors() {
        viewModelScope.launch { backgroundPreferencesRepository.restoreDefaultZoneColors() }
    }
}
