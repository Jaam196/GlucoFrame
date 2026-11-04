package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.BackgroundMode
import com.example.core.model.BackgroundPreferences
import com.example.core.model.CustomFolderOrder
import com.example.core.model.GlucoseZone
import com.example.core.model.ZoneDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.backgroundDataStore: DataStore<Preferences> by preferencesDataStore(name = "background_prefs")

class BackgroundPreferencesRepository(private val context: Context) {

    private object Keys {
        val MODE = stringPreferencesKey("bg_mode")
        val PRESET_CATEGORY = stringPreferencesKey("bg_preset_category")
        val CUSTOM_FOLDER_URI = stringPreferencesKey("bg_custom_folder_uri")
        val CUSTOM_FOLDER_ORDER = stringPreferencesKey("bg_custom_folder_order")
        val CUSTOM_FOLDER_SELECTED_ALL = booleanPreferencesKey("bg_custom_folder_selected_all")
        val CUSTOM_FOLDER_SELECTED_IDS = stringSetPreferencesKey("bg_custom_folder_selected_ids")
        val FIXED_IMAGE_URI = stringPreferencesKey("bg_fixed_image_uri")
        val CHANGE_INTERVAL_SEC = intPreferencesKey("bg_change_interval_sec")
        val AUTO_CHANGE_ENABLED = booleanPreferencesKey("bg_auto_change_enabled")
        val CROSSFADE_DURATION_SEC = floatPreferencesKey("bg_crossfade_duration_sec")
        val DARK_LAYER_OPACITY = floatPreferencesKey("bg_dark_layer_opacity")
        val BLUR_RADIUS = floatPreferencesKey("bg_blur_radius")
        val SATURATION = floatPreferencesKey("bg_saturation")
        val BRIGHTNESS = floatPreferencesKey("bg_brightness")
        val CONTRAST = floatPreferencesKey("bg_contrast")
        val AUTO_READING_PROTECTION = booleanPreferencesKey("bg_auto_reading_protection")
        val COLOR_URGENT_LOW = stringPreferencesKey("bg_color_urgent_low")
        val COLOR_LOW = stringPreferencesKey("bg_color_low")
        val COLOR_NEAR_LOW = stringPreferencesKey("bg_color_near_low")
        val COLOR_ELEVATED = stringPreferencesKey("bg_color_elevated")
        val COLOR_IN_RANGE = stringPreferencesKey("bg_color_in_range")
        val COLOR_HIGH = stringPreferencesKey("bg_color_high")
        val COLOR_VERY_HIGH = stringPreferencesKey("bg_color_very_high")
        val KEN_BURNS = booleanPreferencesKey("bg_ken_burns")
        val SHOW_PHOTO_NAME = booleanPreferencesKey("bg_show_photo_name")
        val PHOTO_DURATIONS = stringPreferencesKey("bg_photo_durations")
    }

    val backgroundPreferencesFlow: Flow<BackgroundPreferences> = context.backgroundDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val modeStr = prefs[Keys.MODE] ?: BackgroundMode.PRESET_LIBRARY.name
            val mode = try {
                BackgroundMode.valueOf(modeStr)
            } catch (e: Exception) {
                BackgroundMode.PRESET_LIBRARY
            }

            val orderStr = prefs[Keys.CUSTOM_FOLDER_ORDER] ?: CustomFolderOrder.RANDOM.name
            val order = try {
                CustomFolderOrder.valueOf(orderStr)
            } catch (e: Exception) {
                CustomFolderOrder.RANDOM
            }

