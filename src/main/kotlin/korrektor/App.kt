package korrektor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import korrektor.editor.TextPanel
import korrektor.editor.clickOnScan
import korrektor.editor.highlightFor
import korrektor.viewer.DefaultPageSourceFactory
import korrektor.viewer.PagePoint
import korrektor.viewer.ProjectViewer
import korrektor.viewer.ScanLinks
import korrektor.viewer.reveal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
            Column(if (model.project != null) Modifier.shortcutKeys(keys) else Modifier) {
                model.error?.let { ErrorBanner(message = it, onDismiss = model::dismissError) }
                model.notice?.let { NoticeBanner(message = it, onDismiss = model::dismissNotice) }
                SaveWarning(model.saver)
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
                                val viewer = checkNotNull(model.viewer)
                                val editor = model.editor
                                // Only what changes the scan is read here, so typing does not recompose it.
                                val highlight by remember(
                                    editor,
                                    viewer,
                                ) { derivedStateOf { editor?.highlightFor(viewer.page) } }
                                val onClick =
                                    remember(editor, viewer) {
                                        { point: PagePoint ->
                                            if (editor != null) editor.clickOnScan(viewer.page, point)
                                        }
                                    }
                                val links =
                                    ScanLinks(highlight, onClick, projectKeys::goToPage, projectKeys::leavePageField)
                                // The scan follows the cursor: a line that is out of view is brought into it.
                                LaunchedEffect(links.highlight) { links.highlight?.let { viewer.reveal(it.box) } }
                                ProjectViewer(project, viewer, Modifier.fillMaxSize(), DefaultPageSourceFactory, links)
                            },
                            modifier = room,
                            right = { TextPanel(checkNotNull(model.editor), Modifier.fillMaxSize()) },
                        )
                    }
                }
            }
            if (model.newProjectOpen) NewProjectDialog(model, pdfPicker, folderPicker)
            if (model.editMetadataOpen) EditMetadataDialog(model)
            if (model.shortcutsOpen) ShortcutListDialog { model.shortcutsOpen = false }
        }
    }
}
