package korrektor

import androidx.compose.runtime.remember
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import korrektor.settings.AppSettings
import korrektor.viewer.hasNext
import korrektor.viewer.hasPrevious

/** Name shown in the window title. */
const val APP_NAME = "korrektor"

fun main() =
    application {
        val model = remember { AppModel(settings = AppSettings(AppSettings.defaultFile())) }
        Window(
            onCloseRequest = {
                if (model.canClose) {
                    model.saveView()
                    exitApplication()
                }
            },
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
                    )
                for (menu in menus(state)) {
                    Menu(menu.title) {
                        menu.groups.forEachIndexed { index, group ->
                            if (index > 0) Separator()
                            for (item in group) {
                                Item(item.text, enabled = item.enabled, onClick = { keys.perform(item.action) })
                            }
                        }
                    }
                }
            }
            App(model, SwingFolderPicker, AwtPdfPicker)
        }
    }
