package com.example.core.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/**
 * Draws the big coloured glucose number and the rotating trend arrow of the notification as images, the way
 * GlucoDataHandler does: the colour, size and arrow angle are exact on every device and system font setting.
 */
object NotificationBitmaps {

    /** The glucose number in [color]. [textSizeDp] is the font size in dp. */
    fun valueBitmap(context: Context, text: String, color: Int, textSizeDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = textSizeDp * density
        }
        val width = (paint.measureText(text) + 4 * density).toInt().coerceAtLeast(1)
        val metrics = paint.fontMetrics
        val height = (metrics.descent - metrics.ascent + 4 * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 2 * density, 2 * density - metrics.ascent, paint)
        return bitmap
    }

    /**
     * Trend arrow pointing right, rotated by [angleDegrees] (clockwise: negative = up, positive = down).
     * [double] draws two arrows (very fast change); [struck] draws a line through it (old reading).
     */
    fun arrowBitmap(
        context: Context,
        angleDegrees: Float,
        color: Int,
        double: Boolean,
        struck: Boolean,
        sizeDp: Float
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (sizeDp * density).toInt().coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }

        val s = size.toFloat()
        fun arrowPath(offset: Float): Path {
            // Arrow pointing right, centred in the square, shifted along its own direction by [offset]
            val path = Path()
            val x0 = s * 0.12f + offset
            val x1 = s * 0.58f + offset
            val x2 = s * 0.90f + offset
            val half = s * 0.07f
            val head = s * 0.22f
            path.moveTo(x0, s / 2 - half)
            path.lineTo(x1, s / 2 - half)
            path.lineTo(x1, s / 2 - head)
            path.lineTo(x2, s / 2)
            path.lineTo(x1, s / 2 + head)
            path.lineTo(x1, s / 2 + half)
            path.lineTo(x0, s / 2 + half)
            path.close()
            return path
        }

        canvas.save()
        canvas.rotate(angleDegrees, s / 2, s / 2)
        if (double) {
            // Two arrows, each a bit smaller, side by side along the direction of travel
            canvas.scale(0.78f, 0.78f, s / 2, s / 2)
            canvas.drawPath(arrowPath(-s * 0.17f), paint)
            canvas.drawPath(arrowPath(s * 0.10f), paint)
        } else {
            canvas.drawPath(arrowPath(0f), paint)
        }
        canvas.restore()

        if (struck) {
            val strike = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                strokeWidth = s * 0.09f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(s * 0.1f, s * 0.9f, s * 0.9f, s * 0.1f, strike)
        }
        return bitmap
    }

    /** GlucoDataHandler-style angle: the arrow rotates between +2 (up) and -2 mg/dL per minute (down). */
    fun angleForRate(ratePerMinute: Float): Float = (-(ratePerMinute / 2f) * 90f).coerceIn(-90f, 90f)
}
