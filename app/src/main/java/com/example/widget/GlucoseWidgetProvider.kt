package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.example.GlucoseFrameApplication
import com.example.core.common.GlucoseSurfaceUpdater
import com.example.core.model.GlucoseSurfaceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GlucoseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? GlucoseFrameApplication ?: return@launch
                val prefs = app.preferencesRepository.userPreferencesFlow.first()
                val reading = app.repository.getLatestReadingSync(prefs.activeSource.name)
                val state = GlucoseSurfaceState.from(
                    reading = reading,
                    thresholds = prefs.thresholds,
                    unit = prefs.unit,
                    nowMs = System.currentTimeMillis(),
                    previousReading = reading?.let {
                        app.repository.getPreviousReading(prefs.activeSource.name, it.measuredAtUtcMs)
                    }
                )

                for (id in appWidgetIds) {
                    val minWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        appWidgetManager.getAppWidgetOptions(id)?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
                    } else {
                        0
                    }
                    val views = GlucoseSurfaceUpdater.buildWidgetRemoteViews(context, id, minWidth, state)
                    appWidgetManager.updateAppWidget(id, views)
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? GlucoseFrameApplication ?: return@launch
                val prefs = app.preferencesRepository.userPreferencesFlow.first()
                val reading = app.repository.getLatestReadingSync(prefs.activeSource.name)
                val state = GlucoseSurfaceState.from(
                    reading = reading,
                    thresholds = prefs.thresholds,
                    unit = prefs.unit,
                    nowMs = System.currentTimeMillis(),
                    previousReading = reading?.let {
                        app.repository.getPreviousReading(prefs.activeSource.name, it.measuredAtUtcMs)
                    }
                )

                val minWidth = newOptions?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
                val views = GlucoseSurfaceUpdater.buildWidgetRemoteViews(context, appWidgetId, minWidth, state)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                // Ignore
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? GlucoseFrameApplication ?: return@launch
                val prefs = app.preferencesRepository.userPreferencesFlow.first()
                val reading = app.repository.getLatestReadingSync(prefs.activeSource.name)
                val state = GlucoseSurfaceState.from(
                    reading = reading,
                    thresholds = prefs.thresholds,
                    unit = prefs.unit,
                    nowMs = System.currentTimeMillis(),
                    previousReading = reading?.let {
                        app.repository.getPreviousReading(prefs.activeSource.name, it.measuredAtUtcMs)
                    }
                )
                GlucoseSurfaceUpdater.updateAllWidgets(context, state)
                GlucoseSurfaceUpdater.updateGraphWidgets(context)
            } catch (e: Exception) {
                // Ignore
            } finally {
                pendingResult.finish()
            }
        }
    }
}
