package korrektor.characters

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.awaitApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.awt.GraphicsEnvironment
import javax.swing.JMenu
import javax.swing.JMenuItem
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Insert menu as a window builds it. The window has to be shown, for a moment: a hidden one does not bring its
 * menu up to date. Without a screen the test does nothing.
 */
class CharacterMenuTest {
    private fun JMenu.entries(): List<JMenuItem> = (0 until itemCount).mapNotNull { getItem(it) }

    private suspend fun waitFor(condition: () -> Boolean) {
        while (!condition()) delay(20)
    }

    @Test
    fun theMenuHasEveryCharacterInItsGroupAndInsertsIt() {
        if (GraphicsEnvironment.isHeadless()) return
        val input = CharacterInput()
        val inserted = mutableListOf<String>()
        var groups = emptyList<Pair<String, List<String>>>()
        var enabledWithoutField = true
        runBlocking {
            withTimeout(60_000) {
                awaitApplication {
                    Window(onCloseRequest = ::exitApplication) {
                        MenuBar { Menu(INSERT_MENU) { CharacterMenus(input) } }
                        LaunchedEffect(Unit) {
                            fun submenus() =
                                window.jMenuBar
                                    .getMenu(0)
                                    .entries()
                                    .map { it as JMenu }

                            fun historic() = submenus().first().entries()
                            waitFor {
                                window.jMenuBar.menuCount == 1 &&
                                    submenus().sumOf { it.itemCount } == SpecialCharacters.all.size
                            }
                            groups = submenus().map { menu -> menu.text to menu.entries().map { it.text } }
                            enabledWithoutField = historic().first().isEnabled
                            input.focused(CharacterTarget { inserted += it })
                            waitFor { historic()[1].isEnabled }
                            historic()[1].doClick()
                            waitFor { inserted.isNotEmpty() }
                            exitApplication()
                        }
                    }
                }
            }
        }
        val expected =
            CharacterGroup.entries.map { group ->
                group.title to SpecialCharacters.all.filter { it.group == group }.map { it.menuText }
            }
        assertEquals(expected, groups)
        assertEquals(false, enabledWithoutField)
        assertEquals(listOf("aͤ"), inserted)
    }
}
