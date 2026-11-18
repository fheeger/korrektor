package korrektor

/** One entry of a menu: the action it performs, the text it shows (with its shortcut) and whether it can be used. */
data class MenuItemSpec(
    val action: AppAction,
    val text: String,
    val enabled: Boolean,
)

/** A menu of the menu bar; its entries are in [groups], with a separator between two groups. */
data class MenuSpec(
    val title: String,
    val groups: List<List<MenuItemSpec>>,
)

/** What decides which entries can be used right now. */
data class MenuState(
    val projectOpen: Boolean = false,
    val hasPreviousPage: Boolean = false,
    val hasNextPage: Boolean = false,
    val pageHasLines: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasNextUnchecked: Boolean = false,
    val hasPreviousUnchecked: Boolean = false,
    val hasMatches: Boolean = false,
    val replaceShown: Boolean = false,
)

/** How the actions are arranged in the menus. Every action of [Shortcuts] has to be in here (tested). */
private val MENU_LAYOUT: List<Pair<String, List<List<AppAction>>>> =
    listOf(
        "Edit" to
            listOf(
                listOf(AppAction.UNDO, AppAction.REDO),
                listOf(AppAction.FIND, AppAction.REPLACE, AppAction.NEXT_MATCH, AppAction.PREVIOUS_MATCH),
                listOf(AppAction.TOGGLE_MARK, AppAction.REPLACE_ONE, AppAction.REPLACE_MARKED),
            ),
        "Go" to
            listOf(
                listOf(AppAction.PREVIOUS_LINE, AppAction.NEXT_LINE, AppAction.FIRST_LINE, AppAction.LAST_LINE),
                listOf(AppAction.PREVIOUS_PAGE, AppAction.NEXT_PAGE, AppAction.GO_TO_PAGE),
            ),
        "Page" to
            listOf(
                listOf(AppAction.MARK_CORRECTED, AppAction.MARK_UNCHECKED),
                listOf(AppAction.NEXT_UNCHECKED, AppAction.PREVIOUS_UNCHECKED),
            ),
        "View" to listOf(listOf(AppAction.ZOOM_IN, AppAction.ZOOM_OUT, AppAction.FIT_PAGE, AppAction.FIT_WIDTH)),
        "Help" to listOf(listOf(AppAction.SHOW_SHORTCUTS)),
    )

/**
 * The menus of the window, made from the table of shortcuts so that the menus and the keys cannot disagree. Each entry
 * shows its shortcut after the name. The entries only show the key: it is the key handler (`Modifier.shortcutKeys`)
 * that carries it out, and a menu accelerator on top could do it a second time.
 */
fun menus(state: MenuState): List<MenuSpec> =
    MENU_LAYOUT.map { (title, groups) ->
        MenuSpec(
            title,
            groups.map { group ->
                group.map { action ->
                    val shortcut = Shortcuts.of(action)
                    MenuItemSpec(action, "${shortcut.label} ${shortcut.shown.text}", enabled(action, state))
                }
            },
        )
    }

/** What else, besides an open project, an action needs; an action that is not here needs nothing else. */
private val NEEDS: Map<AppAction, (MenuState) -> Boolean> =
    mapOf(
        AppAction.PREVIOUS_PAGE to { it.hasPreviousPage },
        AppAction.NEXT_PAGE to { it.hasNextPage },
        AppAction.NEXT_UNCHECKED to { it.hasNextUnchecked },
        AppAction.PREVIOUS_UNCHECKED to { it.hasPreviousUnchecked },
        AppAction.NEXT_MATCH to { it.hasMatches },
        AppAction.PREVIOUS_MATCH to { it.hasMatches },
        AppAction.TOGGLE_MARK to { it.replaceShown && it.hasMatches },
        AppAction.REPLACE_ONE to { it.replaceShown && it.hasMatches },
        AppAction.REPLACE_MARKED to { it.replaceShown && it.hasMatches },
        AppAction.UNDO to { it.canUndo },
        AppAction.REDO to { it.canRedo },
        AppAction.PREVIOUS_LINE to { it.pageHasLines },
        AppAction.NEXT_LINE to { it.pageHasLines },
        AppAction.FIRST_LINE to { it.pageHasLines },
        AppAction.LAST_LINE to { it.pageHasLines },
    )

private fun enabled(
    action: AppAction,
    state: MenuState,
): Boolean = action == AppAction.SHOW_SHORTCUTS || (state.projectOpen && (NEEDS[action]?.invoke(state) ?: true))
