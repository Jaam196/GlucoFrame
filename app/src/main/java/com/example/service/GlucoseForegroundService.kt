package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.GlucoseFrameApplication
import com.example.core.common.GlucoseSurfaceUpdater
import com.example.core.model.GlucoseSurfaceState
import com.example.core.model.SyncResult
import com.example.data.source.SourceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class GlucoseForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as GlucoseFrameApplication

        // Android kills the app if a service started with startForegroundService() does not call
        // startForeground() quickly, so a simple notification is shown first if the fancy one fails.
        val notification = try {
            val prefs = runBlocking { app.preferencesRepository.userPreferencesFlow.first() }
            if (prefs.notificationsEnabled && prefs.statusBarNotificationEnabled) {
                GlucoseSurfaceUpdater.buildNotification(this, app.currentSurfaceState, prefs.hideOnLockscreen)
            } else {
                // Notifications are off (master switch or setting) but the service still runs for the local server / alarms
                GlucoseSurfaceUpdater.buildMinimalServiceNotification(this, "Funcionando en segundo plano")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not build the notification, using a simple one", e)
            buildFallbackNotification()
        }

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, GlucoseSurfaceUpdater.NOTIFICATION_ID, notification, fgsType)
        } catch (e: Exception) {
            Log.e(TAG, "Error in ServiceCompat.startForeground", e)
            try {
                ServiceCompat.startForeground(this, GlucoseSurfaceUpdater.NOTIFICATION_ID, buildFallbackNotification(), fgsType)
            } catch (e2: Exception) {
                Log.e(TAG, "Foreground start failed twice, stopping the service", e2)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // Background readings are driven by the wake-up alarm (it works even in Doze and after a kill)
        com.example.sync.SyncAlarm.ensureScheduled(this, 5_000L)

        return START_STICKY
    }

    private fun buildFallbackNotification(): android.app.Notification {
        GlucoseSurfaceUpdater.createNotificationChannel(this)
        return androidx.core.app.NotificationCompat.Builder(this, GlucoseSurfaceUpdater.CHANNEL_GLUCOSE_STATUS)
            .setSmallIcon(com.example.R.drawable.ic_notification_glucose)
            .setContentTitle("GlucoseFrame")
            .setContentText("Monitorizando la glucosa")
            .setOngoing(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        GlucoseSurfaceUpdater.cancelExtraNotification(this)
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "GlucoseFGS"

        @Volatile
        var isRunning: Boolean = false

        fun start(context: Context) {
            val intent = Intent(context, GlucoseForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Catches ForegroundServiceStartNotAllowedException on Android 12+ (API 31+)
                Log.e(TAG, "Could not start GlucoseForegroundService", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, GlucoseForegroundService::class.java)
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not stop GlucoseForegroundService", e)
            }
        }
    }
}
