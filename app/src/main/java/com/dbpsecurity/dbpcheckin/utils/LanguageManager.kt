package com.dbpsecurity.dbpcheckin.utils

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

object LanguageManager {
    var currentLanguage by mutableStateOf("en") // "en" for English, "hi" for Hindi

    fun setLocale(context: Context, languageCode: String) {
        currentLanguage = languageCode
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
