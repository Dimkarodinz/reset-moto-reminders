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
    fun `fixed dark presentation uses light status and navigation icons`() {
        val theme = File("src/main/res/values/themes.xml").readText()
        val api27Theme = File("src/main/res/values-v27/themes.xml").readText()
        val activity = File("src/main/java/dev/resetlight/MainActivity.kt").readText()
        val composeTheme = File("src/main/java/dev/resetlight/ui/ResetMotoTheme.kt").readText()

        assertTrue(theme.contains("<item name=\"android:windowLightStatusBar\">false</item>"))
        assertTrue(api27Theme.contains("<item name=\"android:windowLightNavigationBar\">false</item>"))
        assertTrue(activity.contains("isAppearanceLightNavigationBars = false"))
        assertTrue(activity.contains("isAppearanceLightStatusBars = false"))
        assertTrue(activity.contains("ResetMotoTheme"))
        assertTrue(composeTheme.contains("darkColorScheme"))
    }

    @Test
    fun `all motorcycle actions and disconnect are disabled during an operation`() {
        val activity = File("src/main/java/dev/resetlight/MainActivity.kt").readText()
        val screen = File("src/main/java/dev/resetlight/features/connection/ConnectionScreen.kt").readText()

        assertTrue(activity.contains("owner.operationInProgress.collectAsState()"))
        assertTrue(screen.contains("operationInProgress: Boolean"))
        assertTrue(screen.contains("actionsEnabled = !operationInProgress"))
        assertTrue(screen.contains("disconnectEnabled = !operationInProgress"))
    }

    @Test
    fun `main header uses the action title treatment`() {
        val screen = File("src/main/java/dev/resetlight/features/connection/ConnectionScreen.kt").readText()

        assertTrue(screen.contains("ActionTitleStyle"))
        assertTrue(screen.contains("FontWeight.Black"))
        assertTrue(screen.contains("FontStyle.Italic"))
    }

    @Test
    fun `first launch blocks motorcycle controls until safety notice is accepted`() {
        val activity = File("src/main/java/dev/resetlight/MainActivity.kt").readText()
        val consentScreen = File("src/main/java/dev/resetlight/features/consent/ReleaseConsentScreen.kt").readText()
        val strings = File("src/main/res/values/strings.xml").readText()

        assertTrue(activity.contains("ReleaseConsentStore"))
        assertTrue(activity.contains("ReleaseConsentScreen"))
        assertTrue(activity.contains("if (!consentAccepted)"))
        assertTrue(consentScreen.contains("consentAccepted"))
        assertTrue(strings.contains("consent_unofficial"))
        assertTrue(strings.contains("consent_no_warranty"))
        assertTrue(strings.contains("consent_reset_not_maintenance"))
        assertTrue(strings.contains("consent_clear_not_repair"))
        assertTrue(strings.contains("consent_owner_responsibility"))
    }

    @Test
    fun `release build enables only the existing bounded write operations`() {
        val build = File("build.gradle.kts").readText()
        val container = File("src/main/java/dev/resetlight/app/AppContainer.kt").readText()

        assertTrue(build.contains("WRITE_OPERATIONS_ENABLED"))
        assertTrue(build.contains("buildConfigField(\"boolean\", \"WRITE_OPERATIONS_ENABLED\", \"true\")"))
        assertTrue(container.contains("writesEnabled = BuildConfig.WRITE_OPERATIONS_ENABLED"))
        assertFalse(container.contains("BuildConfig.RESEARCH_BUILD"))
    }

    @Test
    fun `binary packages the project license and required notices`() {
        val build = File("build.gradle.kts").readText()

        assertTrue(build.contains("generateLegalAssets"))
        assertTrue(build.contains("../LICENSE"))
        assertTrue(build.contains("../NOTICE"))
        assertTrue(build.contains("../THIRD_PARTY_NOTICES.md"))
        assertTrue(build.contains("legal-notices"))
    }

    @Test
    fun `original OBDLink MX is packaged with localized physical pairing guidance`() {
        val build = File("build.gradle.kts").readText()
        val container = File("src/main/java/dev/resetlight/app/AppContainer.kt").readText()

        assertTrue(build.contains("obdlink-mx-android.adaptermap.yaml"))
        assertTrue(container.contains("obdlinkMxProfile"))
        listOf("values", "values-de", "values-es", "values-fr", "values-uk").forEach { valuesDir ->
            val strings = File("src/main/res/$valuesDir/strings.xml").readText()
            assertTrue("$valuesDir must name OBDLink MX", strings.contains("OBDLink MX"))
        }
    }
}
