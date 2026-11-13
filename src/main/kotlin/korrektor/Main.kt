package korrektor

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/** Name shown in the window title. */
const val APP_NAME = "korrektor"

fun main() =
    application {
        val model = remember { AppModel() }
        Window(onCloseRequest = ::exitApplication, title = windowTitle(model.project)) {
            App(model, SwingFolderPicker, AwtPdfPicker)
        }
    }
