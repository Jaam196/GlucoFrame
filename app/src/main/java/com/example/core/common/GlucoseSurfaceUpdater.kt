package com.example.core.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import com.example.GlucoseFrameApplication
import com.example.MainActivity
import com.example.R
import com.example.core.designsystem.GlucoseColors
import com.example.core.designsystem.StyleColors
import com.example.core.model.DisplayStyle
import com.example.core.model.GlucoseReading
import com.example.core.model.ExtraStatusIcon
import com.example.core.model.NotificationCardStyle
import com.example.core.model.StatusIconMode
import com.example.core.model.TrendArrow
import com.example.core.model.ThresholdSettings
import com.example.widget.GlucoseGraphWidgetProvider
import kotlinx.coroutines.flow.first
import com.example.core.model.TextColorSource
import com.example.core.model.WidgetBackground
import com.example.core.model.WidgetCorner
import com.example.core.model.FreshnessState
import com.example.core.model.GlucoseSurfaceState
import com.example.core.model.GlucoseZone
import com.example.widget.GlucoseWidgetProvider

object GlucoseSurfaceUpdater {

    const val NOTIFICATION_ID = 1001
    const val EXTRA_NOTIFICATION_ID = 1002
    const val CHANNEL_SERVICE_MIN = "channel_glucose_service_min"
    const val CHANNEL_GLUCOSE_STATUS = "channel_glucose_status"

    private var lastIconText: String? = null
    private var cachedIconBitmap: Bitmap? = null

    /**
     * Creates notification channel with IMPORTANCE_LOW, no vibration, no sound (API 26+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_GLUCOSE_STATUS,
                "Glucosa en barra de estado",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra la lectura actual de glucosa y tendencia de forma fija"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds the persistent status bar notification
     */
    fun buildNotification(
        context: Context,
        state: GlucoseSurfaceState,
        hideOnLockscreen: Boolean
    ): Notification {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

        val visibility = if (hideOnLockscreen) {
            NotificationCompat.VISIBILITY_PRIVATE
        } else {
            NotificationCompat.VISIBILITY_PUBLIC
        }

        val style = (context.applicationContext as? GlucoseFrameApplication)?.displayStyle ?: DisplayStyle()

        val builder = NotificationCompat.Builder(context, CHANNEL_GLUCOSE_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(visibility)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(
                if (state.freshness == FreshnessState.ACTUAL || state.freshness == FreshnessState.RETRASADA) {
                    GlucoseColors.argbForZone(state.zone)
                } else {
                    GlucoseColors.ObsoleteGray.toArgb()
                }
            )
            .setContentTitle(
                if (state.deltaText.isNotBlank() && state.freshness == FreshnessState.ACTUAL) {
                    "${state.title}  (${state.deltaText})"
                } else {
                    state.title
                }
            )

        // Only add subtitle if not actual or if there is extra freshness context
        if (state.freshness != FreshnessState.ACTUAL && state.subtitle.isNotBlank()) {
            builder.setContentText(state.subtitle)
        }

        // Coloured card (number, arrow, delta, level name, age) like GlucoDataHandler's notification
        if (style.notificationCard != NotificationCardStyle.PLAIN) {
            try {
                val small = buildNotificationViews(context, R.layout.notification_glucose_small, state, style.notificationCard)
                val big = buildNotificationViews(context, R.layout.notification_glucose_big, state, style.notificationCard)
                builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                builder.setCustomContentView(small)
                builder.setCustomBigContentView(big)
            } catch (e: Exception) {
                // Fall back to the plain notification instead of crashing the app
                Log.e("GlucoseSurfaceUpdater", "Could not build the notification card", e)
            }
        }

        // Small icon: dynamic bitmap (API 23+) with white text on transparent, cached.
        // Android draws this icon in a single system colour, so colour lives in the card above.
        var iconSet = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val iconText = when (style.statusIconMode) {
                    StatusIconMode.VALUE -> state.valueText
                    StatusIconMode.DELTA -> state.deltaText.ifBlank { state.valueText }
                    StatusIconMode.TREND -> state.trendSymbol.ifBlank { state.valueText }
                }
                val isLiveForIcon = state.freshness == FreshnessState.ACTUAL || state.freshness == FreshnessState.RETRASADA
                // Some phones (Samsung One UI) show the status-bar icon in its own colour, like GlucoDataHandler's;
                // others tint it with the system colour, which is harmless.
                val iconColor = when {
                    !style.statusIconColored -> Color.WHITE
                    isLiveForIcon -> GlucoseColors.argbForZone(state.zone)
                    else -> GlucoseColors.ObsoleteGray.toArgb()
                }
                val iconBmp = getOrCreateStatusIcon(context, iconText.ifBlank { "--" }, iconColor)
                builder.setSmallIcon(IconCompat.createWithBitmap(iconBmp))
                iconSet = true
            } catch (e: Exception) {
                Log.e("GlucoseSurfaceUpdater", "Could not draw the status icon", e)
            }
        }
        if (!iconSet) {
            builder.setSmallIcon(R.drawable.ic_notification_glucose)
        }