            BackgroundPreferences(
                mode = mode,
                presetCategory = prefs[Keys.PRESET_CATEGORY] ?: "todos",
                customFolderUri = prefs[Keys.CUSTOM_FOLDER_URI],
                customFolderOrder = order,
                customFolderSelectedAll = prefs[Keys.CUSTOM_FOLDER_SELECTED_ALL] ?: true,
                customFolderSelectedIds = prefs[Keys.CUSTOM_FOLDER_SELECTED_IDS] ?: emptySet(),
                fixedImageUri = prefs[Keys.FIXED_IMAGE_URI],
                changeIntervalSeconds = prefs[Keys.CHANGE_INTERVAL_SEC] ?: 180,
                autoChangeEnabled = prefs[Keys.AUTO_CHANGE_ENABLED] ?: false,
                crossfadeDurationSeconds = prefs[Keys.CROSSFADE_DURATION_SEC] ?: 1.0f,
                darkLayerOpacity = prefs[Keys.DARK_LAYER_OPACITY] ?: 0.40f,
                blurRadius = prefs[Keys.BLUR_RADIUS] ?: 0f,
                saturation = prefs[Keys.SATURATION] ?: 1.0f,
                brightness = prefs[Keys.BRIGHTNESS] ?: 1.0f,
                contrast = prefs[Keys.CONTRAST] ?: 1.0f,
                autoReadingProtection = prefs[Keys.AUTO_READING_PROTECTION] ?: true,
                colorUrgentLowHex = prefs[Keys.COLOR_URGENT_LOW] ?: ZoneDefaults.HEX.getValue(GlucoseZone.URGENT_LOW),
                colorLowHex = prefs[Keys.COLOR_LOW] ?: ZoneDefaults.HEX.getValue(GlucoseZone.LOW),
                colorNearLowHex = prefs[Keys.COLOR_NEAR_LOW] ?: ZoneDefaults.HEX.getValue(GlucoseZone.NEAR_LOW),
                colorInRangeHex = prefs[Keys.COLOR_IN_RANGE] ?: ZoneDefaults.HEX.getValue(GlucoseZone.IN_RANGE),
                colorElevatedHex = prefs[Keys.COLOR_ELEVATED] ?: ZoneDefaults.HEX.getValue(GlucoseZone.ELEVATED),
                colorHighHex = prefs[Keys.COLOR_HIGH] ?: ZoneDefaults.HEX.getValue(GlucoseZone.HIGH),
                colorVeryHighHex = prefs[Keys.COLOR_VERY_HIGH] ?: ZoneDefaults.HEX.getValue(GlucoseZone.VERY_HIGH),
                kenBurnsEnabled = prefs[Keys.KEN_BURNS] ?: false,
                showPhotoName = prefs[Keys.SHOW_PHOTO_NAME] ?: false,
                photoDurations = decodeDurations(prefs[Keys.PHOTO_DURATIONS])
            )
        }

    private fun decodeDurations(json: String?): Map<String, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(json)
            val map = HashMap<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val seconds = obj.optInt(key, 0)
                if (seconds > 0) map[key] = seconds
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun updateKenBurns(enabled: Boolean) {
        context.backgroundDataStore.edit { it[Keys.KEN_BURNS] = enabled }
    }

    suspend fun updateShowPhotoName(enabled: Boolean) {
        context.backgroundDataStore.edit { it[Keys.SHOW_PHOTO_NAME] = enabled }
    }

    /** Sets how long one picture stays on screen; null (or 0) goes back to the general interval. */
    suspend fun updatePhotoDuration(uriString: String, seconds: Int?) {
        context.backgroundDataStore.edit { prefs ->
            val current = decodeDurations(prefs[Keys.PHOTO_DURATIONS]).toMutableMap()
            if (seconds == null || seconds <= 0) current.remove(uriString) else current[uriString] = seconds
            val obj = org.json.JSONObject()
            for ((key, value) in current) obj.put(key, value)
            prefs[Keys.PHOTO_DURATIONS] = obj.toString()
        }
    }

    suspend fun updateMode(mode: BackgroundMode) {
        context.backgroundDataStore.edit { it[Keys.MODE] = mode.name }
    }

    suspend fun updatePresetCategory(category: String) {
        context.backgroundDataStore.edit { it[Keys.PRESET_CATEGORY] = category }
    }

    suspend fun updateCustomFolderUri(uri: String?) {
        context.backgroundDataStore.edit {
            if (uri != null) it[Keys.CUSTOM_FOLDER_URI] = uri else it.remove(Keys.CUSTOM_FOLDER_URI)
        }
    }

    suspend fun updateCustomFolderOrder(order: CustomFolderOrder) {
        context.backgroundDataStore.edit { it[Keys.CUSTOM_FOLDER_ORDER] = order.name }
    }

    suspend fun updateCustomFolderSelection(selectAll: Boolean, selectedIds: Set<String>) {
        context.backgroundDataStore.edit {
            it[Keys.CUSTOM_FOLDER_SELECTED_ALL] = selectAll
            it[Keys.CUSTOM_FOLDER_SELECTED_IDS] = selectedIds
        }
    }

    suspend fun updateFixedImageUri(uri: String?) {
        context.backgroundDataStore.edit {
            if (uri != null) it[Keys.FIXED_IMAGE_URI] = uri else it.remove(Keys.FIXED_IMAGE_URI)
        }
    }

    suspend fun updateChangeInterval(seconds: Int) {
        context.backgroundDataStore.edit { it[Keys.CHANGE_INTERVAL_SEC] = seconds }
    }

    suspend fun updateAutoChangeEnabled(enabled: Boolean) {
        context.backgroundDataStore.edit { it[Keys.AUTO_CHANGE_ENABLED] = enabled }
    }

    suspend fun updateCrossfadeDuration(seconds: Float) {
        context.backgroundDataStore.edit { it[Keys.CROSSFADE_DURATION_SEC] = seconds }
    }

    suspend fun updateDarkLayerOpacity(opacity: Float) {
        context.backgroundDataStore.edit { it[Keys.DARK_LAYER_OPACITY] = opacity }
    }

    suspend fun updateBlurRadius(radius: Float) {
        context.backgroundDataStore.edit { it[Keys.BLUR_RADIUS] = radius }
    }

    suspend fun updateSaturation(saturation: Float) {
        context.backgroundDataStore.edit { it[Keys.SATURATION] = saturation }
    }

    suspend fun updateBrightness(brightness: Float) {
        context.backgroundDataStore.edit { it[Keys.BRIGHTNESS] = brightness }
    }

    suspend fun updateContrast(contrast: Float) {
        context.backgroundDataStore.edit { it[Keys.CONTRAST] = contrast }
    }

    suspend fun updateAutoReadingProtection(enabled: Boolean) {
        context.backgroundDataStore.edit { it[Keys.AUTO_READING_PROTECTION] = enabled }
    }

    suspend fun updateZoneColor(zone: GlucoseZone, hexColor: String) {
        context.backgroundDataStore.edit {
            when (zone) {
                GlucoseZone.URGENT_LOW -> it[Keys.COLOR_URGENT_LOW] = hexColor
                GlucoseZone.LOW -> it[Keys.COLOR_LOW] = hexColor
                GlucoseZone.NEAR_LOW -> it[Keys.COLOR_NEAR_LOW] = hexColor
                GlucoseZone.IN_RANGE -> it[Keys.COLOR_IN_RANGE] = hexColor
                GlucoseZone.ELEVATED -> it[Keys.COLOR_ELEVATED] = hexColor
                GlucoseZone.HIGH -> it[Keys.COLOR_HIGH] = hexColor
                GlucoseZone.VERY_HIGH -> it[Keys.COLOR_VERY_HIGH] = hexColor
            }
        }
    }

    suspend fun restoreDefaultZoneColors() {
        context.backgroundDataStore.edit {
            it[Keys.COLOR_URGENT_LOW] = ZoneDefaults.HEX.getValue(GlucoseZone.URGENT_LOW)
            it[Keys.COLOR_LOW] = ZoneDefaults.HEX.getValue(GlucoseZone.LOW)
            it[Keys.COLOR_NEAR_LOW] = ZoneDefaults.HEX.getValue(GlucoseZone.NEAR_LOW)
            it[Keys.COLOR_IN_RANGE] = ZoneDefaults.HEX.getValue(GlucoseZone.IN_RANGE)
            it[Keys.COLOR_ELEVATED] = ZoneDefaults.HEX.getValue(GlucoseZone.ELEVATED)
            it[Keys.COLOR_HIGH] = ZoneDefaults.HEX.getValue(GlucoseZone.HIGH)
            it[Keys.COLOR_VERY_HIGH] = ZoneDefaults.HEX.getValue(GlucoseZone.VERY_HIGH)
        }
    }
}
