package com.example.feature.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.CryptoUtils
import com.example.core.common.GlucoseUtils
import com.example.core.datastore.PreferencesRepository
import com.example.core.datastore.UserPreferences
import com.example.core.datastore.needsForegroundService
import com.example.core.model.ActiveSourceType
import com.example.core.model.AppThemeMode
import com.example.core.model.GlucoseReading
import com.example.core.model.GlucoseUnit
import com.example.core.model.OrientationOption
import com.example.core.model.SyncEvent
import com.example.core.model.ThresholdSettings
import com.example.core.security.KeystoreManager
import com.example.core.security.SecureCredentials
import com.example.data.librelinkup.LibreLinkUpSource
import com.example.data.repository.GlucoseRepository
import com.example.data.source.SourceResult
import com.example.sync.SyncEngine
import com.example.sync.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val credentials: SecureCredentials = SecureCredentials(),
    val syncStatus: SyncStatus = SyncStatus(),
    val latestReading: GlucoseReading? = null,
    val recentEvents: List<SyncEvent> = emptyList(),
    val availablePatients: List<Pair<String, String>> = emptyList(),
    val testConnectionRunning: Boolean = false,
    val testConnectionMessage: String? = null,
    val isBatteryOptimizationIgnored: Boolean = false,
    val credentialsUnreadable: Boolean = false,
    val historyReadings: List<GlucoseReading> = emptyList(),
    val historyAverageMgDl: Double = 0.0,
    val historyTimeInRangePercent: Double = 0.0,
    val historyTimeBelowPercent: Double = 0.0,
    val historyTimeAbovePercent: Double = 0.0,
    val historyTotalCount: Int = 0
)

