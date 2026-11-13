package korrektor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
    KorrektorTheme {
        Surface(modifier.fillMaxSize()) {
            Column {
                model.error?.let { ErrorBanner(message = it, onDismiss = model::dismissError) }
                model.notice?.let { NoticeBanner(message = it, onDismiss = model::dismissNotice) }
                val project = model.project
                if (project == null) {
                    StartScreen(onOpenProject = openProject, onNewProject = model::openNewProject)
                } else {
                    val actions = ProjectActions(model::openNewProject, openProject, model::openEditMetadata)
                    ProjectScreen(project = project, metadata = model.metadata, actions = actions)
                }
            }
            if (model.newProjectOpen) NewProjectDialog(model, pdfPicker, folderPicker)
            if (model.editMetadataOpen) EditMetadataDialog(model)
        }
    }
}
