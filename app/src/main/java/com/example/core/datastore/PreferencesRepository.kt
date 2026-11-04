package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.AccessGesture
import com.example.core.model.ActiveSourceType
import com.example.core.model.AppThemeMode
import com.example.core.model.GlucoseUnit
import com.example.core.model.OrientationOption
import com.example.core.model.ThresholdSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "glucose_frame_prefs")

data class UserPreferences(
    val activeSource: ActiveSourceType = ActiveSourceType.LIBRELINKUP,
    val unit: GlucoseUnit = GlucoseUnit.MG_DL,
    val thresholds: ThresholdSettings = ThresholdSettings(),
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val orientation: OrientationOption = OrientationOption.AUTO,
    val syncIntervalSeconds: Int = 60, // Minimum 60s
    val retentionDays: Int = 30, // 30 up to 90
    val keepScreenOn: Boolean = true,
    val keepScreenOnOnlyCharging: Boolean = false,
    val pixelShiftEnabled: Boolean = true,
    val dimmingScheduleEnabled: Boolean = false,
    val dimmingStartHour: Int = 23,
    val dimmingStartMinute: Int = 0,
    val dimmingEndHour: Int = 7,
    val dimmingEndMinute: Int = 0,
    val dimmingUseBlackScreen: Boolean = false,
    val accessGesture: AccessGesture = AccessGesture.LONG_PRESS_2S,
    val pinEnabled: Boolean = false,
    val chartWindowHours: Int = 3,
    // Advanced LibreLinkUp settings
    val lluDomain: String = "io",
    val lluRegion: String = "",
    val lluProduct: String = "llu.android",
    val lluVersion: String = "4.17.0",
    val lluAutoAcceptTerms: Boolean = true,
    // Disclaimer
    val medicalDisclaimerAccepted: Boolean = false,
    // Clock skew detected
    val clockSkewDetected: Boolean = false,
    val lastServerClockOffsetMs: Long = 0L,
    // Home Launcher alias
    val kioskHomeEnabled: Boolean = false,
    // Status bar notification and widget
    val statusBarNotificationEnabled: Boolean = false,
    val hideOnLockscreen: Boolean = false,
    val startOnBoot: Boolean = true,
    // Keep reading glucose in the background all the time (a small silent notification is the price)
    val keepSyncingInBackground: Boolean = true,
    // Local network sharing: this device serves its readings to other devices
    val lanServerEnabled: Boolean = false,
    val lanServerPort: Int = 17581,
    val lanServerToken: String = "",
    // Master switch for every notification and alarm (the bell button on the main screen)
    val notificationsEnabled: Boolean = true,
    // Glucose alarms (off by default; they never replace the official sensor/app alarms)
    val alarmsEnabled: Boolean = false,
    val alarmUrgentLow: Boolean = true,
    val alarmLow: Boolean = true,
    val alarmHigh: Boolean = true,
    val alarmVeryHigh: Boolean = true,
    val alarmObsolete: Boolean = false,
    val alarmSound: Boolean = true,
    val alarmFullScreen: Boolean = true,
    val alarmRepeatMinutes: Int = 5,
    val alarmSnoozeMinutes: Int = 30,
    val alarmSnoozeUntilMs: Long = 0L
)

