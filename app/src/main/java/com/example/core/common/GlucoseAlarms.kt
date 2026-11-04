package com.example.core.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.core.datastore.UserPreferences
import com.example.core.model.FreshnessState
import com.example.core.model.GlucoseSurfaceState
import com.example.core.model.GlucoseZone
import com.example.service.AlarmActionReceiver

/**
 * Optional glucose alarms (very low, low, high, very high, no data), like GlucoDataHandler's: a loud notification
 * that repeats while the situation lasts, can be snoozed, and may open full screen on the lock screen.
 * Off by default. They are a secondary help and never replace the official sensor or app alarms.
 */
object GlucoseAlarms {

    const val CHANNEL_SOUND = "channel_glucose_alarm_sound"
    const val CHANNEL_VIBRATE = "channel_glucose_alarm_vibrate"
    const val ALARM_NOTIFICATION_ID = 1003
    const val ACTION_SNOOZE = "com.example.action.ALARM_SNOOZE"

    enum class Type(val title: String) {
        URGENT_LOW("Glucosa MUY BAJA"),
        LOW("Glucosa baja"),
        HIGH("Glucosa alta"),
        VERY_HIGH("Glucosa MUY ALTA"),
        OBSOLETE("Sin datos de glucosa")
    }

    @Volatile private var activeType: Type? = null
    @Volatile private var lastAlarmMs: Long = 0L

    /** Which alarm (if any) the current situation calls for, honouring which alarm types the user enabled. */
    fun typeFor(state: GlucoseSurfaceState, prefs: UserPreferences): Type? {
        if (state.freshness == FreshnessState.OBSOLETA) {
            return if (prefs.alarmObsolete) Type.OBSOLETE else null
        }
        if (state.freshness != FreshnessState.ACTUAL && state.freshness != FreshnessState.RETRASADA) return null
        return when (state.zone) {
            GlucoseZone.URGENT_LOW -> when {
                prefs.alarmUrgentLow -> Type.URGENT_LOW
                prefs.alarmLow -> Type.LOW
                else -> null
            }
            GlucoseZone.LOW -> if (prefs.alarmLow) Type.LOW else null
            GlucoseZone.HIGH -> when {
                prefs.alarmHigh -> Type.HIGH
                else -> null
            }
            GlucoseZone.VERY_HIGH -> when {
                prefs.alarmVeryHigh -> Type.VERY_HIGH
                prefs.alarmHigh -> Type.HIGH
                else -> null
            }
            else -> null
        }
    }

    /** Called with every new state (about once a minute at least). */
    fun evaluate(context: Context, state: GlucoseSurfaceState, prefs: UserPreferences) {
        try {
            if (!prefs.alarmsEnabled || !prefs.notificationsEnabled) {
                clear(context)
                return
            }
            val type = typeFor(state, prefs)
            if (type == null) {
                clear(context)
                return
            }
            val nowMs = System.currentTimeMillis()
            if (nowMs < prefs.alarmSnoozeUntilMs) return

            val due = activeType != type || nowMs - lastAlarmMs >= prefs.alarmRepeatMinutes * 60_000L
            if (!due) return
            activeType = type
            lastAlarmMs = nowMs
            post(context, type, state, prefs)
        } catch (e: Exception) {
            Log.e("GlucoseAlarms", "Error evaluating alarms", e)
        }
    }

    /** Removes the alarm notification and forgets the state (the next problem alarms again immediately). */
    fun clear(context: Context) {
        if (activeType != null) {
            activeType = null
            try {
                NotificationManagerCompat.from(context).cancel(ALARM_NOTIFICATION_ID)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun postTest(context: Context, prefs: UserPreferences) {
        try {
            val sample = GlucoseSurfaceState.from(
                reading = null,
                thresholds = prefs.thresholds,
                unit = prefs.unit
            )
            post(context, Type.LOW, sample, prefs, testText = "Prueba de alarma: así sonará y se verá una alarma real.")
        } catch (e: Exception) {
            Log.e("GlucoseAlarms", "Error posting test alarm", e)
        }
    }

    private fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val sound = NotificationChannel(CHANNEL_SOUND, "Alarmas de glucosa (con sonido)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alarmas de glucosa baja, alta o sin datos, con sonido y vibración"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            setShowBadge(false)
        }
        val vibrate = NotificationChannel(CHANNEL_VIBRATE, "Alarmas de glucosa (solo vibración)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alarmas de glucosa solo con vibración"
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600)
            setShowBadge(false)
        }
        manager.createNotificationChannel(sound)
        manager.createNotificationChannel(vibrate)
    }

    private fun post(
        context: Context,
        type: Type,
        state: GlucoseSurfaceState,
        prefs: UserPreferences,
        testText: String? = null
    ) {
        createChannels(context)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val openIntent = PendingIntent.getActivity(
            context, 10,
            Intent(context, MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            flags
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            context, 11,
            Intent(context, AlarmActionReceiver::class.java).setAction(ACTION_SNOOZE),
            flags
        )

        val valueLine = if (state.valueText != "--") {
            "${state.valueText} ${state.unit.label} ${state.trendSymbol}" +
                (if (state.deltaText.isNotBlank()) "  (Δ ${state.deltaText})" else "")
        } else {
            "Sin lectura reciente"
        }

        val builder = NotificationCompat.Builder(context, if (prefs.alarmSound) CHANNEL_SOUND else CHANNEL_VIBRATE)
            .setSmallIcon(R.drawable.ic_notification_glucose)
            .setContentTitle("${type.title}: ${state.valueText}")
            .setContentText(testText ?: valueLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(testText ?: "$valueLine\n${state.zone.labelEs} · ${state.ageText}"))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .addAction(0, "Posponer ${prefs.alarmSnoozeMinutes} min", snoozeIntent)

        if (prefs.alarmFullScreen) {
            builder.setFullScreenIntent(openIntent, true)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Before Android 8 sound and vibration belong to the notification, not to a channel
            builder.setDefaults(if (prefs.alarmSound) NotificationCompat.DEFAULT_ALL else NotificationCompat.DEFAULT_VIBRATE)
        }

        try {
            NotificationManagerCompat.from(context).notify(ALARM_NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.w("GlucoseAlarms", "Notification permission not granted", e)
        }
    }
}
