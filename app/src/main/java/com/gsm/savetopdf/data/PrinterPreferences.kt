package com.gsm.savetopdf.data

import android.content.Context
import com.gsm.savetopdf.print.MediaFormat

/**
 * Minimal synchronous SharedPreferences store for default paper format and save-location mode.
 */
class PrinterPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun getDefaultMediaFormat(): MediaFormat {
        return MediaFormat.fromId(prefs.getString(KEY_DEFAULT_MEDIA_FORMAT, MediaFormat.ISO_A4.id))
    }

    fun setDefaultMediaFormat(format: MediaFormat) {
        prefs.edit().putString(KEY_DEFAULT_MEDIA_FORMAT, format.id).apply()
    }

    fun isAskLocationEveryTime(): Boolean {
        return prefs.getBoolean(KEY_ASK_LOCATION_EVERY_TIME, true)
    }

    fun setAskLocationEveryTime(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ASK_LOCATION_EVERY_TIME, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "save_as_pdf_printer_prefs"
        private const val KEY_DEFAULT_MEDIA_FORMAT = "default_media_format"
        private const val KEY_ASK_LOCATION_EVERY_TIME = "ask_location_every_time"
    }
}
