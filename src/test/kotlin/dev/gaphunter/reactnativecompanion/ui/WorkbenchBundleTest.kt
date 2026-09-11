package dev.gaphunter.reactnativecompanion.ui

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.Properties

class WorkbenchBundleTest {
    private fun resources(suffix: String): Properties = Properties().apply {
        WorkbenchBundleTest::class.java.getResourceAsStream("/messages/WorkbenchBundle$suffix.properties")!!.reader(Charsets.UTF_8).use { load(it) }
    }
    @Test fun languagesHaveSameKeysAndFormatEveryMessage() {
        val en = resources("")
        val zh = resources("_zh")
        assertEquals(en.keys, zh.keys)
        for (key in en.stringPropertyNames()) {
            assertTrue(WorkbenchBundle.message("en", key, "A", "B", "C").isNotBlank())
            assertTrue(WorkbenchBundle.message("zh", key, "A", "B", "C").isNotBlank())
        }
    }
    @Test fun defaultDoesNotFollowChineseSystemLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
            assertEquals("Run Android", WorkbenchBundle.message("en", "run.android"))
            assertEquals("运行 Android", WorkbenchBundle.message("zh", "run.android"))
        } finally { Locale.setDefault(previous) }
    }
    @Test fun followsIdeLocaleAndFallsBackToEnglish() {
        assertEquals("zh", WorkbenchBundle.resolveLanguage("auto", Locale.SIMPLIFIED_CHINESE))
        assertEquals("zh", WorkbenchBundle.resolveLanguage("auto", Locale.TRADITIONAL_CHINESE))
        for (locale in listOf(Locale.ENGLISH, Locale.JAPANESE, Locale.GERMAN, Locale.FRENCH)) {
            assertEquals("en", WorkbenchBundle.resolveLanguage("auto", locale))
        }
        assertEquals("en", WorkbenchBundle.resolveLanguage("en", Locale.CHINESE))
        assertEquals("zh", WorkbenchBundle.resolveLanguage("zh", Locale.ENGLISH))
    }
    @Test fun placeholdersPreservePathsAndNewlines() {
        val result = WorkbenchBundle.message("en", "console.header", "C:/O'Brien/mobile", "start", "default")
        assertTrue(result.contains("C:/O'Brien/mobile\nCommand:"))
        assertFalse(result.contains("{0}"))
    }
}
