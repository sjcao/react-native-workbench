package dev.gaphunter.reactnativecompanion.ui

import com.intellij.DynamicBundle
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

/** Follow the IDE by default; unsupported languages always fall back to English. */
object WorkbenchBundle {
    fun resolveLanguage(language: String, ideLocale: Locale): String = when (language) {
        "zh", "en" -> language
        else -> if (ideLocale.language == "zh") "zh" else "en"
    }

    fun message(language: String, key: String, vararg args: Any): String {
        val resolved = if (language == "en" || language == "zh") language
            else resolveLanguage(language, DynamicBundle.getLocale())
        val locale = if (resolved == "zh") Locale.SIMPLIFIED_CHINESE else Locale.ROOT
        val bundle = ResourceBundle.getBundle("messages.WorkbenchBundle", locale,
            WorkbenchBundle::class.java.classLoader,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES))
        return MessageFormat(bundle.getString(key), locale).format(args)
    }
}