class SettingsViewModel(
    private val context: Context,
    private val repository: GlucoseRepository,
    private val preferencesRepository: PreferencesRepository,
    private val keystoreManager: KeystoreManager,
    private val syncEngine: SyncEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadData() {
        viewModelScope.launch {
            var lastSource: ActiveSourceType? = null
            preferencesRepository.userPreferencesFlow.collect { prefs ->
                _uiState.value = _uiState.value.copy(preferences = prefs)
                if (lastSource != null && lastSource != prefs.activeSource) {
                    loadHistoryStats(7)
                }
                lastSource = prefs.activeSource
            }
        }

        viewModelScope.launch {
            syncEngine.syncStatus.collect { status ->
                _uiState.value = _uiState.value.copy(syncStatus = status)
            }
        }

        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow
                .flatMapLatest { prefs ->
                    repository.getLatestReading(prefs.activeSource.name)
                }
                .collect { reading ->
                    _uiState.value = _uiState.value.copy(latestReading = reading)
                }
        }

        viewModelScope.launch {
            repository.getRecentSyncEvents(200).collect { events ->
                _uiState.value = _uiState.value.copy(recentEvents = events)
            }
        }

        viewModelScope.launch {
            keystoreManager.credentialsUnreadable.collect { unreadable ->
                _uiState.value = _uiState.value.copy(credentialsUnreadable = unreadable)
            }
        }

        // Load credentials and battery status
        _uiState.value = _uiState.value.copy(
            credentials = keystoreManager.loadCredentials(),
            isBatteryOptimizationIgnored = checkBatteryOptimization()
        )

        loadHistoryStats(days = 7)
    }

    fun checkBatteryOptimization(): Boolean {
        // Battery optimization (Doze) only exists from Android 6 (API 23); older versions have none.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    fun requestIgnoreBatteryOptimization(activity: Activity) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general battery settings
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            activity.startActivity(intent)
        }
    }

    fun openAppSystemSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        activity.startActivity(intent)
    }

    fun toggleKioskHomeLauncher(enable: Boolean) {
        viewModelScope.launch {
            try {
                val pkgMgr = context.packageManager
                val aliasComponent = ComponentName(context, "com.example.HomeLauncherAlias")
                val newState = if (enable) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                pkgMgr.setComponentEnabledSetting(
                    aliasComponent,
                    newState,
                    PackageManager.DONT_KILL_APP
                )
                preferencesRepository.setKioskHomeEnabled(enable)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun saveLibreCredentials(email: String, pass: String) {
        val trimmedEmail = email.trim()
        var emailChanged = false

        val updated = keystoreManager.updateCredentials { cur ->
            if (cur.libreEmail != trimmedEmail) {
                emailChanged = true
                cur.copy(
                    libreEmail = trimmedEmail,
                    librePassword = pass,
                    libreToken = "",
                    libreTokenExpiresMs = 0L,
                    libreUserId = "",
                    libreRegion = "",
                    librePatientId = "",
                    librePatientName = ""
                )
            } else if (cur.librePassword != pass) {
                cur.copy(
                    librePassword = pass,
                    libreToken = "",
                    libreTokenExpiresMs = 0L
                )
            } else {
                cur
            }
        }

        if (emailChanged) {
            viewModelScope.launch {
                preferencesRepository.updateLluRegion("")
            }
        }
        _uiState.value = _uiState.value.copy(credentials = updated)
    }

    fun selectLibrePatient(patientId: String, patientName: String) {
        val updated = keystoreManager.updateCredentials { cur ->
            cur.copy(librePatientId = patientId, librePatientName = patientName)
        }
        _uiState.value = _uiState.value.copy(credentials = updated)
    }

    fun saveNightscoutCredentials(url: String, token: String, secret: String) {
        val updated = keystoreManager.updateCredentials { cur ->
            cur.copy(
                nightscoutUrl = url.trim(),
                nightscoutToken = token.trim(),
                nightscoutSecret = secret.trim()
            )
        }
        _uiState.value = _uiState.value.copy(credentials = updated)
    }

    fun saveJugglucoCredentials(url: String, token: String) {
        val updated = keystoreManager.updateCredentials { cur ->
            cur.copy(
                jugglucoUrl = url.trim(),
                jugglucoToken = token.trim()
            )
        }
        _uiState.value = _uiState.value.copy(credentials = updated)
    }

    fun savePin(pin: String) {
        viewModelScope.launch {
            val hash = if (pin.isBlank()) "" else CryptoUtils.hashPinPbkdf2(pin)
            val updated = keystoreManager.updateCredentials { cur ->
                cur.copy(pinHash = hash)
            }
            preferencesRepository.updatePinEnabled(hash.isNotBlank())
            _uiState.value = _uiState.value.copy(credentials = updated)
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                testConnectionRunning = true,
                testConnectionMessage = null
            )
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val result = syncEngine.testActiveSource()

            val msg = when (result) {
                is SourceResult.Success -> {
                    if (result.readings.isNotEmpty()) {
                        val first = result.readings.first()
                        val formattedVal = GlucoseUtils.formatValue(first.valueMgDl, prefs.unit)
                        "Éxito: Conexión establecida. Lectura recibida: $formattedVal ${prefs.unit.label} (${first.trend.descriptionEs})"
                    } else {
                        "Éxito: Conexión establecida correctamente"
                    }
                }
                is SourceResult.Error -> {
                    "Error: ${result.error.message}"
                }
            }

            // If LibreLinkUp, also refresh patient list
            if (prefs.activeSource == ActiveSourceType.LIBRELINKUP) {
                val lluSource = syncEngine.getSourceForType(prefs.activeSource) as? LibreLinkUpSource
                val patients = lluSource?.fetchConnectionsList() ?: emptyList()
                _uiState.value = _uiState.value.copy(availablePatients = patients)
            }

            _uiState.value = _uiState.value.copy(
                testConnectionRunning = false,
                testConnectionMessage = msg
            )
        }
    }

    fun loadHistoryStats(days: Int) {
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val now = System.currentTimeMillis()
            val startMs = now - (days.toLong() * 24 * 3600 * 1000L)
            val rawReadings = repository.getReadingsBetweenSync(prefs.activeSource.name, startMs, now)

            if (rawReadings.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    historyReadings = emptyList(),
                    historyAverageMgDl = 0.0,
                    historyTimeInRangePercent = 0.0,
                    historyTimeBelowPercent = 0.0,
                    historyTimeAbovePercent = 0.0,
                    historyTotalCount = 0
                )
                return@launch
            }

            val readings = rawReadings.sortedBy { it.measuredAtUtcMs }
            val count = readings.size
            val th = prefs.thresholds

            var totalWeight = 0.0
            var weightedValueSum = 0.0
            var weightBelow = 0.0
            var weightInRange = 0.0
            var weightAbove = 0.0

            for (i in readings.indices) {
                val weight = if (i < readings.size - 1) {
                    val diffMs = readings[i + 1].measuredAtUtcMs - readings[i].measuredAtUtcMs
                    val diffMin = diffMs.toDouble() / (60.0 * 1000.0)
                    diffMin.coerceIn(0.0, 20.0)
                } else {
                    5.0
                }

                val value = readings[i].valueMgDl
                totalWeight += weight
                weightedValueSum += value * weight

                if (value < th.lowMgDl) {
                    weightBelow += weight
                } else if (value > th.highMgDl) {
                    weightAbove += weight
                } else {
                    weightInRange += weight
                }
            }

            val avg = if (totalWeight > 0) weightedValueSum / totalWeight else 0.0
            val pctBelow = if (totalWeight > 0) (weightBelow * 100.0) / totalWeight else 0.0
            val pctInRange = if (totalWeight > 0) (weightInRange * 100.0) / totalWeight else 0.0
            val pctAbove = if (totalWeight > 0) (weightAbove * 100.0) / totalWeight else 0.0

            _uiState.value = _uiState.value.copy(
                historyReadings = readings,
                historyAverageMgDl = avg,
                historyTimeInRangePercent = pctInRange,
                historyTimeBelowPercent = pctBelow,
                historyTimeAbovePercent = pctAbove,
                historyTotalCount = count
            )
        }
    }

    fun exportCsv(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val now = System.currentTimeMillis()
            val startMs = now - (prefs.retentionDays.toLong() * 24 * 3600 * 1000L)
            val success = repository.exportToCsv(context, uri, prefs.unit, prefs.thresholds, startMs, now)
            onComplete(success)
        }
    }

    // Preference setters
    fun setActiveSource(source: ActiveSourceType) = viewModelScope.launch {
        preferencesRepository.updateActiveSource(source)
    }

    fun setUnit(unit: GlucoseUnit) = viewModelScope.launch {
        preferencesRepository.updateUnit(unit)
    }

    fun setThresholds(thresholds: ThresholdSettings) = viewModelScope.launch {
        preferencesRepository.updateThresholds(thresholds)
    }

    fun setThemeMode(theme: AppThemeMode) = viewModelScope.launch {
        preferencesRepository.updateThemeMode(theme)
    }

    fun setOrientation(orientation: OrientationOption) = viewModelScope.launch {
        preferencesRepository.updateOrientation(orientation)
    }

    fun setSyncInterval(seconds: Int) = viewModelScope.launch {
        preferencesRepository.updateSyncInterval(seconds)
    }

    fun setRetentionDays(days: Int) = viewModelScope.launch {
        preferencesRepository.updateRetentionDays(days)
    }

    fun setKeepScreenOn(keepOn: Boolean, onlyCharging: Boolean) = viewModelScope.launch {
        preferencesRepository.updateKeepScreenOn(keepOn, onlyCharging)
    }

    fun setPixelShift(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.updatePixelShift(enabled)
    }

    fun setDimmingSchedule(
        enabled: Boolean,
        startH: Int,
        startM: Int,
        endH: Int,
        endM: Int,
        blackScreen: Boolean
    ) = viewModelScope.launch {
        preferencesRepository.updateDimmingSchedule(enabled, startH, startM, endH, endM, blackScreen)
    }

    fun setLluAdvanced(domain: String, region: String, product: String, ver: String, autoTerms: Boolean) = viewModelScope.launch {
        preferencesRepository.updateLluAdvanced(domain, region, product, ver, autoTerms)
    }

    fun setMedicalDisclaimerAccepted(accepted: Boolean) = viewModelScope.launch {
        preferencesRepository.setMedicalDisclaimerAccepted(accepted)
    }

    /** Starts, restarts (to refresh its notification) or stops the background service depending on what needs it. */
    private suspend fun refreshForegroundService() {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        if (prefs.needsForegroundService()) {
            com.example.service.GlucoseForegroundService.start(context)
        } else {
            com.example.service.GlucoseForegroundService.stop(context)
            com.example.core.common.GlucoseSurfaceUpdater.cancelAllNotifications(context)
        }
    }

    fun setStatusBarNotificationEnabled(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.updateStatusBarNotificationEnabled(enabled)
        refreshForegroundService()
    }

    /** The bell button: turns every notification and alarm on or off without losing the individual settings. */
    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.updateNotificationsEnabled(enabled)
        if (!enabled) {
            com.example.core.common.GlucoseAlarms.clear(context)
            com.example.core.common.GlucoseSurfaceUpdater.cancelAllNotifications(context)
        } else {
            val app = context.applicationContext as? com.example.GlucoseFrameApplication
            val prefs = preferencesRepository.userPreferencesFlow.first()
            if (app != null && prefs.statusBarNotificationEnabled) {
                com.example.core.common.GlucoseSurfaceUpdater.updateNotification(context, app.currentSurfaceState, prefs.hideOnLockscreen)
            }
        }
        refreshForegroundService()
    }

    fun setKeepSyncingInBackground(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.updateKeepSyncing(enabled)
        refreshForegroundService()
    }

    /** Opens the Android screen that lets the user allow exact alarms (Android 12+), used to wake the phone on time. */
    fun openExactAlarmSettings(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                activity.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${activity.packageName}"))
                )
            }
        } catch (e: Exception) {
            openAppSystemSettings(activity)
        }
    }

    fun setAlarmsEnabled(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.updateAlarmsEnabled(enabled)
        if (!enabled) com.example.core.common.GlucoseAlarms.clear(context)
        refreshForegroundService()
    }

    fun setAlarmFlag(name: String, value: Boolean) = viewModelScope.launch {
        preferencesRepository.updateAlarmFlag(name, value)
    }

    fun setAlarmTimes(repeatMinutes: Int, snoozeMinutes: Int) = viewModelScope.launch {
        preferencesRepository.updateAlarmTimes(repeatMinutes, snoozeMinutes)
    }

    fun clearAlarmSnooze() = viewModelScope.launch {
        preferencesRepository.updateAlarmSnoozeUntil(0L)
    }

    fun testAlarm() = viewModelScope.launch {
        com.example.core.common.GlucoseAlarms.postTest(context, preferencesRepository.userPreferencesFlow.first())
    }

    /** Turns local-network sharing on/off. A random token is created the first time and kept afterwards. */
    fun setLanServerEnabled(enabled: Boolean) = viewModelScope.launch {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val token = prefs.lanServerToken.ifBlank { CryptoUtils.generateToken() }
        preferencesRepository.updateLanServer(enabled, token)
        refreshForegroundService()
    }

    fun regenerateLanToken() = viewModelScope.launch {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        preferencesRepository.updateLanServer(prefs.lanServerEnabled, CryptoUtils.generateToken())
    }

    fun setHideOnLockscreen(hide: Boolean) = viewModelScope.launch {
        preferencesRepository.updateHideOnLockscreen(hide)
        val app = context.applicationContext as? com.example.GlucoseFrameApplication
        if (app != null && app.preferencesRepository.userPreferencesFlow.first().statusBarNotificationEnabled) {
            com.example.core.common.GlucoseSurfaceUpdater.updateNotification(context, app.currentSurfaceState, hide)
        }
    }

    fun setStartOnBoot(startOnBoot: Boolean) = viewModelScope.launch {
        preferencesRepository.updateStartOnBoot(startOnBoot)
    }
}
