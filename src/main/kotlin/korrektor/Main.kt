package korrektor

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import korrektor.settings.AppSettings

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
            App(model, SwingFolderPicker, AwtPdfPicker)
        }
    }
