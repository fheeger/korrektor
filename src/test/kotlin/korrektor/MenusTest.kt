package korrektor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MenusTest {
    private val open =
        MenuState(
            projectOpen = true,
            hasPreviousPage = true,
            hasNextPage = true,
            pageHasLines = true,
            canUndo = true,
            canRedo = true,
            hasNextUnchecked = true,
            hasPreviousUnchecked = true,
        )

    private fun items(state: MenuState) = menus(state).flatMap { it.groups }.flatten()

    private fun MenuState.enabled() = items(this).filter { it.enabled }.map { it.action }.toSet()

    @Test
    fun everyActionOfTheTableIsInExactlyOneMenuEntry() {
        val actions = items(open).map { it.action }
        assertEquals(AppAction.entries.toSet(), actions.toSet(), "every action has an entry")
        assertEquals(actions.size, actions.toSet().size, "no action twice")
        assertEquals(Shortcuts.all.map { it.action }.toSet(), actions.toSet())
    }

    @Test
    fun everyEntryShowsItsLabelAndItsShortcut() {
        for (item in items(open)) {
            val shortcut = Shortcuts.of(item.action)
            assertTrue(item.text.startsWith(shortcut.label), item.text)
            assertTrue(item.text.endsWith(shortcut.shown.text), item.text)
        }
    }

    @Test
    fun theJumpsToUncheckedPagesAreEnabledOnlyWhenThereIsSuchAPage() {
        val none = open.copy(hasNextUnchecked = false, hasPreviousUnchecked = false)
        assertFalse(AppAction.NEXT_UNCHECKED in none.enabled())
        assertFalse(AppAction.PREVIOUS_UNCHECKED in none.enabled())
        assertTrue(AppAction.NEXT_UNCHECKED in none.copy(hasNextUnchecked = true).enabled())
        assertFalse(AppAction.PREVIOUS_UNCHECKED in none.copy(hasNextUnchecked = true).enabled())
        assertTrue(AppAction.PREVIOUS_UNCHECKED in none.copy(hasPreviousUnchecked = true).enabled())
        assertTrue(AppAction.MARK_CORRECTED in none.enabled())
    }

    @Test
    fun theMenusAreEditGoPageViewAndHelp() {
        assertEquals(listOf("Edit", "Go", "Page", "View", "Help"), menus(open).map { it.title })
        val page = menus(open).first { it.title == "Page" }.groups.flatten()
        assertEquals(
            listOf(
                AppAction.MARK_CORRECTED,
                AppAction.MARK_UNCHECKED,
                AppAction.NEXT_UNCHECKED,
                AppAction.PREVIOUS_UNCHECKED,
            ),
            page.map { it.action },
        )
        assertTrue(page.first().text.endsWith("Ctrl+Enter"), page.first().text)
    }

    @Test
    fun withoutAProjectOnlyTheListOfShortcutsCanBeUsed() {
        assertEquals(setOf(AppAction.SHOW_SHORTCUTS), MenuState().enabled())
    }

    @Test
    fun withAProjectEverythingCanBeUsed() {
        assertEquals(AppAction.entries.toSet(), open.enabled())
    }

    @Test
    fun theFirstAndLastPageDisableTheirDirection() {
        val first = open.copy(hasPreviousPage = false).enabled()
        assertFalse(AppAction.PREVIOUS_PAGE in first)
        assertTrue(AppAction.NEXT_PAGE in first)
        val last = open.copy(hasNextPage = false).enabled()
        assertFalse(AppAction.NEXT_PAGE in last)
        assertTrue(AppAction.PREVIOUS_PAGE in last)
    }

    @Test
    fun aPageWithoutLinesDisablesTheMovesBetweenLines() {
        val enabled = open.copy(pageHasLines = false).enabled()
        for (action in listOf(
            AppAction.PREVIOUS_LINE,
            AppAction.NEXT_LINE,
            AppAction.FIRST_LINE,
            AppAction.LAST_LINE,
        )) {
            assertFalse(action in enabled, "$action")
        }
        assertTrue(AppAction.NEXT_PAGE in enabled)
        assertTrue(AppAction.ZOOM_IN in enabled)
    }

    @Test
    fun undoAndRedoAreEnabledOnlyWhenThereIsSomethingToDo() {
        assertFalse(AppAction.UNDO in open.copy(canUndo = false).enabled())
        assertTrue(AppAction.REDO in open.copy(canUndo = false).enabled())
        assertFalse(AppAction.REDO in open.copy(canRedo = false).enabled())
        assertTrue(AppAction.UNDO in open.copy(canRedo = false).enabled())
        val edit = menus(open).first()
        assertEquals("Edit", edit.title)
        assertEquals(listOf(AppAction.UNDO, AppAction.REDO), edit.groups.flatten().map { it.action })
        assertTrue(
            edit.groups
                .flatten()
                .first()
                .text
                .endsWith("Ctrl+Z"),
        )
        assertTrue(
            edit.groups
                .flatten()
                .last()
                .text
                .endsWith("Ctrl+Y"),
        )
    }

    @Test
    fun theF1KeyToggledTheListInTheModel() {
        val model = AppModel()
        assertFalse(model.shortcutsOpen)
        ProjectKeys(model).perform(AppAction.SHOW_SHORTCUTS)
        assertTrue(model.shortcutsOpen)
        ProjectKeys(model).perform(AppAction.SHOW_SHORTCUTS)
        assertFalse(model.shortcutsOpen)
    }

    @Test
    fun everyActionHasAHandlerInTheProject() {
        // An action in the table that does nothing would be a menu entry and a key that are dead.
        val model = AppModel()
        val keys = ProjectKeys(model)
        assertEquals(AppAction.entries.toSet(), keys.handledActions)
    }
}
