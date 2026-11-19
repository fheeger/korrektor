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
            hasMatches = true,
            replaceShown = true,
            hasCurrentMatch = true,
            hasCheckedMatches = true,
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
    fun theMenusAreFileEditGoPageViewAndHelp() {
        assertEquals(listOf("File", "Edit", "Go", "Page", "View", "Help"), menus(open).map { it.title })
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
    fun exportIsInTheFileMenuAndNeedsAProject() {
        val file = menus(open).first()
        assertEquals("File", file.title)
        assertEquals(listOf(AppAction.EXPORT), file.groups.flatten().map { it.action })
        assertTrue(
            file.groups
                .flatten()
                .single()
                .text
                .endsWith("Ctrl+E"),
        )
        assertTrue(AppAction.EXPORT in open.enabled())
        assertFalse(AppAction.EXPORT in MenuState().enabled())
    }

    @Test
    fun theStepsBetweenHitsNeedResults() {
        val none = open.copy(hasMatches = false).enabled()
        assertFalse(AppAction.NEXT_MATCH in none)
        assertFalse(AppAction.PREVIOUS_MATCH in none)
        assertTrue(AppAction.FIND in none && AppAction.REPLACE in none)
        assertTrue(AppAction.NEXT_MATCH in open.enabled())
    }

    @Test
    fun theReplaceKeysNeedTheReplaceFieldAndResults() {
        val replacing = AppAction.TOGGLE_MARK to AppAction.REPLACE_ONE
        val all = setOf(replacing.first, replacing.second, AppAction.REPLACE_MARKED)
        assertTrue(open.enabled().containsAll(all))
        assertTrue(open.copy(replaceShown = false).enabled().none { it in all }, "the replace field is hidden")
        assertTrue(
            open.copy(hasMatches = false, hasCurrentMatch = false, hasCheckedMatches = false).enabled().none {
                it in
                    all
            },
        )
        assertFalse(AppAction.TOGGLE_MARK in open.copy(hasCurrentMatch = false).enabled())
        assertFalse(AppAction.REPLACE_MARKED in open.copy(hasCheckedMatches = false).enabled())
        assertTrue(AppAction.REPLACE_ONE in open.copy(hasCurrentMatch = false).enabled(), "it goes to the first hit")
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
        val edit = menus(open).first { it.title == "Edit" }
        assertEquals("Edit", edit.title)
        assertEquals(
            listOf(
                listOf(AppAction.UNDO, AppAction.REDO),
                listOf(AppAction.FIND, AppAction.REPLACE, AppAction.NEXT_MATCH, AppAction.PREVIOUS_MATCH),
                listOf(AppAction.TOGGLE_MARK, AppAction.REPLACE_ONE, AppAction.REPLACE_MARKED),
            ),
            edit.groups.map { group -> group.map { it.action } },
        )
        val undoRedo = edit.groups.first()
        assertTrue(undoRedo.first().text.endsWith("Ctrl+Z"), undoRedo.first().text)
        assertTrue(undoRedo.last().text.endsWith("Ctrl+Y"), undoRedo.last().text)
        val find = edit.groups[1]
        for ((item, keys) in find.zip(listOf("Ctrl+F", "Ctrl+H", "F3", "Shift+F3"))) {
            assertTrue(item.text.endsWith(keys), item.text)
        }
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
