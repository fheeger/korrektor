package korrektor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The whole window: an error banner when needed, the start screen or the open project, and the dialog. */
@Composable
fun App(
    model: AppModel,
    folderPicker: FolderPicker,
    pdfPicker: PdfPicker,
    modifier: Modifier = Modifier,
) {
    val openProject = { folderPicker.pick()?.let(model::openProject) ?: Unit }
    MaterialTheme {
        Surface(modifier.fillMaxSize()) {
            Column {
                model.error?.let { ErrorBanner(message = it, onDismiss = model::dismissError) }
                val project = model.project
                if (project == null) {
                    StartScreen(onOpenProject = openProject, onNewProject = model::openNewProject)
                } else {
                    ProjectScreen(project = project, onOpenProject = openProject, onNewProject = model::openNewProject)
                }
            }
            if (model.newProjectOpen) NewProjectDialog(model, pdfPicker, folderPicker)
        }
    }
}
