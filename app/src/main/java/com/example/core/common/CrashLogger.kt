package com.example.core.common

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves the last crash to a small text file (shown in Settings → Diagnóstico) so a closing app can be diagnosed
 * without a computer. Nothing is sent anywhere.
 */
object CrashLogger {

    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_CHARS = 6000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val writer = StringWriter()
                throwable.printStackTrace(PrintWriter(writer))
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val text = "$time  (hilo: ${thread.name})\n$writer".take(MAX_CHARS)
                File(appContext.filesDir, FILE_NAME).writeText(text)
            } catch (e: Exception) {
                // Never fail while reporting a failure
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? {
        return try {
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            if (file.exists()) file.readText().ifBlank { null } else null
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        try {
            File(context.applicationContext.filesDir, FILE_NAME).delete()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
