package com.example.core.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

object BitmapUtils {

    // 16 MB in-memory LRU cache for thumbnails
    private val thumbnailCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    /**
     * Decodes and scales an image to fit target bounds, respecting EXIF orientation.
     * Guaranteed safe for Android 5+ (minSdk 21) using standard BitmapFactory.
     */
    fun decodeSampledBitmap(
        context: Context,
        uriString: String,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        return try {
            val (origWidth, origHeight) = readBitmapDimensions(context, uriString)
            if (origWidth <= 0 || origHeight <= 0) return null

            var sampleSize = 1
            if (origHeight > reqHeight || origWidth > reqWidth) {
                val halfHeight = origHeight / 2
                val halfWidth = origWidth / 2
                while ((halfHeight / sampleSize) >= reqHeight && (halfWidth / sampleSize) >= reqWidth) {
                    sampleSize *= 2
                }
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = openStream(context, uriString)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            // Apply EXIF rotation
            val rotationDegrees = getExifRotation(context, uriString)
            val oriented = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated != decoded) decoded.recycle()
                rotated
            } else {
                decoded
            }

            // Scale to match target dimensions if needed
            if (reqWidth > 0 && reqHeight > 0 && (oriented.width > reqWidth * 1.5 || oriented.height > reqHeight * 1.5)) {
                val scale = minOf(reqWidth.toFloat() / oriented.width, reqHeight.toFloat() / oriented.height)
                val targetW = (oriented.width * scale).roundToInt().coerceAtLeast(1)
                val targetH = (oriented.height * scale).roundToInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(oriented, targetW, targetH, true)
                if (scaled != oriented) oriented.recycle()
                scaled
            } else {
                oriented
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets or loads a small thumbnail (~120x120) with in-memory LRU caching.
     */
    fun getThumbnail(context: Context, uriString: String, sizePx: Int = 120): Bitmap? {
        val cached = thumbnailCache.get(uriString)
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        val decoded = decodeSampledBitmap(context, uriString, sizePx, sizePx)
        if (decoded != null) {
            thumbnailCache.put(uriString, decoded)
        }
        return decoded
    }

    /**
     * Applies fast pure-Kotlin box blur to a bitmap.
     * Scales down, applies 2 passes of box blur, and scales back up.
     * Completely safe across all Android versions (minSdk 21+).
     */
    fun applyBlur(source: Bitmap, blurRadius: Float): Bitmap {
        if (blurRadius <= 0.5f) return source

        return try {
            // Downscale factor proportional to blur radius (higher radius = lower resolution for softness & speed)
            val downscale = (1f / (1f + blurRadius * 0.35f)).coerceIn(0.12f, 0.45f)
            val smallW = (source.width * downscale).roundToInt().coerceAtLeast(16)
            val smallH = (source.height * downscale).roundToInt().coerceAtLeast(16)

            val small = Bitmap.createScaledBitmap(source, smallW, smallH, true)
            val radiusInt = (blurRadius * 0.75f).roundToInt().coerceIn(2, 16)

            fastBoxBlur(small, radiusInt)

            val blurred = Bitmap.createScaledBitmap(small, source.width, source.height, true)
            small.recycle()
            blurred
        } catch (e: Exception) {
            source
        }
    }

    private fun fastBoxBlur(bitmap: Bitmap, radius: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val div = radius + radius + 1

        val r = IntArray(w * h)
        val g = IntArray(w * h)
        val b = IntArray(w * h)

        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int

        val vmin = IntArray(max(w, h))
        val divsum = (div + 1) shr 1
        val divsum2 = divsum * divsum
        val dv = IntArray(256 * divsum2)
        for (idx in 0 until 256 * divsum2) {
            dv[idx] = idx / divsum2
        }

        yw = 0
        yi = 0

        for (row in 0 until h) {
            rsum = 0
            gsum = 0
            bsum = 0
            for (idx in -radius..radius) {
                p = pix[yi + minOf(wm, maxOf(idx, 0))]
                rsum += (p and 0xff0000) shr 16
                gsum += (p and 0x00ff00) shr 8
                bsum += (p and 0x0000ff)
            }
            for (col in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                if (row == 0) {
                    vmin[col] = minOf(col + radius + 1, wm)
                }
                p = pix[yw + vmin[col]]
                val p2 = pix[yw + maxOf(col - radius, 0)]

                rsum += ((p and 0xff0000) - (p2 and 0xff0000)) shr 16
                gsum += ((p and 0x00ff00) - (p2 and 0x00ff00)) shr 8
                bsum += (p and 0x0000ff) - (p2 and 0x0000ff)
                yi++
            }
            yw += w
        }

        for (col in 0 until w) {
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (idx in -radius..radius) {
                yi = maxOf(0, yp) + col
                rsum += r[yi]
                gsum += g[yi]
                bsum += b[yi]
                yp += w
            }
            yi = col
            for (row in 0 until h) {
                pix[yi] = (0xff000000.toInt()) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                if (col == 0) {
                    vmin[row] = minOf(row + radius + 1, hm) * w
                }
                p = col + vmin[row]
                val p2 = col + maxOf(row - radius, 0) * w

                rsum += r[p] - r[p2]
                gsum += g[p] - g[p2]
                bsum += b[p] - b[p2]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
    }

    private fun readBitmapDimensions(context: Context, uriString: String): Pair<Int, Int> {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(context, uriString)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            Pair(options.outWidth, options.outHeight)
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    private fun getExifRotation(context: Context, uriString: String): Int {
        return try {
            openStream(context, uriString)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun openStream(context: Context, uriString: String): InputStream? {
        return try {
            if (uriString.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(uriString))
            } else if (uriString.startsWith("file://")) {
                File(Uri.parse(uriString).path ?: "").inputStream()
            } else if (uriString.startsWith("asset://")) {
                val assetPath = uriString.removePrefix("asset://")
                context.assets.open(assetPath)
            } else {
                File(uriString).inputStream()
            }
        } catch (e: Exception) {
            null
        }
    }
}
