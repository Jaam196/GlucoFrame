package com.example.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Wake-up clock for background readings. Android's own alarm service wakes the phone even in Doze and even if
 * the app process was killed, runs a sync, and sets the next alarm, so readings keep coming without the app open.
 */
object SyncAlarm {

    private const val REQUEST_CODE = 4242
    const val ACTION = "com.example.action.SYNC_ALARM"
    private const val PREFS = "sync_alarm"
    private const val KEY_NEXT_AT = "next_at"

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SyncAlarmReceiver::class.java).setAction(ACTION)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    /** True when the system lets the app set alarms at exact times (Android 12+ asks the user). */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return try {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        } catch (e: Exception) {
            false
        }
    }

    /** Sets the next sync alarm [delayMs] from now (replacing any previous one). */
    fun scheduleNext(context: Context, delayMs: Long) {
        val appContext = context.applicationContext
        val triggerAt = System.currentTimeMillis() + delayMs.coerceAtLeast(1_000L)
        try {
            val manager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = pendingIntent(appContext)
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ->
                    manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                canScheduleExact(appContext) ->
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                else ->
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_NEXT_AT, triggerAt).apply()
        } catch (e: Exception) {
            Log.e("SyncAlarm", "Could not schedule the sync alarm", e)
        }
    }

    /** Re-arms the alarm chain if it is missing or overdue (for example after a reboot or an app update). */
    fun ensureScheduled(context: Context, delayMs: Long = 60_000L) {
        val nextAt = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_NEXT_AT, 0L)
        if (nextAt == 0L || System.currentTimeMillis() > nextAt + 30_000L) {
            scheduleNext(context, delayMs)
        }
    }

    fun cancel(context: Context) {
        try {
            val appContext = context.applicationContext
            (appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent(appContext))
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_NEXT_AT).apply()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