        return builder.build()
    }

    /** Angle of the trend arrow: from the measured rate when known, otherwise from the sensor's trend. */
    private fun arrowAngle(state: GlucoseSurfaceState): Float? {
        val rate = state.ratePerMinute
        if (rate != null) return NotificationBitmaps.angleForRate(rate)
        return when (state.reading?.trend) {
            TrendArrow.RISING_FAST -> -90f
            TrendArrow.RISING -> -45f
            TrendArrow.FLAT -> 0f
            TrendArrow.FALLING -> 45f
            TrendArrow.FALLING_FAST -> 90f
            else -> null
        }
    }

    private fun isFastTrend(state: GlucoseSurfaceState): Boolean {
        val rate = state.ratePerMinute
        if (rate != null) return kotlin.math.abs(rate) >= 3f
        val trend = state.reading?.trend
        return trend == TrendArrow.RISING_FAST || trend == TrendArrow.FALLING_FAST
    }

    /**
     * RemoteViews of the notification card, like GlucoDataHandler: system background, number and arrow in the
     * colour of the current level, arrow rotated by the trend, and the change since the previous reading ("Δ +1").
     */
    private fun buildNotificationViews(
        context: Context,
        layoutRes: Int,
        state: GlucoseSurfaceState,
        card: NotificationCardStyle
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutRes)
        val isLive = state.freshness == FreshnessState.ACTUAL || state.freshness == FreshnessState.RETRASADA
        val levelColor = if (isLive) GlucoseColors.argbForZone(state.zone) else GlucoseColors.ObsoleteGray.toArgb()

        // Colours: SYSTEM keeps the system background (text styles adapt to it); LEVEL/DARK paint their own background
        var valueColor = levelColor
        var arrowColor = levelColor
        var labelColor: Int? = null
        when (card) {
            NotificationCardStyle.LEVEL -> {
                val onLevel = if (isLive && !GlucoseColors.prefersDarkText(state.zone)) Color.WHITE
                else if (isLive) Color.parseColor("#1A1A1A") else Color.WHITE
                views.setViewVisibility(R.id.notif_bg, View.VISIBLE)
                views.setInt(R.id.notif_bg, "setColorFilter", levelColor)
                valueColor = onLevel
                arrowColor = onLevel
                labelColor = onLevel
            }
            NotificationCardStyle.DARK -> {
                views.setViewVisibility(R.id.notif_bg, View.VISIBLE)
                views.setInt(R.id.notif_bg, "setColorFilter", Color.parseColor("#1E293B"))
                labelColor = Color.parseColor("#E2E8F0")
            }
            else -> Unit
        }

        // Big coloured number and rotating arrow, drawn as images (like GlucoDataHandler)
        val isBigLayout = layoutRes == R.layout.notification_glucose_big
        views.setImageViewBitmap(
            R.id.notif_value,
            NotificationBitmaps.valueBitmap(context, state.valueText, valueColor, if (isBigLayout) 46f else 34f)
        )
        views.setContentDescription(R.id.notif_value, "${state.valueText} ${state.unit.label}")

        // Arrow: rotates with the speed of change, double when very fast, struck through when the reading is old
        val angle = arrowAngle(state)
        if (angle == null) {
            views.setViewVisibility(R.id.notif_arrow, View.GONE)
        } else {
            views.setImageViewBitmap(
                R.id.notif_arrow,
                NotificationBitmaps.arrowBitmap(
                    context = context,
                    angleDegrees = angle,
                    color = arrowColor,
                    double = isFastTrend(state),
                    struck = !isLive,
                    sizeDp = if (isBigLayout) 44f else 34f
                )
            )
            views.setViewVisibility(R.id.notif_arrow, View.VISIBLE)
        }

        if (state.deltaText.isNotBlank() && isLive) {
            views.setTextViewText(R.id.notif_delta, "Δ ${state.deltaText}")
            views.setViewVisibility(R.id.notif_delta, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.notif_delta, View.GONE)
        }
        views.setTextViewText(R.id.notif_age, state.ageText)
        views.setTextViewText(R.id.notif_zone, if (isLive) state.zone.labelEs else "Sin datos actuales")
        views.setViewVisibility(R.id.notif_zone, View.VISIBLE)

        val label = labelColor
        if (label != null) {
            views.setTextColor(R.id.notif_delta, label)
            views.setTextColor(R.id.notif_age, label)
            views.setTextColor(R.id.notif_zone, label)
        }
        return views
    }

    /** Optional second, empty notification whose only purpose is one more icon in the status bar. */
    private fun updateExtraNotification(context: Context, state: GlucoseSurfaceState, hideOnLockscreen: Boolean) {
        val manager = NotificationManagerCompat.from(context)
        val style = (context.applicationContext as? GlucoseFrameApplication)?.displayStyle ?: DisplayStyle()
        if (style.extraStatusIcon == ExtraStatusIcon.NONE || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            manager.cancel(EXTRA_NOTIFICATION_ID)
            return
        }
        val text = when (style.extraStatusIcon) {
            ExtraStatusIcon.DELTA -> state.deltaText.ifBlank { "--" }
            else -> state.trendSymbol.ifBlank { "--" }
        }
        createNotificationChannel(context)
        val liveExtra = state.freshness == FreshnessState.ACTUAL || state.freshness == FreshnessState.RETRASADA
        val extraColor = when {
            !style.statusIconColored -> Color.WHITE
            liveExtra -> GlucoseColors.argbForZone(state.zone)
            else -> GlucoseColors.ObsoleteGray.toArgb()
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_GLUCOSE_STATUS)
            .setSmallIcon(IconCompat.createWithBitmap(createStatusIconBitmap(context, text, extraColor)))
            .setColor(extraColor)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(if (hideOnLockscreen) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle("GlucoseFrame")
            .build()
        manager.notify(EXTRA_NOTIFICATION_ID, notification)
    }

    /** The tiny silent notification Android requires while the background service runs and the card is off. */
    fun buildMinimalServiceNotification(context: Context, text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_SERVICE_MIN,
                "Servicio en segundo plano",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Aviso mínimo mientras la app comparte datos o vigila alarmas en segundo plano"
                setShowBadge(false)
                setSound(null, null)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(context, CHANNEL_SERVICE_MIN)
            .setSmallIcon(R.drawable.ic_notification_glucose)
            .setContentTitle("GlucoseFrame")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    /** Removes every notification of the app that can be removed (used by the master switch). */
    fun cancelAllNotifications(context: Context) {
        try {
            val manager = NotificationManagerCompat.from(context)
            manager.cancel(NOTIFICATION_ID)
            manager.cancel(EXTRA_NOTIFICATION_ID)
            manager.cancel(GlucoseAlarms.ALARM_NOTIFICATION_ID)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun cancelExtraNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(EXTRA_NOTIFICATION_ID)
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Updates notification if permission is granted
     */
    fun updateNotification(
        context: Context,
        state: GlucoseSurfaceState,
        hideOnLockscreen: Boolean
    ) {
        try {
            val notification = buildNotification(context, state, hideOnLockscreen)
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            updateExtraNotification(context, state, hideOnLockscreen)
        } catch (e: SecurityException) {
            Log.w("GlucoseSurfaceUpdater", "Notification permission not granted", e)
        } catch (e: Exception) {
            Log.e("GlucoseSurfaceUpdater", "Error updating notification", e)
        }
    }

    /**
     * Generates or retrieves cached dynamic small icon bitmap
     */
    @Synchronized
    fun getOrCreateStatusIcon(context: Context, text: String, color: Int = Color.WHITE): Bitmap {
        val key = "$text|$color"
        if (key == lastIconText && cachedIconBitmap != null && !cachedIconBitmap!!.isRecycled) {
            return cachedIconBitmap!!
        }
        val bmp = createStatusIconBitmap(context, text, color)
        cachedIconBitmap = bmp
        lastIconText = key
        return bmp
    }

    /**
     * Draws number in white over transparent background (24x24 dp)
     */
    fun createStatusIconBitmap(context: Context, text: String, textColor: Int = Color.WHITE): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (24 * density).toInt().coerceAtLeast(24)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER

            val textSizeDp = when {
                text.length <= 2 -> 15f
                text.length == 3 -> 12f
                text.length == 4 -> 9f
                else -> 8f
            }
            textSize = textSizeDp * density
        }

        val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(text, size / 2f, yPos, paint)
        return bitmap
    }

    /**
     * Builds RemoteViews for widget (compact if < 180dp, large otherwise)
     */
    fun buildWidgetRemoteViews(
        context: Context,
        appWidgetId: Int,
        minWidthDp: Int,
        state: GlucoseSurfaceState
    ): RemoteViews {
        val isCompact = minWidthDp < 180
        val layoutId = if (isCompact) R.layout.widget_glucose_compact else R.layout.widget_glucose_large
        val views = RemoteViews(context.packageName, layoutId)

        val style = (context.applicationContext as? GlucoseFrameApplication)?.displayStyle ?: DisplayStyle()

        val palette = computeWidgetPalette(style, state)
        views.setImageViewResource(R.id.widget_bg, palette.cornerRes)
        views.setInt(R.id.widget_bg, "setColorFilter", palette.bgColor)
        views.setInt(R.id.widget_bg, "setImageAlpha", palette.bgAlpha)
        val valueColor = palette.valueColor
        val labelColor = palette.labelColor

        // --- Sizes ---
        val scale = style.widgetSizeScale.coerceIn(0.5f, 2.5f)
        views.setTextViewTextSize(R.id.widget_value, TypedValue.COMPLEX_UNIT_SP, (if (isCompact) 28f else 32f) * scale)
        views.setTextViewTextSize(R.id.widget_trend, TypedValue.COMPLEX_UNIT_SP, (if (isCompact) 24f else 28f) * scale)

        views.setTextViewText(R.id.widget_value, state.valueText)
        views.setTextViewText(R.id.widget_trend, state.trendSymbol)
        views.setTextColor(R.id.widget_value, valueColor)
        views.setTextColor(R.id.widget_trend, valueColor)
        views.setViewVisibility(R.id.widget_trend, if (style.widgetShowTrend) View.VISIBLE else View.GONE)

        // Change since the previous reading ("+4" / "-7")
        views.setTextViewText(R.id.widget_delta, state.deltaText)
        views.setTextColor(R.id.widget_delta, labelColor)
        views.setTextViewTextSize(R.id.widget_delta, TypedValue.COMPLEX_UNIT_SP, (if (isCompact) 14f else 16f) * scale)
        views.setViewVisibility(
            R.id.widget_delta,
            if (style.widgetShowDelta && state.deltaText.isNotBlank()) View.VISIBLE else View.GONE
        )

        if (!isCompact) {
            views.setTextViewText(R.id.widget_unit, state.unit.label)
            views.setTextViewText(R.id.widget_zone, state.zone.labelEs)
            views.setTextViewText(R.id.widget_age, state.ageText)
            views.setTextColor(R.id.widget_unit, labelColor)
            views.setTextColor(R.id.widget_zone, labelColor)
            views.setTextColor(R.id.widget_age, labelColor)
            views.setTextViewTextSize(R.id.widget_unit, TypedValue.COMPLEX_UNIT_SP, 14f * scale)
            views.setTextViewTextSize(R.id.widget_zone, TypedValue.COMPLEX_UNIT_SP, 13f * scale)
            views.setTextViewTextSize(R.id.widget_age, TypedValue.COMPLEX_UNIT_SP, 12f * scale)
            views.setViewVisibility(R.id.widget_unit, if (style.widgetShowUnit) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_zone, if (style.widgetShowZone) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_age, if (style.widgetShowAge) View.VISIBLE else View.GONE)
        }

        // Tap opens MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, flags)
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        return views
    }

    private class WidgetPalette(
        val bgColor: Int,
        val bgAlpha: Int,
        val cornerRes: Int,
        val valueColor: Int,
        val labelColor: Int
    )

    /** Background, corner shape and text colours of a widget, from the user's style and the current level. */
    private fun computeWidgetPalette(style: DisplayStyle, state: GlucoseSurfaceState): WidgetPalette {
        val isLive = state.freshness == FreshnessState.ACTUAL || state.freshness == FreshnessState.RETRASADA
        val zoneBgColor = if (isLive) GlucoseColors.argbForZone(state.zone) else GlucoseColors.ObsoleteGray.toArgb()

        val bgColor: Int = when (style.widgetBackground) {
            WidgetBackground.ZONE_COLOR -> zoneBgColor
            WidgetBackground.CUSTOM_COLOR -> StyleColors.parseArgb(style.widgetCustomBgHex, zoneBgColor)
            WidgetBackground.DARK -> Color.parseColor("#111827")
            WidgetBackground.TRANSPARENT -> Color.TRANSPARENT
        }
        val bgAlpha = if (style.widgetBackground == WidgetBackground.TRANSPARENT) {
            0
        } else {
            (style.widgetOpacity.coerceIn(0.05f, 1f) * 255f).toInt()
        }
        val cornerRes = when (style.widgetCorner) {
            WidgetCorner.SQUARE -> R.drawable.bg_widget_shape_square
            WidgetCorner.SMALL -> R.drawable.bg_widget_shape_small
            WidgetCorner.MEDIUM -> R.drawable.bg_widget_shape_medium
            WidgetCorner.LARGE -> R.drawable.bg_widget_shape_large
            WidgetCorner.PILL -> R.drawable.bg_widget_shape_pill
        }

        val onColoredBg = when {
            !isLive -> Color.WHITE
            GlucoseColors.prefersDarkText(state.zone) -> Color.parseColor("#1A1A1A")
            else -> Color.WHITE
        }
        val textColors: Pair<Int, Int> = if (style.widgetTextSource == TextColorSource.CUSTOM) {
            val custom = StyleColors.parseArgb(style.widgetTextHex, Color.WHITE)
            Pair(custom, custom)
        } else if (style.widgetBackground == WidgetBackground.ZONE_COLOR) {
            Pair(onColoredBg, onColoredBg)
        } else if (style.widgetBackground == WidgetBackground.CUSTOM_COLOR) {
            val lightBackground = ColorUtils.calculateLuminance(bgColor) > 0.5
            val readable = if (lightBackground) Color.parseColor("#1A1A1A") else Color.WHITE
            Pair(readable, readable)
        } else {
            // Dark / transparent: the number keeps the colour of the glucose level, labels stay light
            Pair(zoneBgColor, Color.parseColor("#E2E8F0"))
        }
        return WidgetPalette(bgColor, bgAlpha, cornerRes, textColors.first, textColors.second)
    }

    /**
     * Builds the "glucose + delta + graph" widget: big number in the colour of the current level, trend arrow,
     * change since the previous reading, a colour bar for the level and the recent graph.
     */
    fun buildGraphWidgetRemoteViews(
        context: Context,
        appWidgetId: Int,
        state: GlucoseSurfaceState,
        readings: List<GlucoseReading>,
        thresholds: ThresholdSettings
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_glucose_graph)
        val style = (context.applicationContext as? GlucoseFrameApplication)?.displayStyle ?: DisplayStyle()
        val palette = computeWidgetPalette(style, state)
        val isLive = state.freshness == FreshnessState.ACTUAL || state.freshness == FreshnessState.RETRASADA
        val scale = style.widgetSizeScale.coerceIn(0.5f, 2.5f)

        views.setImageViewResource(R.id.widget_bg, palette.cornerRes)
        views.setInt(R.id.widget_bg, "setColorFilter", palette.bgColor)
        views.setInt(R.id.widget_bg, "setImageAlpha", palette.bgAlpha)

        // Level indicator bar on the left edge
        val levelColor = if (isLive) GlucoseColors.argbForZone(state.zone) else GlucoseColors.ObsoleteGray.toArgb()
        views.setInt(R.id.widget_level_bar, "setBackgroundColor", levelColor)

        views.setTextViewText(R.id.widget_value, state.valueText)
        views.setTextColor(R.id.widget_value, palette.valueColor)
        views.setTextViewTextSize(R.id.widget_value, TypedValue.COMPLEX_UNIT_SP, 34f * scale)

        views.setTextViewText(R.id.widget_trend, state.trendSymbol)
        views.setTextColor(R.id.widget_trend, palette.valueColor)
        views.setTextViewTextSize(R.id.widget_trend, TypedValue.COMPLEX_UNIT_SP, 28f * scale)
        views.setViewVisibility(R.id.widget_trend, if (style.widgetShowTrend) View.VISIBLE else View.GONE)

        views.setTextViewText(R.id.widget_delta, state.deltaText)
        views.setTextColor(R.id.widget_delta, palette.labelColor)
        views.setTextViewTextSize(R.id.widget_delta, TypedValue.COMPLEX_UNIT_SP, 18f * scale)
        views.setViewVisibility(
            R.id.widget_delta,
            if (style.widgetShowDelta && state.deltaText.isNotBlank()) View.VISIBLE else View.GONE
        )

        views.setTextViewText(R.id.widget_age, state.ageText)
        views.setTextColor(R.id.widget_age, palette.labelColor)
        views.setTextViewTextSize(R.id.widget_age, TypedValue.COMPLEX_UNIT_SP, 12f * scale)
        views.setViewVisibility(R.id.widget_age, if (style.widgetShowAge) View.VISIBLE else View.GONE)

        val chart = WidgetChartRenderer.render(
            readings = readings,
            thresholds = thresholds,
            windowHours = style.widgetGraphHours,
            widthPx = 640,
            heightPx = 240,
            maxMgDl = style.chartMaxMgDl
        )
        views.setImageViewBitmap(R.id.widget_chart, chart)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        views.setOnClickPendingIntent(
            R.id.widget_container,
            PendingIntent.getActivity(context, appWidgetId, intent, pendingFlags)
        )
        return views
    }

    /** Reads the latest data and redraws every "glucose + graph" widget on the home screen. */
    suspend fun updateGraphWidgets(context: Context) {
        try {
            val app = context.applicationContext as? GlucoseFrameApplication ?: return
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GlucoseGraphWidgetProvider::class.java))
            if (ids == null || ids.isEmpty()) return

            val prefs = app.preferencesRepository.userPreferencesFlow.first()
            val sourceId = prefs.activeSource.name
            val nowMs = System.currentTimeMillis()
            val latest = app.repository.getLatestReadingSync(sourceId)
            val previous = latest?.let { app.repository.getPreviousReading(sourceId, it.measuredAtUtcMs) }
            val state = GlucoseSurfaceState.from(latest, prefs.thresholds, prefs.unit, nowMs, previous)
            val windowMs = app.displayStyle.widgetGraphHours.coerceIn(1, 24) * 3_600_000L
            val readings = app.repository.getReadingsBetweenSync(sourceId, nowMs - windowMs, nowMs)

            for (id in ids) {
                manager.updateAppWidget(
                    id,
                    buildGraphWidgetRemoteViews(context, id, state, readings, prefs.thresholds)
                )
            }
        } catch (e: Exception) {
            Log.e("GlucoseSurfaceUpdater", "Error updating graph widgets", e)
        }
    }

    /**
     * Updates all home screen widgets
     */
    fun updateAllWidgets(context: Context, state: GlucoseSurfaceState) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, GlucoseWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            if (ids == null || ids.isEmpty()) return

            for (id in ids) {
                val minWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    appWidgetManager.getAppWidgetOptions(id)?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
                } else {
                    0
                }
                val views = buildWidgetRemoteViews(context, id, minWidth, state)
                appWidgetManager.updateAppWidget(id, views)
            }
        } catch (e: Exception) {
            Log.e("GlucoseSurfaceUpdater", "Error updating widgets", e)
        }
    }
}
