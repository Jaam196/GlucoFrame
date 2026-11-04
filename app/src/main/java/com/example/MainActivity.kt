package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.designsystem.GlucoseFrameTheme
import com.example.core.designsystem.LocalDisplayStyle
import com.example.core.model.GlucoseZone
import com.example.core.model.OrientationOption
import com.example.core.model.SyncResult
import com.example.data.source.SourceResult
import com.example.core.datastore.needsForegroundService
import com.example.feature.background.BackgroundViewModel
import com.example.service.GlucoseForegroundService
import com.example.sync.SyncAlarm
import com.example.feature.background.settings.BackgroundSettingsScreen
import com.example.feature.background.settings.BackgroundSettingsViewModel
import com.example.feature.community.CommunityBackgroundsScreen
import com.example.feature.community.CommunityBackgroundsViewModel
import com.example.feature.displaystyle.DisplayStyleScreen
import com.example.feature.displaystyle.DisplayStyleViewModel
import com.example.feature.frame.FrameScreen
import com.example.feature.frame.FrameViewModel
import com.example.feature.settings.MedicalDisclaimerDialog
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ScreenState {
    FRAME,
    SETTINGS,
    BACKGROUND_SETTINGS,
    DISPLAY_STYLE,
    COMMUNITY_BACKGROUNDS
}

class MainActivity : ComponentActivity() {

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastKeepScreenOn: Boolean = true
    private var lastKeepScreenOnOnlyCharging: Boolean = false
    private var batteryReceiver: BroadcastReceiver? = null

