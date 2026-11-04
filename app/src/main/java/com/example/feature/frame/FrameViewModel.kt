package com.example.feature.frame

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.CryptoUtils
import com.example.core.common.GlucoseUtils
import com.example.core.datastore.PreferencesRepository
import com.example.core.datastore.UserPreferences
import com.example.core.model.FreshnessState
import com.example.core.model.GlucoseReading
import com.example.core.security.KeystoreManager
import com.example.data.repository.GlucoseRepository
import com.example.sync.SyncEngine
import com.example.sync.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

data class FrameUiState(
    val latestReading: GlucoseReading? = null,
    val previousReading: GlucoseReading? = null,
    val windowReadings: List<GlucoseReading> = emptyList(),
    val freshness: FreshnessState = FreshnessState.SIN_DATOS,
    val currentNowMs: Long = System.currentTimeMillis(),
    val preferences: UserPreferences = UserPreferences(),
    val preferencesLoaded: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus(),
    val selectedWindowHours: Int = 3,
    val pixelShiftX: Float = 0f,
    val pixelShiftY: Float = 0f,
    val isNightDimmed: Boolean = false,
    val pinDialogOpen: Boolean = false,
    val pinError: Boolean = false,
    val pinLockoutSeconds: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class FrameViewModel(
    private val repository: GlucoseRepository,
    private val preferencesRepository: PreferencesRepository,
    private val keystoreManager: KeystoreManager,
    private val syncEngine: SyncEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(FrameUiState())
    val uiState: StateFlow<FrameUiState> = _uiState.asStateFlow()

    private var failedPinAttempts: Int = 0
    private var currentLockoutSec: Int = 30
    private var lockedUntilMs: Long = 0L

    init {
        // Collect preferences
        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow.collect { prefs ->
                val sourceChanged = _uiState.value.preferences.activeSource != prefs.activeSource
                val windowHoursChanged = _uiState.value.preferences.chartWindowHours != prefs.chartWindowHours
                _uiState.value = _uiState.value.copy(
                    preferences = prefs,
                    preferencesLoaded = true,
                    selectedWindowHours = prefs.chartWindowHours
                )
                if (sourceChanged || windowHoursChanged) {
                    refreshWindowReadings(prefs.activeSource.name, prefs.chartWindowHours)
                }
            }
        }

        // Collect sync status
        viewModelScope.launch {
            syncEngine.syncStatus.collect { status ->
                _uiState.value = _uiState.value.copy(syncStatus = status)
            }
        }

        // Collect latest reading from Room filtered by active source
        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow
                .flatMapLatest { prefs ->
                    val sourceId = prefs.activeSource.name
                    repository.getLatestReading(sourceId).map { latest ->
                        Pair(prefs, latest)
                    }
                }
                .collect { (prefs, latest) ->
                    val sourceId = prefs.activeSource.name
                    val prev = if (latest != null) repository.getPreviousReading(sourceId, latest.measuredAtUtcMs) else null
                    val now = System.currentTimeMillis() + prefs.lastServerClockOffsetMs
                    val freshness = GlucoseUtils.computeFreshness(latest, now, prefs.thresholds)
                    _uiState.value = _uiState.value.copy(
                        latestReading = latest,
                        previousReading = prev,
                        freshness = freshness
                    )
                    refreshWindowReadings(sourceId, _uiState.value.selectedWindowHours)
                }
        }

        // 1-second ticker: recalculates age, freshness, night dimming, and PIN lockout
        viewModelScope.launch {
            while (isActive) {
                val prefs = _uiState.value.preferences
                val now = System.currentTimeMillis() + prefs.lastServerClockOffsetMs
                val latest = _uiState.value.latestReading
                val freshness = GlucoseUtils.computeFreshness(latest, now, prefs.thresholds)

                // Night dimming check
                val isDimmed = checkNightDimming(prefs)

                // PIN lockout remaining
                val realNow = System.currentTimeMillis()
                val remainingLockout = if (lockedUntilMs > realNow) {
                    ((lockedUntilMs - realNow + 999) / 1000).toInt()
                } else {
                    0
                }

                _uiState.value = _uiState.value.copy(
                    currentNowMs = now,
                    freshness = freshness,
                    isNightDimmed = isDimmed,
                    pinLockoutSeconds = remainingLockout
                )
                delay(1000)
            }
        }

        // Pixel shift timer: shifts 1-2 px every 3 minutes to prevent burn-in
        viewModelScope.launch {
            var step = 0
            while (isActive) {
                delay(180_000) // 3 minutes
                if (_uiState.value.preferences.pixelShiftEnabled) {
                    step = (step + 1) % 4
                    val (dx, dy) = when (step) {
                        0 -> Pair(0f, 0f)
                        1 -> Pair(1.5f, 0f)
                        2 -> Pair(1.5f, 1.5f)
                        else -> Pair(0f, 1.5f)
                    }
                    _uiState.value = _uiState.value.copy(pixelShiftX = dx, pixelShiftY = dy)
                }
            }
        }
    }

    fun selectWindowHours(hours: Int) {
        _uiState.value = _uiState.value.copy(selectedWindowHours = hours)
        refreshWindowReadings(_uiState.value.preferences.activeSource.name, hours)
    }

    private fun refreshWindowReadings(sourceId: String, hours: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val startMs = now - (hours * 3600 * 1000L)
            val readings = repository.getReadingsBetweenSync(sourceId, startMs, now)
            _uiState.value = _uiState.value.copy(windowReadings = readings)
        }
    }

    private fun checkNightDimming(prefs: UserPreferences): Boolean {
        if (!prefs.dimmingScheduleEnabled) return false
        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = prefs.dimmingStartHour * 60 + prefs.dimmingStartMinute
        val endMinutes = prefs.dimmingEndHour * 60 + prefs.dimmingEndMinute

        return if (startMinutes < endMinutes) {
            currentMinutes in startMinutes until endMinutes
        } else {
            // Over midnight (e.g. 23:00 to 07:00)
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            syncEngine.syncOnce()
        }
    }

    fun onSettingsRequested(onProceed: () -> Unit) {
        val creds = keystoreManager.loadCredentials()
        val pinEnabled = _uiState.value.preferences.pinEnabled
        if (pinEnabled && creds.pinHash.isNotBlank()) {
            _uiState.value = _uiState.value.copy(pinDialogOpen = true, pinError = false)
        } else {
            onProceed()
        }
    }

    fun dismissPinDialog() {
        _uiState.value = _uiState.value.copy(pinDialogOpen = false, pinError = false)
    }

    fun verifyPin(enteredPin: String, onValid: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now < lockedUntilMs) {
            val remaining = ((lockedUntilMs - now + 999) / 1000).toInt()
            _uiState.value = _uiState.value.copy(pinError = true, pinLockoutSeconds = remaining)
            return
        }

        val creds = keystoreManager.loadCredentials()
        val result = CryptoUtils.verifyPin(enteredPin, creds.pinHash)
        if (result is CryptoUtils.PinVerificationResult.Valid) {
            failedPinAttempts = 0
            currentLockoutSec = 30
            lockedUntilMs = 0L
            _uiState.value = _uiState.value.copy(
                pinDialogOpen = false,
                pinError = false,
                pinLockoutSeconds = 0
            )

            // Migrate legacy SHA-256 hash if needed
            if (result.migratedHash != null) {
                keystoreManager.updateCredentials { it.copy(pinHash = result.migratedHash) }
            }

            onValid()
        } else {
            failedPinAttempts++
            if (failedPinAttempts >= 5) {
                failedPinAttempts = 0
                val lockout = currentLockoutSec
                lockedUntilMs = System.currentTimeMillis() + (lockout * 1000L)
                currentLockoutSec = (currentLockoutSec * 2).coerceAtMost(600) // max 10 min
                _uiState.value = _uiState.value.copy(
                    pinError = true,
                    pinLockoutSeconds = lockout
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    pinError = true,
                    pinLockoutSeconds = 0
                )
            }
        }
    }
}
