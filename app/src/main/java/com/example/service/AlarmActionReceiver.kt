package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.GlucoseFrameApplication
import com.example.core.common.GlucoseAlarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Handles the "Posponer" button of an alarm notification. */
class AlarmActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GlucoseAlarms.ACTION_SNOOZE) return
        val app = context.applicationContext as? GlucoseFrameApplication ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = app.preferencesRepository.userPreferencesFlow.first()
                app.preferencesRepository.updateAlarmSnoozeUntil(
                    System.currentTimeMillis() + prefs.alarmSnoozeMinutes * 60_000L
                )
                NotificationManagerCompat.from(context).cancel(GlucoseAlarms.ALARM_NOTIFICATION_ID)
            } catch (e: Exception) {
                // Ignore
            } finally {
                pending.finish()
            }
        }
    }
}
