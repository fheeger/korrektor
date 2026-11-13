package korrektor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The whole window: an error banner when needed, and the start screen or the open project. */
@Composable
fun App(
    model: AppModel,
    folderPicker: FolderPicker,
    modifier: Modifier = Modifier,
) {
    val openProject = { folderPicker.pick()?.let(model::openProject) ?: Unit }
    MaterialTheme {
        Surface(modifier.fillMaxSize()) {
            Column {
                model.error?.let { ErrorBanner(message = it, onDismiss = model::dismissError) }
                val project = model.project
                if (project == null) {
                    StartScreen(onOpenProject = openProject)
                } else {
                    ProjectScreen(project = project, onOpenProject = openProject)
                }
            }
        }
    }
}
