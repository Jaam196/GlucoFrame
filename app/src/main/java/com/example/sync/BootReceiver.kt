package com.example.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.GlucoseFrameApplication
import com.example.core.datastore.needsForegroundService
import com.example.service.GlucoseForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // TODO_ANDROID_STUDIO: Comprobar el arranque automático y reprogramación de WorkManager tras reinicio en la tablet física.
            SyncWatchdogWorker.schedule(context)

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as? GlucoseFrameApplication
                    // Alarms are cleared by a reboot: start the reading chain again, whatever the other settings are
                    SyncAlarm.scheduleNext(context, 15_000L)
                    if (app != null) {
                        val prefs = app.preferencesRepository.userPreferencesFlow.first()
                        if (prefs.startOnBoot && prefs.needsForegroundService()) {
                            GlucoseForegroundService.start(context)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
