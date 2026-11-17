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
)

/** How the actions are arranged in the menus. Every action of [Shortcuts] has to be in here (tested). */
private val MENU_LAYOUT: List<Pair<String, List<List<AppAction>>>> =
    listOf(
        "Edit" to listOf(listOf(AppAction.UNDO, AppAction.REDO)),
        "Go" to
            listOf(
                listOf(AppAction.PREVIOUS_LINE, AppAction.NEXT_LINE, AppAction.FIRST_LINE, AppAction.LAST_LINE),
                listOf(AppAction.PREVIOUS_PAGE, AppAction.NEXT_PAGE, AppAction.GO_TO_PAGE),
            ),
        "Page" to listOf(listOf(AppAction.MARK_CORRECTED, AppAction.MARK_UNCHECKED)),
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

private fun enabled(
    action: AppAction,
    state: MenuState,
): Boolean =
    when (action) {
        AppAction.SHOW_SHORTCUTS -> true
        AppAction.PREVIOUS_PAGE -> state.projectOpen && state.hasPreviousPage
        AppAction.NEXT_PAGE -> state.projectOpen && state.hasNextPage
        AppAction.UNDO -> state.projectOpen && state.canUndo
        AppAction.REDO -> state.projectOpen && state.canRedo
        AppAction.PREVIOUS_LINE, AppAction.NEXT_LINE, AppAction.FIRST_LINE, AppAction.LAST_LINE ->
            state.projectOpen && state.pageHasLines
        else -> state.projectOpen
    }
