package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.ChartStyle
import com.example.core.model.ClockPosition
import com.example.core.model.ColorSource
import com.example.core.model.DisplayStyle
import com.example.core.model.NightNumberMode
import com.example.core.model.NumberEffect
import com.example.core.model.NumberFont
import com.example.core.model.TextColorSource
import com.example.core.model.WidgetBackground
import com.example.core.model.WidgetCorner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.IOException

private val Context.displayStyleDataStore: DataStore<Preferences> by preferencesDataStore(name = "display_style_prefs")

/** Saves the whole [DisplayStyle] as one JSON text, so new options never need a database migration. */
class DisplayStyleRepository(private val context: Context) {

    private val styleKey = stringPreferencesKey("display_style_json")

    val styleFlow: Flow<DisplayStyle> = context.displayStyleDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs -> DisplayStyleCodec.decode(prefs[styleKey]) }
        .distinctUntilChanged()

    suspend fun update(transform: (DisplayStyle) -> DisplayStyle) {
        context.displayStyleDataStore.edit { prefs ->
            val current = DisplayStyleCodec.decode(prefs[styleKey])
            prefs[styleKey] = DisplayStyleCodec.encode(transform(current))
        }
    }

    suspend fun resetAll() {
        context.displayStyleDataStore.edit { prefs ->
            prefs[styleKey] = DisplayStyleCodec.encode(DisplayStyle())
        }
    }
}

object DisplayStyleCodec {

    private inline fun <reified E : Enum<E>> JSONObject.enumOr(key: String, default: E): E {
        return try {
            enumValueOf<E>(optString(key, default.name))
        } catch (e: Exception) {
            default
        }
    }

    private fun JSONObject.floatOr(key: String, default: Float): Float =
        optDouble(key, default.toDouble()).toFloat()

    fun encode(s: DisplayStyle): String {
        val o = JSONObject()
        o.put("numberFont", s.numberFont.name)
        o.put("numberSizeScale", s.numberSizeScale.toDouble())
        o.put("numberEffect", s.numberEffect.name)
        o.put("numberColorSource", s.numberColorSource.name)
        o.put("numberCustomHex", s.numberCustomHex)
        o.put("showZoneBadge", s.showZoneBadge)
        o.put("showTrendArrow", s.showTrendArrow)
        o.put("showUnit", s.showUnit)
        o.put("showInfoRow", s.showInfoRow)
        o.put("showDelta", s.showDelta)
        o.put("infoTextScale", s.infoTextScale.toDouble())
        o.put("showPanel", s.showPanel)
        o.put("panelOpacity", s.panelOpacity.toDouble())

        o.put("nightNumberMode", s.nightNumberMode.name)
        o.put("nightDimLevel", s.nightDimLevel.toDouble())
        o.put("nightCustomHex", s.nightCustomHex)

        o.put("chartVisible", s.chartVisible)
        o.put("chartStyle", s.chartStyle.name)
        o.put("chartColorSource", s.chartColorSource.name)
        o.put("chartCustomHex", s.chartCustomHex)
        o.put("chartLineWidthDp", s.chartLineWidthDp.toDouble())
        o.put("chartPointRadiusDp", s.chartPointRadiusDp.toDouble())
        o.put("chartShowTargetBand", s.chartShowTargetBand)
        o.put("chartBandOpacity", s.chartBandOpacity.toDouble())
        o.put("chartShowGrid", s.chartShowGrid)
        o.put("chartShowAxisLabels", s.chartShowAxisLabels)
        o.put("chartShowRefLine", s.chartShowRefLine)
        o.put("chartMaxMgDl", s.chartMaxMgDl)
        o.put("chartPanelOpacity", s.chartPanelOpacity.toDouble())
        o.put("chartTextScale", s.chartTextScale.toDouble())
        o.put("showWindowChips", s.showWindowChips)

        o.put("clockVisible", s.clockVisible)
        o.put("clockPosition", s.clockPosition.name)
        o.put("clock24h", s.clock24h)
        o.put("clockShowSeconds", s.clockShowSeconds)
        o.put("clockShowDate", s.clockShowDate)
        o.put("clockSizeSp", s.clockSizeSp.toDouble())
        o.put("clockFont", s.clockFont.name)
        o.put("clockColorSource", s.clockColorSource.name)
        o.put("clockCustomHex", s.clockCustomHex)
        o.put("clockOpacity", s.clockOpacity.toDouble())
        o.put("clockPanel", s.clockPanel)

        o.put("showConnectionBadge", s.showConnectionBadge)
        o.put("showNotificationButton", s.showNotificationButton)
        o.put("showTreatments", s.showTreatments)
        o.put("showSensorDays", s.showSensorDays)

        o.put("widgetBackground", s.widgetBackground.name)
        o.put("widgetCustomBgHex", s.widgetCustomBgHex)
        o.put("widgetOpacity", s.widgetOpacity.toDouble())
        o.put("widgetCorner", s.widgetCorner.name)
        o.put("widgetTextSource", s.widgetTextSource.name)
        o.put("widgetTextHex", s.widgetTextHex)
        o.put("widgetSizeScale", s.widgetSizeScale.toDouble())
        o.put("widgetShowTrend", s.widgetShowTrend)
        o.put("widgetShowUnit", s.widgetShowUnit)
        o.put("widgetShowZone", s.widgetShowZone)
        o.put("widgetShowAge", s.widgetShowAge)
        o.put("widgetShowDelta", s.widgetShowDelta)
        o.put("widgetGraphHours", s.widgetGraphHours)
        o.put("notificationCard", s.notificationCard.name)
        o.put("extraStatusIcon", s.extraStatusIcon.name)
        o.put("statusIconMode", s.statusIconMode.name)
        o.put("statusIconColored", s.statusIconColored)
        return o.toString()
    }