    // True when Android does not restrict this app's battery use (needed for readings in the background)
    private val batteryIgnoredState = mutableStateOf(true)

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                true
            } else {
                (getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(packageName)
            }
        } catch (e: Exception) {
            true
        }
    }

    override fun onResume() {
        super.onResume()
        batteryIgnoredState.value = isIgnoringBatteryOptimizations()
    }

    override fun onStart() {
        super.onStart()
        (application as GlucoseFrameApplication).isUiVisible = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    applyKeepScreenOn(lastKeepScreenOn, lastKeepScreenOnOnlyCharging)
                }
            }
        }
        batteryReceiver = receiver
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onStop() {
        super.onStop()
        (application as GlucoseFrameApplication).isUiVisible = false
        batteryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore
            }
            batteryReceiver = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hide system bars for immersive kiosk frame
        hideSystemBars()

        val app = application as GlucoseFrameApplication

        // TODO_ANDROID_STUDIO: Comprobar el comportamiento de reconexión de red con sensor FreeStyle Libre 2 Plus real y cuenta de seguidor.
        registerNetworkCallback(app)

        // Main synchronization loop bound to Activity Lifecycle (repeatOnLifecycle STARTED)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Make sure the background reading machinery is alive every time the app opens
                try {
                    val startPrefs = app.preferencesRepository.userPreferencesFlow.first()
                    if (startPrefs.needsForegroundService() && !GlucoseForegroundService.isRunning) {
                        GlucoseForegroundService.start(app)
                    }
                    SyncAlarm.ensureScheduled(app, 60_000L)
                } catch (e: Exception) {
                    // Never block the screen because of this
                }

                // Immediate sync on start (with full graph backfill)
                app.syncEngine.syncOnce(forceGraph = true)

                while (isActive) {
                    val loopPrefs = app.preferencesRepository.userPreferencesFlow.first()
                    // A local server on the home network can be asked often (cheap, no cloud limits)
                    val intervalSec = if (loopPrefs.activeSource == com.example.core.model.ActiveSourceType.JUGGLUCO) {
                        minOf(loopPrefs.syncIntervalSeconds, 20)
                    } else {
                        loopPrefs.syncIntervalSeconds
                    }

                    delay(intervalSec * 1000L)

                    val result = app.syncEngine.syncOnce()
                    if (result is SourceResult.Error) {
                        if (result.error.resultType == SyncResult.RATE_LIMIT && result.error.retryDelaySeconds > 0) {
                            delay(result.error.retryDelaySeconds * 1000L)
                        } else if (result.error.pauseLoop) {
                            // Pause loop on bad credentials or terms pending until user resolves
                            delay(60_000L)
                        }
                    }
                }
            }
        }

        setContent {
            val frameViewModel: FrameViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        FrameViewModel(
                            repository = app.repository,
                            preferencesRepository = app.preferencesRepository,
                            keystoreManager = app.keystoreManager,
                            syncEngine = app.syncEngine
                        )
                    }
                }
            )

            val settingsViewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(
                            context = app,
                            repository = app.repository,
                            preferencesRepository = app.preferencesRepository,
                            keystoreManager = app.keystoreManager,
                            syncEngine = app.syncEngine
                        )
                    }
                }
            )

            val backgroundViewModel: BackgroundViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        BackgroundViewModel(
                            context = app,
                            backgroundRepository = app.backgroundRepository,
                            backgroundPreferencesRepository = app.backgroundPreferencesRepository
                        )
                    }
                }
            )

            val backgroundSettingsViewModel: BackgroundSettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        BackgroundSettingsViewModel(
                            context = app,
                            backgroundRepository = app.backgroundRepository,
                            backgroundPreferencesRepository = app.backgroundPreferencesRepository,
                            glucoseRepository = app.repository
                        )
                    }
                }
            )

            val displayStyleViewModel: DisplayStyleViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        DisplayStyleViewModel(
                            styleRepository = app.displayStyleRepository,
                            backgroundPreferencesRepository = app.backgroundPreferencesRepository,
                            preferencesRepository = app.preferencesRepository
                        )
                    }
                }
            )

            val communityViewModel: CommunityBackgroundsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        CommunityBackgroundsViewModel(
                            communityRepository = com.example.data.community.CommunityBackgroundsRepository(app),
                            backgroundRepository = app.backgroundRepository,
                            backgroundPreferencesRepository = app.backgroundPreferencesRepository,
                            appContext = app
                        )
                    }
                }
            )
            val communityState by communityViewModel.uiState.collectAsState()

            val displayStyleState by displayStyleViewModel.uiState.collectAsState()
            val frameState by frameViewModel.uiState.collectAsState()
            val settingsState by settingsViewModel.uiState.collectAsState()
            val backgroundState by backgroundViewModel.uiState.collectAsState()
            val backgroundSettingsState by backgroundSettingsViewModel.uiState.collectAsState()

            // Update background state on glucose or night changes
            LaunchedEffect(
                frameState.latestReading,
                frameState.freshness,
                frameState.isNightDimmed,
                frameState.preferences.dimmingUseBlackScreen
            ) {
                val zone = frameState.latestReading?.let {
                    frameState.preferences.thresholds.zoneFor(it.valueMgDl)
                } ?: GlucoseZone.IN_RANGE
                val isNightBlack = frameState.isNightDimmed && frameState.preferences.dimmingUseBlackScreen
                backgroundViewModel.updateGlucoseState(zone, frameState.freshness, isNightBlack)
            }

            // Apply screen orientation and keep-screen-on based on preferences
            LaunchedEffect(frameState.preferences.orientation) {
                applyOrientation(frameState.preferences.orientation)
            }

            LaunchedEffect(frameState.preferences.keepScreenOn, frameState.preferences.keepScreenOnOnlyCharging) {
                applyKeepScreenOn(
                    frameState.preferences.keepScreenOn,
                    frameState.preferences.keepScreenOnOnlyCharging
                )
            }

            var currentScreen by remember { mutableStateOf(ScreenState.FRAME) }

            GlucoseFrameTheme(themeMode = frameState.preferences.themeMode) {
              CompositionLocalProvider(LocalDisplayStyle provides displayStyleState.style) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentScreen) {
                            ScreenState.FRAME -> {
                                FrameScreen(
                                    state = frameState,
                                    backgroundState = backgroundState,
                                    onSelectWindow = { hours -> frameViewModel.selectWindowHours(hours) },
                                    onNavigateToSettings = { currentScreen = ScreenState.SETTINGS },
                                    onToggleNotifications = { enabled -> settingsViewModel.setNotificationsEnabled(enabled) },
                                    showBatteryWarning = !batteryIgnoredState.value,
                                    onFixBattery = { settingsViewModel.requestIgnoreBatteryOptimization(this@MainActivity) },
                                    onVerifyPin = { pin, onValid -> frameViewModel.verifyPin(pin, onValid) },
                                    onDismissPin = { frameViewModel.dismissPinDialog() },
                                    onRequestSettingsWithPin = { onProceed ->
                                        frameViewModel.onSettingsRequested(onProceed)
                                    }
                                )
                            }
                            ScreenState.SETTINGS -> {
                                SettingsScreen(
                                    state = settingsState,
                                    viewModel = settingsViewModel,
                                    onBackToFrame = {
                                        hideSystemBars()
                                        currentScreen = ScreenState.FRAME
                                    },
                                    onNavigateToBackgroundSettings = {
                                        currentScreen = ScreenState.BACKGROUND_SETTINGS
                                    },
                                    onNavigateToDisplayStyle = {
                                        currentScreen = ScreenState.DISPLAY_STYLE
                                    }
                                )
                            }
                            ScreenState.DISPLAY_STYLE -> {
                                DisplayStyleScreen(
                                    state = displayStyleState,
                                    viewModel = displayStyleViewModel,
                                    onBack = {
                                        currentScreen = ScreenState.SETTINGS
                                    }
                                )
                            }
                            ScreenState.BACKGROUND_SETTINGS -> {
                                BackHandler {
                                    currentScreen = ScreenState.SETTINGS
                                }
                                BackgroundSettingsScreen(
                                    state = backgroundSettingsState,
                                    viewModel = backgroundSettingsViewModel,
                                    onBack = {
                                        currentScreen = ScreenState.SETTINGS
                                    },
                                    onOpenCommunity = {
                                        currentScreen = ScreenState.COMMUNITY_BACKGROUNDS
                                    }
                                )
                            }
                            ScreenState.COMMUNITY_BACKGROUNDS -> {
                                CommunityBackgroundsScreen(
                                    state = communityState,
                                    viewModel = communityViewModel,
                                    onBack = {
                                        currentScreen = ScreenState.BACKGROUND_SETTINGS
                                    }
                                )
                            }
                        }

                        // First-use Medical Disclaimer
                        if (frameState.preferencesLoaded && !frameState.preferences.medicalDisclaimerAccepted) {
                            MedicalDisclaimerDialog(
                                onAccept = {
                                    settingsViewModel.setMedicalDisclaimerAccepted(true)
                                }
                            )
                        }
                    }
                }
              }
            }
        }
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun applyOrientation(option: OrientationOption) {
        // TODO_ANDROID_STUDIO: Comprobar el bloqueo de orientación fija (requestedOrientation) y la propiedad PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY en la tablet física con Android 16+.
        requestedOrientation = when (option) {
            OrientationOption.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationOption.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationOption.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    private fun applyKeepScreenOn(keepOn: Boolean, onlyWhileCharging: Boolean) {
        lastKeepScreenOn = keepOn
        lastKeepScreenOnOnlyCharging = onlyWhileCharging
        if (!keepOn) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            return
        }

        if (onlyWhileCharging) {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = registerReceiver(null, batteryFilter)
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            if (isCharging) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun registerNetworkCallback(app: GlucoseFrameApplication) {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    // Network restored, immediately sync
                    lifecycleScope.launch {
                        app.syncEngine.syncOnce()
                    }
                }
            }
            networkCallback?.let { connectivityManager?.registerNetworkCallback(request, it) }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
