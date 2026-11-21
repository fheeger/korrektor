package korrektor

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AboutTest {
    @Test
    fun theVersionIsTheOneOfTheBuild() {
        // The build passes its own version to the tests, so the two cannot drift apart.
        assertEquals(System.getProperty("korrektor.expectedVersion"), AppInfo.version)
        assertTrue(AppInfo.version.matches(Regex("""\d+\.\d+\.\d+(-[a-z]+\.\d+)?""")), AppInfo.version)
    }

    @Test
    fun theDialogShowsVersionLicenseAndRepository() =
        runComposeUiTest {
            setContent { About({}) }
            onNodeWithTag("about version").assertTextContains(AppInfo.version, substring = true)
            onNodeWithTag("about license").assertTextContains("GPL-3.0", substring = true)
            onNodeWithTag("about repository").assertTextContains(AppInfo.REPOSITORY)
        }

    @Test
    fun closeCallsBack() =
        runComposeUiTest {
            var closed = 0
            setContent { About({ closed++ }) }
            onNodeWithText("Close").performClick()
            assertEquals(1, closed)
        }

    @Test
    fun theAboutDialogIsOffAtTheStartAndTheEntryIsNamedAfterTheApp() {
        assertFalse(AppModel().aboutOpen)
        assertEquals("About korrektor", ABOUT_ITEM)
    }
}