    /** Unknown or missing values fall back to the defaults, so old saved data never breaks the app. */
    fun decode(json: String?): DisplayStyle {
        val d = DisplayStyle()
        if (json.isNullOrBlank()) return d
        return try {
            val o = JSONObject(json)
            DisplayStyle(
                numberFont = o.enumOr("numberFont", d.numberFont),
                numberSizeScale = o.floatOr("numberSizeScale", d.numberSizeScale),
                numberEffect = o.enumOr("numberEffect", d.numberEffect),
                numberColorSource = o.enumOr("numberColorSource", d.numberColorSource),
                numberCustomHex = o.optString("numberCustomHex", d.numberCustomHex),
                showZoneBadge = o.optBoolean("showZoneBadge", d.showZoneBadge),
                showTrendArrow = o.optBoolean("showTrendArrow", d.showTrendArrow),
                showUnit = o.optBoolean("showUnit", d.showUnit),
                showInfoRow = o.optBoolean("showInfoRow", d.showInfoRow),
                showDelta = o.optBoolean("showDelta", d.showDelta),
                infoTextScale = o.floatOr("infoTextScale", d.infoTextScale),
                showPanel = o.optBoolean("showPanel", d.showPanel),
                panelOpacity = o.floatOr("panelOpacity", d.panelOpacity),

                nightNumberMode = o.enumOr("nightNumberMode", d.nightNumberMode),
                nightDimLevel = o.floatOr("nightDimLevel", d.nightDimLevel),
                nightCustomHex = o.optString("nightCustomHex", d.nightCustomHex),

                chartVisible = o.optBoolean("chartVisible", d.chartVisible),
                chartStyle = o.enumOr("chartStyle", d.chartStyle),
                chartColorSource = o.enumOr("chartColorSource", d.chartColorSource),
                chartCustomHex = o.optString("chartCustomHex", d.chartCustomHex),
                chartLineWidthDp = o.floatOr("chartLineWidthDp", d.chartLineWidthDp),
                chartPointRadiusDp = o.floatOr("chartPointRadiusDp", d.chartPointRadiusDp),
                chartShowTargetBand = o.optBoolean("chartShowTargetBand", d.chartShowTargetBand),
                chartBandOpacity = o.floatOr("chartBandOpacity", d.chartBandOpacity),
                chartShowGrid = o.optBoolean("chartShowGrid", d.chartShowGrid),
                chartShowAxisLabels = o.optBoolean("chartShowAxisLabels", d.chartShowAxisLabels),
                chartShowRefLine = o.optBoolean("chartShowRefLine", d.chartShowRefLine),
                chartMaxMgDl = o.optInt("chartMaxMgDl", d.chartMaxMgDl),
                chartPanelOpacity = o.floatOr("chartPanelOpacity", d.chartPanelOpacity),
                chartTextScale = o.floatOr("chartTextScale", d.chartTextScale),
                showWindowChips = o.optBoolean("showWindowChips", d.showWindowChips),

                clockVisible = o.optBoolean("clockVisible", d.clockVisible),
                clockPosition = o.enumOr("clockPosition", d.clockPosition),
                clock24h = o.optBoolean("clock24h", d.clock24h),
                clockShowSeconds = o.optBoolean("clockShowSeconds", d.clockShowSeconds),
                clockShowDate = o.optBoolean("clockShowDate", d.clockShowDate),
                clockSizeSp = o.floatOr("clockSizeSp", d.clockSizeSp),
                clockFont = o.enumOr("clockFont", d.clockFont),
                clockColorSource = o.enumOr("clockColorSource", d.clockColorSource),
                clockCustomHex = o.optString("clockCustomHex", d.clockCustomHex),
                clockOpacity = o.floatOr("clockOpacity", d.clockOpacity),
                clockPanel = o.optBoolean("clockPanel", d.clockPanel),

                showConnectionBadge = o.optBoolean("showConnectionBadge", d.showConnectionBadge),
                showNotificationButton = o.optBoolean("showNotificationButton", d.showNotificationButton),
                showTreatments = o.optBoolean("showTreatments", d.showTreatments),
                showSensorDays = o.optBoolean("showSensorDays", d.showSensorDays),

                widgetBackground = o.enumOr("widgetBackground", d.widgetBackground),
                widgetCustomBgHex = o.optString("widgetCustomBgHex", d.widgetCustomBgHex),
                widgetOpacity = o.floatOr("widgetOpacity", d.widgetOpacity),
                widgetCorner = o.enumOr("widgetCorner", d.widgetCorner),
                widgetTextSource = o.enumOr("widgetTextSource", d.widgetTextSource),
                widgetTextHex = o.optString("widgetTextHex", d.widgetTextHex),
                widgetSizeScale = o.floatOr("widgetSizeScale", d.widgetSizeScale),
                widgetShowTrend = o.optBoolean("widgetShowTrend", d.widgetShowTrend),
                widgetShowUnit = o.optBoolean("widgetShowUnit", d.widgetShowUnit),
                widgetShowZone = o.optBoolean("widgetShowZone", d.widgetShowZone),
                widgetShowAge = o.optBoolean("widgetShowAge", d.widgetShowAge),
                widgetShowDelta = o.optBoolean("widgetShowDelta", d.widgetShowDelta),
                widgetGraphHours = o.optInt("widgetGraphHours", d.widgetGraphHours),
                notificationCard = o.enumOr("notificationCard", d.notificationCard),
                extraStatusIcon = o.enumOr("extraStatusIcon", d.extraStatusIcon),
                statusIconMode = o.enumOr("statusIconMode", d.statusIconMode),
                statusIconColored = o.optBoolean("statusIconColored", d.statusIconColored)
            )
        } catch (e: Exception) {
            d
        }
    }
}