/** The foreground service keeps notifications, alarms and the local server alive in the background. */
fun UserPreferences.needsForegroundService(): Boolean =
    keepSyncingInBackground || lanServerEnabled || (notificationsEnabled && (statusBarNotificationEnabled || alarmsEnabled))

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val ACTIVE_SOURCE = stringPreferencesKey("active_source")
        val GLUCOSE_UNIT = stringPreferencesKey("glucose_unit")
        val URGENT_LOW = intPreferencesKey("urgent_low")
        val LOW = intPreferencesKey("low")
        val TARGET_REF = intPreferencesKey("target_ref")
        val HIGH = intPreferencesKey("high")
        val VERY_HIGH = intPreferencesKey("very_high")
        val NEAR_LOW = intPreferencesKey("near_low")
        val ELEVATED = intPreferencesKey("elevated")
        val DELAYED_MIN = intPreferencesKey("delayed_min")
        val OBSOLETE_MIN = intPreferencesKey("obsolete_min")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ORIENTATION = stringPreferencesKey("orientation")
        val SYNC_INTERVAL = intPreferencesKey("sync_interval")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEEP_SCREEN_ON_CHARGING = booleanPreferencesKey("keep_screen_on_charging")
        val PIXEL_SHIFT = booleanPreferencesKey("pixel_shift")
        val DIMMING_ENABLED = booleanPreferencesKey("dimming_enabled")
        val DIMMING_START_HOUR = intPreferencesKey("dimming_start_hour")
        val DIMMING_START_MINUTE = intPreferencesKey("dimming_start_minute")
        val DIMMING_END_HOUR = intPreferencesKey("dimming_end_hour")
        val DIMMING_END_MINUTE = intPreferencesKey("dimming_end_minute")
        val DIMMING_BLACK_SCREEN = booleanPreferencesKey("dimming_black_screen")
        val ACCESS_GESTURE = stringPreferencesKey("access_gesture")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val CHART_WINDOW_HOURS = intPreferencesKey("chart_window_hours")
        val LLU_DOMAIN = stringPreferencesKey("llu_domain")
        val LLU_REGION = stringPreferencesKey("llu_region")
        val LLU_PRODUCT = stringPreferencesKey("llu_product")
        val LLU_VERSION = stringPreferencesKey("llu_version")
        val LLU_AUTO_ACCEPT_TERMS = booleanPreferencesKey("llu_auto_accept_terms")
        val MEDICAL_DISCLAIMER_ACCEPTED = booleanPreferencesKey("medical_disclaimer_accepted")
        val CLOCK_SKEW_DETECTED = booleanPreferencesKey("clock_skew_detected")
        val LAST_SERVER_CLOCK_OFFSET = longPreferencesKey("last_server_clock_offset")
        val KIOSK_HOME_ENABLED = booleanPreferencesKey("kiosk_home_enabled")
        val STATUS_BAR_NOTIFICATION = booleanPreferencesKey("status_bar_notification")
        val HIDE_ON_LOCKSCREEN = booleanPreferencesKey("hide_on_lockscreen")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val LAN_SERVER_ENABLED = booleanPreferencesKey("lan_server_enabled")
        val LAN_SERVER_PORT = intPreferencesKey("lan_server_port")
        val LAN_SERVER_TOKEN = stringPreferencesKey("lan_server_token")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEEP_SYNCING = booleanPreferencesKey("keep_syncing_background")
        val ALARMS_ENABLED = booleanPreferencesKey("alarms_enabled")
        val ALARM_URGENT_LOW = booleanPreferencesKey("alarm_urgent_low")
        val ALARM_LOW = booleanPreferencesKey("alarm_low")
        val ALARM_HIGH = booleanPreferencesKey("alarm_high")
        val ALARM_VERY_HIGH = booleanPreferencesKey("alarm_very_high")
        val ALARM_OBSOLETE = booleanPreferencesKey("alarm_obsolete")
        val ALARM_SOUND = booleanPreferencesKey("alarm_sound")
        val ALARM_FULL_SCREEN = booleanPreferencesKey("alarm_full_screen")
        val ALARM_REPEAT_MIN = intPreferencesKey("alarm_repeat_min")
        val ALARM_SNOOZE_MIN = intPreferencesKey("alarm_snooze_min")
        val ALARM_SNOOZE_UNTIL = longPreferencesKey("alarm_snooze_until")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { p ->
        val activeSource = try {
            ActiveSourceType.valueOf(p[Keys.ACTIVE_SOURCE] ?: ActiveSourceType.LIBRELINKUP.name)
        } catch (e: Exception) {
            ActiveSourceType.LIBRELINKUP
        }
        val unit = try {
            GlucoseUnit.valueOf(p[Keys.GLUCOSE_UNIT] ?: GlucoseUnit.MG_DL.name)
        } catch (e: Exception) {
            GlucoseUnit.MG_DL
        }
        val theme = try {
            AppThemeMode.valueOf(p[Keys.THEME_MODE] ?: AppThemeMode.DARK.name)
        } catch (e: Exception) {
            AppThemeMode.DARK
        }
        val orientation = try {
            OrientationOption.valueOf(p[Keys.ORIENTATION] ?: OrientationOption.AUTO.name)
        } catch (e: Exception) {
            OrientationOption.AUTO
        }
        val gesture = try {
            AccessGesture.valueOf(p[Keys.ACCESS_GESTURE] ?: AccessGesture.LONG_PRESS_2S.name)
        } catch (e: Exception) {
            AccessGesture.LONG_PRESS_2S
        }

        UserPreferences(
            activeSource = activeSource,
            unit = unit,
            thresholds = ThresholdSettings(
                urgentLowMgDl = p[Keys.URGENT_LOW] ?: 54,
                lowMgDl = p[Keys.LOW] ?: 70,
                targetReferenceMgDl = p[Keys.TARGET_REF] ?: 100,
                highMgDl = p[Keys.HIGH] ?: 180,
                veryHighMgDl = p[Keys.VERY_HIGH] ?: 250,
                nearLowMgDl = p[Keys.NEAR_LOW] ?: 90,
                elevatedMgDl = p[Keys.ELEVATED] ?: 140,
                delayedMinutes = p[Keys.DELAYED_MIN] ?: 5,
                obsoleteMinutes = (p[Keys.OBSOLETE_MIN] ?: 10).coerceAtMost(15)
            ),
            themeMode = theme,
            orientation = orientation,
            syncIntervalSeconds = (p[Keys.SYNC_INTERVAL] ?: 60).coerceAtLeast(60),
            retentionDays = (p[Keys.RETENTION_DAYS] ?: 30).coerceIn(7, 90),
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: true,
            keepScreenOnOnlyCharging = p[Keys.KEEP_SCREEN_ON_CHARGING] ?: false,
            pixelShiftEnabled = p[Keys.PIXEL_SHIFT] ?: true,
            dimmingScheduleEnabled = p[Keys.DIMMING_ENABLED] ?: false,
            dimmingStartHour = p[Keys.DIMMING_START_HOUR] ?: 23,
            dimmingStartMinute = p[Keys.DIMMING_START_MINUTE] ?: 0,
            dimmingEndHour = p[Keys.DIMMING_END_HOUR] ?: 7,
            dimmingEndMinute = p[Keys.DIMMING_END_MINUTE] ?: 0,
            dimmingUseBlackScreen = p[Keys.DIMMING_BLACK_SCREEN] ?: false,
            accessGesture = gesture,
            pinEnabled = p[Keys.PIN_ENABLED] ?: false,
            chartWindowHours = p[Keys.CHART_WINDOW_HOURS] ?: 3,
            lluDomain = p[Keys.LLU_DOMAIN] ?: "io",
            lluRegion = p[Keys.LLU_REGION] ?: "",
            lluProduct = p[Keys.LLU_PRODUCT] ?: "llu.android",
            lluVersion = p[Keys.LLU_VERSION] ?: "4.17.0",
            lluAutoAcceptTerms = p[Keys.LLU_AUTO_ACCEPT_TERMS] ?: true,
            medicalDisclaimerAccepted = p[Keys.MEDICAL_DISCLAIMER_ACCEPTED] ?: false,
            clockSkewDetected = p[Keys.CLOCK_SKEW_DETECTED] ?: false,
            lastServerClockOffsetMs = p[Keys.LAST_SERVER_CLOCK_OFFSET] ?: 0L,
            kioskHomeEnabled = p[Keys.KIOSK_HOME_ENABLED] ?: false,
            statusBarNotificationEnabled = p[Keys.STATUS_BAR_NOTIFICATION] ?: false,
            hideOnLockscreen = p[Keys.HIDE_ON_LOCKSCREEN] ?: false,
            startOnBoot = p[Keys.START_ON_BOOT] ?: true,
            keepSyncingInBackground = p[Keys.KEEP_SYNCING] ?: true,
            lanServerEnabled = p[Keys.LAN_SERVER_ENABLED] ?: false,
            lanServerPort = (p[Keys.LAN_SERVER_PORT] ?: 17581).coerceIn(1024, 65535),
            lanServerToken = p[Keys.LAN_SERVER_TOKEN] ?: "",
            notificationsEnabled = p[Keys.NOTIFICATIONS_ENABLED] ?: true,
            alarmsEnabled = p[Keys.ALARMS_ENABLED] ?: false,
            alarmUrgentLow = p[Keys.ALARM_URGENT_LOW] ?: true,
            alarmLow = p[Keys.ALARM_LOW] ?: true,
            alarmHigh = p[Keys.ALARM_HIGH] ?: true,
            alarmVeryHigh = p[Keys.ALARM_VERY_HIGH] ?: true,
            alarmObsolete = p[Keys.ALARM_OBSOLETE] ?: false,
            alarmSound = p[Keys.ALARM_SOUND] ?: true,
            alarmFullScreen = p[Keys.ALARM_FULL_SCREEN] ?: true,
            alarmRepeatMinutes = (p[Keys.ALARM_REPEAT_MIN] ?: 5).coerceIn(1, 60),
            alarmSnoozeMinutes = (p[Keys.ALARM_SNOOZE_MIN] ?: 30).coerceIn(5, 240),
            alarmSnoozeUntilMs = p[Keys.ALARM_SNOOZE_UNTIL] ?: 0L
        )
    }

    suspend fun updateActiveSource(source: ActiveSourceType) {
        context.dataStore.edit { it[Keys.ACTIVE_SOURCE] = source.name }
    }

    suspend fun updateUnit(unit: GlucoseUnit) {
        context.dataStore.edit { it[Keys.GLUCOSE_UNIT] = unit.name }
    }

    suspend fun updateThresholds(thresholds: ThresholdSettings) {
        context.dataStore.edit {
            it[Keys.URGENT_LOW] = thresholds.urgentLowMgDl
            it[Keys.LOW] = thresholds.lowMgDl
            it[Keys.TARGET_REF] = thresholds.targetReferenceMgDl
            it[Keys.HIGH] = thresholds.highMgDl
            it[Keys.VERY_HIGH] = thresholds.veryHighMgDl
            it[Keys.NEAR_LOW] = thresholds.nearLowMgDl
            it[Keys.ELEVATED] = thresholds.elevatedMgDl
            it[Keys.DELAYED_MIN] = thresholds.delayedMinutes
            it[Keys.OBSOLETE_MIN] = thresholds.obsoleteMinutes.coerceAtMost(15)
        }
    }

    suspend fun updateThemeMode(theme: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = theme.name }
    }

    suspend fun updateOrientation(orientation: OrientationOption) {
        context.dataStore.edit { it[Keys.ORIENTATION] = orientation.name }
    }

    suspend fun updateSyncInterval(seconds: Int) {
        context.dataStore.edit { it[Keys.SYNC_INTERVAL] = seconds.coerceAtLeast(60) }
    }

    suspend fun updateRetentionDays(days: Int) {
        context.dataStore.edit { it[Keys.RETENTION_DAYS] = days.coerceIn(7, 90) }
    }

    suspend fun updateKeepScreenOn(keepOn: Boolean, onlyWhileCharging: Boolean) {
        context.dataStore.edit {
            it[Keys.KEEP_SCREEN_ON] = keepOn
            it[Keys.KEEP_SCREEN_ON_CHARGING] = onlyWhileCharging
        }
    }

    suspend fun updatePixelShift(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PIXEL_SHIFT] = enabled }
    }

    suspend fun updateDimmingSchedule(
        enabled: Boolean,
        startHour: Int,
        startMin: Int,
        endHour: Int,
        endMin: Int,
        blackScreen: Boolean
    ) {
        context.dataStore.edit {
            it[Keys.DIMMING_ENABLED] = enabled
            it[Keys.DIMMING_START_HOUR] = startHour
            it[Keys.DIMMING_START_MINUTE] = startMin
            it[Keys.DIMMING_END_HOUR] = endHour
            it[Keys.DIMMING_END_MINUTE] = endMin
            it[Keys.DIMMING_BLACK_SCREEN] = blackScreen
        }
    }

    suspend fun updateAccessGesture(gesture: AccessGesture) {
        context.dataStore.edit { it[Keys.ACCESS_GESTURE] = gesture.name }
    }

    suspend fun updatePinEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PIN_ENABLED] = enabled }
    }

    suspend fun updateChartWindowHours(hours: Int) {
        context.dataStore.edit { it[Keys.CHART_WINDOW_HOURS] = hours }
    }

    suspend fun updateLluAdvanced(
        domain: String,
        region: String,
        product: String,
        version: String,
        autoAcceptTerms: Boolean
    ) {
        context.dataStore.edit {
            it[Keys.LLU_DOMAIN] = domain
            it[Keys.LLU_REGION] = region
            it[Keys.LLU_PRODUCT] = product
            it[Keys.LLU_VERSION] = version
            it[Keys.LLU_AUTO_ACCEPT_TERMS] = autoAcceptTerms
        }
    }

    suspend fun updateLluVersion(version: String) {
        context.dataStore.edit { it[Keys.LLU_VERSION] = version }
    }

    suspend fun updateLluRegion(region: String) {
        context.dataStore.edit { it[Keys.LLU_REGION] = region }
    }

    suspend fun setMedicalDisclaimerAccepted(accepted: Boolean) {
        context.dataStore.edit { it[Keys.MEDICAL_DISCLAIMER_ACCEPTED] = accepted }
    }

    suspend fun setClockSkew(detected: Boolean, offsetMs: Long) {
        context.dataStore.edit {
            it[Keys.CLOCK_SKEW_DETECTED] = detected
            it[Keys.LAST_SERVER_CLOCK_OFFSET] = offsetMs
        }
    }

    suspend fun setKioskHomeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KIOSK_HOME_ENABLED] = enabled }
    }

    suspend fun updateStatusBarNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STATUS_BAR_NOTIFICATION] = enabled }
    }

    suspend fun updateHideOnLockscreen(hide: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_ON_LOCKSCREEN] = hide }
    }

    suspend fun updateStartOnBoot(startOnBoot: Boolean) {
        context.dataStore.edit { it[Keys.START_ON_BOOT] = startOnBoot }
    }

    suspend fun updateKeepSyncing(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SYNCING] = enabled }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun updateAlarmsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALARMS_ENABLED] = enabled }
    }

    /** [name] is one of: urgent_low, low, high, very_high, obsolete, sound, full_screen. */
    suspend fun updateAlarmFlag(name: String, value: Boolean) {
        val key = when (name) {
            "urgent_low" -> Keys.ALARM_URGENT_LOW
            "low" -> Keys.ALARM_LOW
            "high" -> Keys.ALARM_HIGH
            "very_high" -> Keys.ALARM_VERY_HIGH
            "obsolete" -> Keys.ALARM_OBSOLETE
            "sound" -> Keys.ALARM_SOUND
            "full_screen" -> Keys.ALARM_FULL_SCREEN
            else -> return
        }
        context.dataStore.edit { it[key] = value }
    }

    suspend fun updateAlarmTimes(repeatMinutes: Int, snoozeMinutes: Int) {
        context.dataStore.edit {
            it[Keys.ALARM_REPEAT_MIN] = repeatMinutes
            it[Keys.ALARM_SNOOZE_MIN] = snoozeMinutes
        }
    }

    suspend fun updateAlarmSnoozeUntil(untilMs: Long) {
        context.dataStore.edit { it[Keys.ALARM_SNOOZE_UNTIL] = untilMs }
    }

    suspend fun updateLanServer(enabled: Boolean, token: String) {
        context.dataStore.edit {
            it[Keys.LAN_SERVER_ENABLED] = enabled
            it[Keys.LAN_SERVER_TOKEN] = token
        }
    }
}
