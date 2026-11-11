package korrektor

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/** Name shown in the window title. */
const val APP_NAME = "korrektor"

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = APP_NAME) {
            BasicText(APP_NAME)
        }
    }
