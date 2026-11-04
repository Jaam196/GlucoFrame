package com.example

import android.app.Application
import com.example.core.common.GlucoseSurfaceUpdater
import com.example.core.designsystem.GlucoseColors
import com.example.core.database.GlucoseDatabase
import com.example.core.database.background.BackgroundDatabase
import com.example.core.datastore.BackgroundPreferencesRepository
import com.example.core.datastore.DisplayStyleRepository
import com.example.core.model.DisplayStyle
import com.example.data.lan.LanServerController
import com.example.core.datastore.PreferencesRepository
import com.example.core.model.GlucoseSurfaceState
import com.example.core.model.GlucoseUnit
import com.example.core.model.GlucoseZone
import com.example.core.model.ThresholdSettings
import com.example.core.security.KeystoreManager
import com.example.data.background.BackgroundRepository
import com.example.data.repository.GlucoseRepository
import com.example.sync.SyncEngine
import com.example.sync.SyncWatchdogWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class GlucoseFrameApplication : Application() {

    lateinit var database: GlucoseDatabase
        private set

    lateinit var repository: GlucoseRepository
        private set

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var keystoreManager: KeystoreManager
        private set

    lateinit var syncEngine: SyncEngine
        private set

    lateinit var backgroundDatabase: BackgroundDatabase
        private set

    lateinit var backgroundPreferencesRepository: BackgroundPreferencesRepository
        private set

    lateinit var backgroundRepository: BackgroundRepository
        private set

    lateinit var displayStyleRepository: DisplayStyleRepository
        private set

    /** Latest style chosen by the user; read by the widgets (which are not Compose). */
    @Volatile
    var displayStyle: DisplayStyle = DisplayStyle()
        private set

    lateinit var lanServerController: LanServerController
        private set

    @Volatile
    var isUiVisible: Boolean = false

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    var currentSurfaceState: GlucoseSurfaceState = GlucoseSurfaceState.from(
        reading = null,
        thresholds = ThresholdSettings(),
        unit = GlucoseUnit.MG_DL
    )
        private set

    private var lastPublishedSurfaceState: GlucoseSurfaceState? = null
    private var lastPublishedLockscreenSetting: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.example.core.common.CrashLogger.install(this)

        database = GlucoseDatabase.getInstance(this)
        repository = GlucoseRepository(database)
        preferencesRepository = PreferencesRepository(this)
        keystoreManager = KeystoreManager(this)
        syncEngine = SyncEngine(this, repository, preferencesRepository, keystoreManager)

        backgroundDatabase = BackgroundDatabase.getInstance(this)
        backgroundPreferencesRepository = BackgroundPreferencesRepository(this)
        backgroundRepository = BackgroundRepository(this, backgroundDatabase)
        displayStyleRepository = DisplayStyleRepository(this)

        com.example.data.librelinkup.SensorInfoStore.load(this)

        lanServerController = LanServerController(this)
        lanServerController.start()

        initDisplayStyleObserver()
        initPaletteObserver()
        initSurfaceObservers()

        // Make sure the background wake-up alarm chain exists (reading glucose must never silently stop)
        com.example.sync.SyncAlarm.ensureScheduled(this, 30_000L)

        // Schedule 15-minute background recovery watchdog
        try {
            SyncWatchdogWorker.schedule(this)
        } catch (e: Exception) {
            // Ignored in unit testing environments where WorkManager is uninitialized
        }
    }

    /** Keeps the widget style up to date and redraws the widgets when the user changes it. */
    private fun initDisplayStyleObserver() {
        appScope.launch {
            displayStyleRepository.styleFlow.collect { style ->
                displayStyle = style
                // Before the first reading is published the surface observer draws the widgets itself
                if (lastPublishedSurfaceState != null) {
                    try {
                        GlucoseSurfaceUpdater.updateAllWidgets(this@GlucoseFrameApplication, currentSurfaceState)
                        GlucoseSurfaceUpdater.updateGraphWidgets(this@GlucoseFrameApplication)
                        val userPrefs = preferencesRepository.userPreferencesFlow.first()
                        if (userPrefs.statusBarNotificationEnabled && userPrefs.notificationsEnabled) {
                            GlucoseSurfaceUpdater.updateNotification(
                                this@GlucoseFrameApplication,
                                currentSurfaceState,
                                userPrefs.hideOnLockscreen
                            )
                        }
                    } catch (e: Exception) {
                        // Ignore: widgets refresh again on the next reading
                    }
                }
            }
        }
    }

    /**
     * Keeps the glucose-level colors chosen by the user in sync with the number, chart, widgets and
     * notification. Widgets/notification are redrawn when a color changes.
     */
    private fun initPaletteObserver() {
        appScope.launch {
            var first = true
            backgroundPreferencesRepository.backgroundPreferencesFlow
                .map { prefs -> GlucoseZone.entries.map { prefs.colorHexForZone(it) } to prefs }
                .distinctUntilChanged { a, b -> a.first == b.first }
                .collect { (_, prefs) ->
                    GlucoseColors.applyPalette(prefs)
                    if (!first) {
                        try {
                            val userPrefs = preferencesRepository.userPreferencesFlow.first()
                            GlucoseSurfaceUpdater.updateAllWidgets(this@GlucoseFrameApplication, currentSurfaceState)
                            GlucoseSurfaceUpdater.updateGraphWidgets(this@GlucoseFrameApplication)
                            if (userPrefs.statusBarNotificationEnabled && userPrefs.notificationsEnabled) {
                                GlucoseSurfaceUpdater.updateNotification(
                                    this@GlucoseFrameApplication,
                                    currentSurfaceState,
                                    userPrefs.hideOnLockscreen
                                )
                            }
                        } catch (e: Exception) {
                            // Ignore: surfaces refresh again on the next reading
                        }
                    }
                    first = false
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun initSurfaceObservers() {
        val tickFlow = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(60_000L)
            }
        }

        appScope.launch {
            preferencesRepository.userPreferencesFlow
                .flatMapLatest { prefs ->
                    combine(
                        repository.getLatestReading(prefs.activeSource.name),
                        tickFlow
                    ) { reading, nowMs ->
                        Triple(reading, prefs, nowMs)
                    }
                }
                .collect { (reading, prefs, nowMs) ->
                    val previous = if (reading != null) {
                        repository.getPreviousReading(prefs.activeSource.name, reading.measuredAtUtcMs)
                    } else {
                        null
                    }
                    val surfaceState = GlucoseSurfaceState.from(
                        reading = reading,
                        thresholds = prefs.thresholds,
                        unit = prefs.unit,
                        nowMs = nowMs,
                        previousReading = previous
                    )
                    currentSurfaceState = surfaceState

                    // Glucose alarms (only when the user turned them on)
                    com.example.core.common.GlucoseAlarms.evaluate(this@GlucoseFrameApplication, surfaceState, prefs)

                    val stateChanged = surfaceState != lastPublishedSurfaceState
                    val lockscreenChanged = prefs.hideOnLockscreen != lastPublishedLockscreenSetting

                    if (stateChanged || lockscreenChanged) {
                        lastPublishedSurfaceState = surfaceState
                        lastPublishedLockscreenSetting = prefs.hideOnLockscreen

                        // Update widgets
                        GlucoseSurfaceUpdater.updateAllWidgets(this@GlucoseFrameApplication, surfaceState)
                        GlucoseSurfaceUpdater.updateGraphWidgets(this@GlucoseFrameApplication)

                        // Update notification if active
                        if (prefs.statusBarNotificationEnabled && prefs.notificationsEnabled) {
                            GlucoseSurfaceUpdater.updateNotification(
                                this@GlucoseFrameApplication,
                                surfaceState,
                                prefs.hideOnLockscreen
                            )
                        }
                    }
                }
        }
    }

    companion object {
        lateinit var instance: GlucoseFrameApplication
            private set

        /** The Application if it already exists (safe to call from tests / early code). */
        fun instanceOrNull(): GlucoseFrameApplication? =
            if (this::instance.isInitialized) instance else null
    }
}
