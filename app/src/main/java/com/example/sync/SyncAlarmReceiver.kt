package com.example.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.GlucoseFrameApplication
import com.example.core.datastore.needsForegroundService
import com.example.core.model.SyncResult
import com.example.data.source.SourceResult
import com.example.service.GlucoseForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Runs when the sync alarm fires: sets the next alarm first (so the chain never breaks), then reads glucose. */
class SyncAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SyncAlarm.ACTION) return
        val app = context.applicationContext as? GlucoseFrameApplication ?: return

        val pending = goAsync()
        val wakeLock = try {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GlucoseFrame:syncAlarm")
                .apply {
                    setReferenceCounted(false)
                    acquire(60_000L)
                }
        } catch (e: Exception) {
            null
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = app.preferencesRepository.userPreferencesFlow.first()
                val intervalMs = prefs.syncIntervalSeconds.coerceAtLeast(60) * 1000L

                // 1) Next alarm first: whatever happens below, the chain continues
                SyncAlarm.scheduleNext(context, intervalMs)

                // 2) Bring the background service back if Android stopped it
                if (prefs.needsForegroundService() && !GlucoseForegroundService.isRunning) {
                    GlucoseForegroundService.start(context)
                }

                // 3) Read glucose (when the screen app is open, its own loop already does it)
                if (!app.isUiVisible) {
                    val result = withTimeoutOrNull(45_000L) { app.syncEngine.syncOnce() }
                    if (result is SourceResult.Error) {
                        val retrySec = result.error.retryDelaySeconds
                        if (result.error.resultType == SyncResult.RATE_LIMIT && retrySec > 0) {
                            SyncAlarm.scheduleNext(context, retrySec * 1000L)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncAlarmReceiver", "Background sync failed", e)
                SyncAlarm.scheduleNext(context, 60_000L)
            } finally {
                try {
                    wakeLock?.release()
                } catch (e: Exception) {
                    // Ignore
                }
                pending.finish()
            }
        }
    }
}
