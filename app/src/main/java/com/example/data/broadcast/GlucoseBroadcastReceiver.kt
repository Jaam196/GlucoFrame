package com.example.data.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.GlucoseFrameApplication
import com.example.core.common.GlucoseUtils
import com.example.core.model.ActiveSourceType
import com.example.core.model.GlucoseReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receives glucose broadcasts from xDrip+, Juggluco, Diabox and Dexcom BYODA and stores them in Room.
 * Readings are only accepted while "Apps locales" is the selected source, and only if the value is plausible.
 */
class GlucoseBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in GlucoseBroadcastParser.ALL_ACTIONS) return

        val app = context.applicationContext as? GlucoseFrameApplication ?: return
        val extras = intent.extras
        val nowMs = System.currentTimeMillis()
        val reading = GlucoseBroadcastParser.parse(action, extras, nowMs)
        val keys = extras?.keySet()?.take(10)?.joinToString(", ") ?: "(sin datos)"

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = app.preferencesRepository.userPreferencesFlow.first()
                if (prefs.activeSource != ActiveSourceType.LOCAL_BROADCAST) {
                    LocalBroadcastState.record(
                        "Llegó un dato de ${GlucoseBroadcastParser.originName(action)}, pero la fuente activa no es «Apps locales»: se ignora.",
                        null
                    )
                    return@launch
                }
                if (reading == null) {
                    LocalBroadcastState.record(
                        "Llegó «$action» pero no se pudo leer la glucosa. Datos recibidos: $keys",
                        null
                    )
                    return@launch
                }

                val thresholds = prefs.thresholds
                app.repository.saveReadings(
                    listOf(
                        GlucoseReading(
                            sourceId = ActiveSourceType.LOCAL_BROADCAST.name,
                            measuredAtUtcMs = reading.timeMs,
                            valueMgDl = reading.valueMgDl,
                            trend = reading.trend,
                            isHigh = reading.valueMgDl > thresholds.highMgDl,
                            isLow = reading.valueMgDl < thresholds.lowMgDl,
                            receivedAtUtcMs = nowMs
                        )
                    )
                )
                LocalBroadcastState.record(
                    "Última lectura: ${GlucoseUtils.formatValue(reading.valueMgDl, prefs.unit)} ${prefs.unit.label} " +
                        "de ${reading.origin} a las ${GlucoseUtils.formatTime(reading.timeMs)}",
                    nowMs
                )
            } catch (e: Exception) {
                Log.e("GlucoseBroadcastReceiver", "Error saving broadcast reading", e)
            } finally {
                pending.finish()
            }
        }
    }
}
