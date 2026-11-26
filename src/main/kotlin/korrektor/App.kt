package korrektor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import korrektor.characters.CharacterArea

/** The whole window: an error banner when needed, the start screen or the open project, and the dialog. */
@Composable
fun App(
    model: AppModel,
    folderPicker: FolderPicker,
    pdfPicker: PdfPicker,
    modifier: Modifier = Modifier,
    // What the shortcuts do: the project's own (`ProjectKeys`), or a replacement for a test.
    keyActions: KeyActions? = null,
) {
    val openProject = { folderPicker.pick()?.let(model::openProject) ?: Unit }
    LaunchedEffect(model.viewer) { model.keepViewSaved() }
    // Both panels show the page the viewer is on: the editor follows it.
    val editor = model.editor
    val shownPage = model.viewer?.page
    LaunchedEffect(editor, shownPage) { if (editor != null && shownPage != null) editor.show(shownPage) }
    KeepSaving(model.saver, shownPage)
    val projectKeys = remember(model) { ProjectKeys(model) }
    val keys = keyActions ?: projectKeys
    KorrektorTheme {
        Surface(modifier.fillMaxSize()) {
            CharacterArea(model.characters) {
                Column(if (model.project != null) Modifier.shortcutKeys(keys, model.characters) else Modifier) {
                    model.error?.let { ErrorBanner(message = it, onDismiss = model::dismissError) }
                    model.notice?.let { NoticeBanner(message = it, onDismiss = model::dismissNotice) }
                    SaveWarning(model.saver)
                    val project = model.project
                    if (project == null) {
                        StartScreen(onOpenProject = openProject, onNewProject = model::openNewProject)
                    } else {
                        val actions =
                            ProjectActions(
                                model::openNewProject,
                                openProject,
                                model::openEditMetadata,
                                model::openExport,
                            )
                        ProjectScreen(project, model.metadata, actions) { room ->
                            ProjectPanels(model, project, projectKeys, room)
                        }
                    }
                }
            }
            Dialogs(model, folderPicker, pdfPicker)
        }
    }
}
