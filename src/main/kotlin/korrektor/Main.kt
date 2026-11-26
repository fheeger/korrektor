package korrektor

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import korrektor.editor.uncheckedAfter
import korrektor.editor.uncheckedBefore
import korrektor.search.selectedCount
import korrektor.settings.AppSettings
import korrektor.viewer.hasNext
import korrektor.viewer.hasPrevious
import kotlinx.coroutines.launch

/** Name shown in the window title. */
const val APP_NAME = "korrektor"

private const val HELP_MENU = "Help"

/** The entry of the Help menu that opens the About dialog. */
const val ABOUT_ITEM = "About $APP_NAME"

fun main() =
    application {
        val model = remember { AppModel(settings = AppSettings(AppSettings.defaultFile())) }
        val scope = rememberCoroutineScope()
        model.exit = ::exitApplication
        Window(
            // The corrections are saved first, without asking; if that fails the user is told (see `requestClose`).
            onCloseRequest = { scope.launch { model.requestClose() } },
            title = windowTitle(model.project, model.metadata),
        ) {
            val keys = remember { ProjectKeys(model) }
            MenuBar {
                val viewer = model.viewer
                val state =
                    MenuState(
                        projectOpen = model.project != null,
                        hasPreviousPage = viewer?.hasPrevious == true,
                        hasNextPage = viewer?.hasNext == true,
                        pageHasLines = model.editor?.lines?.isNotEmpty() == true,
                        canUndo = model.editor?.history?.canUndo == true,
                        canRedo = model.editor?.history?.canRedo == true,
                        hasNextUnchecked =
                            viewer != null && model.editor?.uncheckedAfter(viewer.page, viewer.pageCount) != null,
                        hasPreviousUnchecked = viewer != null && model.editor?.uncheckedBefore(viewer.page) != null,
                        hasMatches = model.search?.results?.isNotEmpty() == true,
                        replaceShown = model.search?.let { it.open && it.showReplace } == true,
                        hasCurrentMatch = model.search?.let { it.current in it.results.indices } == true,
                        hasCheckedMatches = model.search?.let { it.selectedCount > 0 && !it.replacing } == true,
                    )
                for (menu in menus(state)) {
                    Menu(menu.title) {
                        menu.groups.forEachIndexed { index, group ->
                            if (index > 0) Separator()
                            for (item in group) {
                                Item(item.text, enabled = item.enabled, onClick = { keys.perform(item.action) })
                            }
                        }
                        // No shortcut, so it is not in the table of shortcuts that the menus are made from.
                        if (menu.title == HELP_MENU) {
                            Separator()
                            Item(ABOUT_ITEM, onClick = { model.aboutOpen = true })
                        }
                    }
                }
            }
            App(model, SwingFolderPicker, AwtPdfPicker)
        }
    }
