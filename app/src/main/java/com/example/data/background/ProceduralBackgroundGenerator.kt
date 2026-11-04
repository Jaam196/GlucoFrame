package com.example.data.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import java.io.File
import java.io.FileOutputStream

object ProceduralBackgroundGenerator {

    data class ProceduralPreset(
        val fileName: String,
        val displayName: String,
        val category: String = "abstractos",
        val primaryColorHex: String,
        val secondaryColorHex: String,
        val accentColorHex: String,
        val isRadial: Boolean = false
    )

    val PRESETS = listOf(
        ProceduralPreset("rojo_profundo.jpg", "Rojo Profundo", "abstractos", "#8B0000", "#E53935", "#FF5722"),
        ProceduralPreset("verde_esmeralda.jpg", "Verde Esmeralda", "abstractos", "#004D40", "#2E7D32", "#66BB6A"),
        ProceduralPreset("azul_oceano.jpg", "Azul Océano", "abstractos", "#0D47A1", "#1976D2", "#00BCD4"),
        ProceduralPreset("amarillo_dorado.jpg", "Amarillo Dorado", "abstractos", "#E65100", "#F57F17", "#FFEE58"),
        ProceduralPreset("purpura_nebulosa.jpg", "Púrpura Nebulosa", "abstractos", "#311B92", "#6A1B9A", "#AB47BC"),
        ProceduralPreset("naranja_atardecer.jpg", "Naranja Atardecer", "abstractos", "#BF360C", "#E64A19", "#FF7043"),
        ProceduralPreset("cian_glaciar.jpg", "Cian Glaciar", "abstractos", "#006064", "#00838F", "#26C6DA"),
        ProceduralPreset("rosa_aurora.jpg", "Rosa Aurora", "abstractos", "#880E4F", "#AD1457", "#EC407A"),
        ProceduralPreset("neutro_carbon.jpg", "Gris Carbón", "abstractos", "#121212", "#242424", "#424242"),
        ProceduralPreset("neutro_claro.jpg", "Niebla Gris", "abstractos", "#616161", "#9E9E9E", "#BDBDBD"),
        ProceduralPreset("multicolor_calido.jpg", "Malla Cálida", "abstractos", "#D32F2F", "#F57C00", "#FBC02D", isRadial = true),
        ProceduralPreset("multicolor_frio.jpg", "Malla Fría", "abstractos", "#1565C0", "#00897B", "#43A047", isRadial = true)
    )

    /**
     * Generates the 12 procedural preset backgrounds into filesDir/preset_backgrounds/abstractos/
     * if they do not already exist.
     * Returns the list of created or existing File objects.
     */
    fun ensureProceduralPresets(context: Context): List<File> {
        val baseDir = File(context.filesDir, "preset_backgrounds/abstractos")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }

        val resultFiles = mutableListOf<File>()
        val width = 960
        val height = 600

        for (preset in PRESETS) {
            val targetFile = File(baseDir, preset.fileName)
            if (!targetFile.exists() || targetFile.length() == 0L) {
                try {
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                    val c1 = Color.parseColor(preset.primaryColorHex)
                    val c2 = Color.parseColor(preset.secondaryColorHex)
                    val c3 = Color.parseColor(preset.accentColorHex)

                    if (preset.isRadial) {
                        paint.shader = RadialGradient(
                            width * 0.4f, height * 0.45f,
                            width * 0.65f,
                            intArrayOf(c3, c2, c1),
                            floatArrayOf(0f, 0.55f, 1f),
                            Shader.TileMode.CLAMP
                        )
                    } else {
                        paint.shader = LinearGradient(
                            0f, 0f,
                            width.toFloat(), height.toFloat(),
                            intArrayOf(c1, c2, c3),
                            floatArrayOf(0f, 0.5f, 1f),
                            Shader.TileMode.CLAMP
                        )
                    }

                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

                    FileOutputStream(targetFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    bitmap.recycle()
                } catch (e: Exception) {
                    // Ignore
                }
            }
            if (targetFile.exists()) {
                resultFiles.add(targetFile)
            }
        }
        return resultFiles
    }
}
