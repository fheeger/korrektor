package korrektor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import korrektor.editor.TextPanel
import korrektor.viewer.DefaultPageSourceFactory
import korrektor.viewer.PageSourceFactory
import korrektor.viewer.ProjectViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The whole window: an error banner when needed, the start screen or the open project, and the dialog. */
@Composable
fun App(
    model: AppModel,
    folderPicker: FolderPicker,
    pdfPicker: PdfPicker,
    modifier: Modifier = Modifier,
    pageSources: PageSourceFactory = DefaultPageSourceFactory,
) {
    val openProject = { folderPicker.pick()?.let(model::openProject) ?: Unit }
    LaunchedEffect(model.viewer) { model.keepViewSaved() }
    // Both panels show the page the viewer is on: the editor follows it.
    val editor = model.editor
    val shownPage = model.viewer?.page
    LaunchedEffect(editor, shownPage) { if (editor != null && shownPage != null) editor.show(shownPage) }
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
                    ProjectScreen(project, model.metadata, actions) { room ->
                        val scope = rememberCoroutineScope()
                        SplitPane(
                            model.layout,
                            { scope.launch(Dispatchers.IO) { model.layout.save() } },
                            left = {
                                ProjectViewer(project, checkNotNull(model.viewer), Modifier.fillMaxSize(), pageSources)
                            },
                            modifier = room,
                            right = { TextPanel(checkNotNull(model.editor), Modifier.fillMaxSize()) },
                        )
                    }
                }
            }
            if (model.newProjectOpen) NewProjectDialog(model, pdfPicker, folderPicker)
            if (model.editMetadataOpen) EditMetadataDialog(model)
        }
    }
}
