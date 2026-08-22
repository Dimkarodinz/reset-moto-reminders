package dev.resetlight.research.general

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class GeneralResearchProjectPolicyTest {
    @Test
    fun `research app uses a fixed dark theme and light system icons`() {
        val theme = File("src/main/res/values/themes.xml").readText()
        val api27Theme = File("src/main/res/values-v27/themes.xml").readText()
        val activity = File("src/main/java/dev/resetlight/research/general/MainActivity.kt").readText()
        val composeTheme = File(
            "src/main/java/dev/resetlight/research/general/GeneralResearchTheme.kt",
        ).readText()

        assertTrue(theme.contains("<item name=\"android:windowLightStatusBar\">false</item>"))
        assertTrue(api27Theme.contains("<item name=\"android:windowLightNavigationBar\">false</item>"))
        assertTrue(activity.contains("isAppearanceLightNavigationBars = false"))
        assertTrue(activity.contains("isAppearanceLightStatusBars = false"))
        assertTrue(activity.contains("GeneralResearchTheme"))
        assertTrue(composeTheme.contains("darkColorScheme"))
    }

    @Test
    fun `header uses the action title treatment without changing body typography`() {
        val screen = File(
            "src/main/java/dev/resetlight/research/general/GeneralResearchScreen.kt",
        ).readText()

        assertTrue(screen.contains("ActionTitleStyle"))
        assertTrue(screen.contains("FontWeight.Black"))
        assertTrue(screen.contains("FontStyle.Italic"))
        assertTrue(screen.contains("FontFamily.SansSerif"))
    }
}
