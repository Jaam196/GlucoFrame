package com.example.feature.background

import android.content.Context
import android.graphics.Bitmap
import android.util.DisplayMetrics
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.BitmapUtils
import com.example.core.common.ColorAnalysisEngine
import com.example.core.database.background.BackgroundImageEntity
import com.example.core.datastore.BackgroundPreferencesRepository
import com.example.core.model.BackgroundMode
import com.example.core.model.BackgroundPreferences
import com.example.core.model.CustomFolderOrder
import com.example.core.model.FreshnessState
import com.example.core.model.GlucoseZone
import com.example.data.background.BackgroundRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class BackgroundLayerUiState(
    val currentBitmap: Bitmap? = null,
    val currentUriString: String? = null,
    val averageBrightness: Float = 0.5f,
    val preferences: BackgroundPreferences = BackgroundPreferences(),
    val isNightDimmedBlack: Boolean = false,
    val isFreshActual: Boolean = true,
    val customFolderPermissionLost: Boolean = false,
    val currentName: String? = null
)

class BackgroundViewModel(
    private val context: Context,
    private val backgroundRepository: BackgroundRepository,
    private val backgroundPreferencesRepository: BackgroundPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackgroundLayerUiState())
    val uiState: StateFlow<BackgroundLayerUiState> = _uiState.asStateFlow()

    private var autoChangeJob: Job? = null
    private var sequentialIndex = 0

    // Zone debounce tracker: >= 2 consecutive readings required, URGENT_LOW is immediate
    private var confirmedZone: GlucoseZone = GlucoseZone.IN_RANGE
    private var pendingZone: GlucoseZone? = null
    private var pendingZoneConsecutiveCount: Int = 0

    private var currentFreshness: FreshnessState = FreshnessState.ACTUAL
    private var isBlackScreenActive: Boolean = false

    private val screenWidth: Int
    private val screenHeight: Int

    init {
        val dm = context.resources.displayMetrics
        screenWidth = dm.widthPixels.coerceAtLeast(800)
        screenHeight = dm.heightPixels.coerceAtLeast(600)

        // Observe background preferences
        viewModelScope.launch {
            backgroundPreferencesRepository.backgroundPreferencesFlow.collect { prefs ->
                val prevPrefs = _uiState.value.preferences
                _uiState.value = _uiState.value.copy(preferences = prefs)

                // Check custom folder permission if in custom folder mode
                if (prefs.mode == BackgroundMode.CUSTOM_FOLDER) {
                    val hasPerm = backgroundRepository.checkCustomFolderPermission(prefs.customFolderUri)
                    _uiState.value = _uiState.value.copy(customFolderPermissionLost = !hasPerm && !prefs.customFolderUri.isNullOrBlank())
                } else {
                    _uiState.value = _uiState.value.copy(customFolderPermissionLost = false)
                }

                val modeOrCategoryChanged = prevPrefs.mode != prefs.mode ||
                    prevPrefs.presetCategory != prefs.presetCategory ||
                    prevPrefs.fixedImageUri != prefs.fixedImageUri ||
                    prevPrefs.customFolderUri != prefs.customFolderUri

                if (modeOrCategoryChanged) {
                    refreshBackground(forceNew = true)
                }

                restartTimerIfNeeded()
            }
        }

        // When the set of usable images changes (a folder finishes scanning, the mode changes, images are
        // enabled/disabled) and nothing valid is on screen, load a picture. Without this the wallpaper stayed
        // empty after choosing a folder, because the first refresh ran before the folder was indexed.
        viewModelScope.launch {
            combine(
                backgroundPreferencesRepository.backgroundPreferencesFlow,
                backgroundRepository.getAllImagesFlow()
            ) { prefs, images ->
                backgroundRepository.getEligibleImages(prefs, images).map { it.uriString }.toSet()
            }.distinctUntilChanged().collect { eligibleUris ->
                val state = _uiState.value
                val shownUri = state.currentUriString
                val needsPicture = state.currentBitmap == null || shownUri == null || shownUri !in eligibleUris
                if (needsPicture && state.preferences.fixedImageUri == null && !isBlackScreenActive) {
                    refreshBackground(forceNew = true)
                }
            }
        }

        // Trigger initial image indexing
        viewModelScope.launch {
            val prefs = backgroundPreferencesRepository.backgroundPreferencesFlow.first()
            backgroundRepository.scanAndSyncImages(prefs.customFolderUri)
        }
    }

    /**
     * Called by FrameViewModel / MainActivity when glucose reading or zone updates.
     * Implements visual zone debounce filter (>= 2 readings, URGENT_LOW immediate).
     */
    fun updateGlucoseState(newZone: GlucoseZone, freshness: FreshnessState, isNightBlack: Boolean) {
        val freshnessChanged = currentFreshness != freshness
        val nightBlackChanged = isBlackScreenActive != isNightBlack
        currentFreshness = freshness
        isBlackScreenActive = isNightBlack

        val isFresh = freshness == FreshnessState.ACTUAL
        _uiState.value = _uiState.value.copy(
            isNightDimmedBlack = isNightBlack,
            isFreshActual = isFresh
        )

        if (isNightBlack) {
            autoChangeJob?.cancel()
            autoChangeJob = null
            // Free current bitmap in memory during black screen
            // (the old bitmap is simply released: recycling it by hand could crash a frame still drawing it)
            _uiState.value = _uiState.value.copy(currentBitmap = null, currentUriString = null)
            return
        }

        if (!isFresh) {
            // Stale or missing reading: keep the chosen wallpaper visible (slightly dimmed by the layer),
            // so the user can still pick and see backgrounds. Zone-based switching only runs with fresh data.
            if (_uiState.value.currentBitmap == null) {
                refreshBackground(forceNew = false)
            }
            return
        }

        var zoneUpdated = false

        if (newZone == GlucoseZone.URGENT_LOW) {
            if (confirmedZone != GlucoseZone.URGENT_LOW) {
                confirmedZone = GlucoseZone.URGENT_LOW
                pendingZone = null
                pendingZoneConsecutiveCount = 0
                zoneUpdated = true
            }
        } else if (newZone == confirmedZone) {
            pendingZone = null
            pendingZoneConsecutiveCount = 0
        } else {
            if (newZone == pendingZone) {
                pendingZoneConsecutiveCount++
                if (pendingZoneConsecutiveCount >= 2) {
                    confirmedZone = newZone
                    pendingZone = null
                    pendingZoneConsecutiveCount = 0
                    zoneUpdated = true
                }
            } else {
                pendingZone = newZone
                pendingZoneConsecutiveCount = 1
            }
        }

        if (zoneUpdated || freshnessChanged || nightBlackChanged || _uiState.value.currentBitmap == null) {
            // A new glucose zone only swaps the picture in "smart color" mode; otherwise the background
            // changes only when the user picks one (or when automatic change is enabled).
            val smartColor = _uiState.value.preferences.mode == BackgroundMode.SMART_COLOR
            refreshBackground(forceNew = zoneUpdated && smartColor)
            restartTimerIfNeeded()
        }
    }

    private fun restartTimerIfNeeded() {
        autoChangeJob?.cancel()
        val prefs = _uiState.value.preferences
        if (isBlackScreenActive || !prefs.autoChangeEnabled || prefs.fixedImageUri != null) {
            return
        }

        autoChangeJob = viewModelScope.launch {
            while (isActive) {
                // Each picture can have its own time on screen; otherwise the general interval is used
                val current = _uiState.value
                val seconds = (current.preferences.photoDurations[current.currentUriString]
                    ?: current.preferences.changeIntervalSeconds).coerceAtLeast(15)
                delay(seconds * 1000L)
                refreshBackground(forceNew = true)
                delay(2_000L) // let the new picture load so its own time is used next
            }
        }
    }

    private fun refreshBackground(forceNew: Boolean) {
        if (isBlackScreenActive) return

        viewModelScope.launch(Dispatchers.Default) {
            val prefs = backgroundPreferencesRepository.backgroundPreferencesFlow.first()

            // 1. If fixed image URI is set, use it
            if (prefs.fixedImageUri != null) {
                if (_uiState.value.currentUriString != prefs.fixedImageUri) {
                    val fixedName = backgroundRepository.getAllImagesSync()
                        .firstOrNull { it.uriString == prefs.fixedImageUri }?.displayName
                    loadAndApplyImage(prefs.fixedImageUri, prefs, displayName = fixedName)
                }
                return@launch
            }

            // Without an explicit request (user pick, timer, smart-color zone change) keep what is on screen
            if (!forceNew && _uiState.value.currentBitmap != null) return@launch

            val allImages = backgroundRepository.getAllImagesSync()
            val eligible = backgroundRepository.getEligibleImages(prefs, allImages)

            if (eligible.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(currentBitmap = null, currentUriString = null)
                }
                return@launch
            }

            val selectedEntity: BackgroundImageEntity? = when (prefs.mode) {
                BackgroundMode.PRESET_LIBRARY -> {
                    pickFromList(eligible, _uiState.value.currentUriString)
                }
                BackgroundMode.CUSTOM_FOLDER -> {
                    if (prefs.customFolderOrder == CustomFolderOrder.SEQUENTIAL) {
                        sequentialIndex = if (eligible.isNotEmpty()) (sequentialIndex + 1) % eligible.size else 0
                        eligible.getOrNull(sequentialIndex)
                    } else {
                        pickFromList(eligible, _uiState.value.currentUriString)
                    }
                }
                BackgroundMode.SMART_COLOR -> {
                    val targetHex = prefs.colorHexForZone(confirmedZone)
                    ColorAnalysisEngine.selectBestMatchingImage(eligible, targetHex, _uiState.value.currentUriString)
                }
            }

            if (selectedEntity != null && (forceNew || selectedEntity.uriString != _uiState.value.currentUriString)) {
                val loaded = loadAndApplyImage(selectedEntity.uriString, prefs, selectedEntity.averageBrightness, selectedEntity.displayName)
                if (!loaded) {
                    // The picture could not be decoded (corrupt / unsupported): try a few others instead of showing nothing
                    val others = eligible.filter { it.uriString != selectedEntity.uriString }.shuffled().take(4)
                    for (other in others) {
                        if (loadAndApplyImage(other.uriString, prefs, other.averageBrightness, other.displayName)) break
                    }
                }
            }
        }
    }

    private fun pickFromList(list: List<BackgroundImageEntity>, lastUri: String?): BackgroundImageEntity {
        if (list.size <= 1) return list.first()
        val favorites = list.filter { it.isFavorite }
        val pool = if (favorites.isNotEmpty()) favorites else list
        val candidates = pool.filter { it.uriString != lastUri }.ifEmpty { pool }
        return candidates[Random.nextInt(candidates.size)]
    }

    private suspend fun loadAndApplyImage(
        uriString: String,
        prefs: BackgroundPreferences,
        knownBrightness: Float = 0.5f,
        displayName: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val decoded = BitmapUtils.decodeSampledBitmap(context, uriString, screenWidth, screenHeight)
        if (decoded == null) {
            Log.w("BackgroundViewModel", "Could not decode background image: $uriString")
            return@withContext false
        }

        val processed = if (prefs.blurRadius > 0.5f) {
            val blurred = BitmapUtils.applyBlur(decoded, prefs.blurRadius)
            if (blurred !== decoded) decoded.recycle()
            blurred
        } else {
            decoded
        }

        withContext(Dispatchers.Main) {
            // The previous bitmap is not recycled by hand: the crossfade may still be drawing it
            _uiState.value = _uiState.value.copy(
                currentBitmap = processed,
                currentUriString = uriString,
                currentName = displayName,
                averageBrightness = knownBrightness
            )
        }
        true
    }

    override fun onCleared() {
        super.onCleared()
        autoChangeJob?.cancel()
    }
}
