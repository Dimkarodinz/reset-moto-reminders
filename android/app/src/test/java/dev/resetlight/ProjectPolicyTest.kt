package dev.resetlight

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectPolicyTest {
    @Test
    fun `unit test environment starts`() {
        assertTrue(true)
    }

    @Test
    fun `manifest has no internet permission`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("android.permission.INTERNET"))
    }

    @Test
    fun `diagnostic files are excluded from backup and device transfer`() {
        val legacy = File("src/main/res/xml/backup_rules.xml").readText()
        val modern = File("src/main/res/xml/data_extraction_rules.xml").readText()
        listOf("diagnostic-logs/", "diagnostic-exports/").forEach { path ->
            assertTrue(legacy.contains(path))
            assertTrue(modern.contains(path))
        }
        assertTrue(modern.contains("device-transfer"))
    }

    @Test
    fun `light system bars use dark status and navigation icons`() {
        val theme = File("src/main/res/values/themes.xml").readText()
        val api27Theme = File("src/main/res/values-v27/themes.xml").readText()
        val activity = File("src/main/java/dev/resetlight/MainActivity.kt").readText()

        assertTrue(theme.contains("<item name=\"android:windowLightStatusBar\">true</item>"))
        assertTrue(api27Theme.contains("<item name=\"android:windowLightNavigationBar\">true</item>"))
        assertTrue(activity.contains("isAppearanceLightNavigationBars = true"))
    }
}
