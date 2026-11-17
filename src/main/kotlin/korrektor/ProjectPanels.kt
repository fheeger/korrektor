package korrektor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import korrektor.editor.statusOf
import korrektor.format.Project
import korrektor.viewer.DefaultPageSourceFactory
import korrektor.viewer.PagePoint
import korrektor.viewer.ProjectViewer
import korrektor.viewer.ScanLinks
import korrektor.viewer.reveal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * What the open project shows below its header: the status bar with the progress (S09) and, side by side, the scan and
 * the text. [keys] are what the page field and a click on the scan tell.
 */
@Composable
fun ProjectPanels(
    model: AppModel,
    project: Project,
    keys: ProjectKeys,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        model.editor?.let { StatusBar(it, project.pages.size) }
        val scope = rememberCoroutineScope()
        SplitPane(
            model.layout,
            { scope.launch(Dispatchers.IO) { model.layout.save() } },
            left = { Scan(model, project, keys) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            right = { TextPanel(checkNotNull(model.editor), Modifier.fillMaxSize(), model.saver) },
        )
    }
}

/** The scan: the viewer of the project's PDF, linked to the text. */
@Composable
private fun Scan(
    model: AppModel,
    project: Project,
    keys: ProjectKeys,
) {
    val viewer = checkNotNull(model.viewer)
    val editor = model.editor
    // Only what changes the scan is read here, so typing does not recompose it.
    val highlight by remember(editor, viewer) { derivedStateOf { editor?.highlightFor(viewer.page) } }
    val onClick =
        remember(editor, viewer) {
            { point: PagePoint -> if (editor != null) editor.clickOnScan(viewer.page, point) }
        }
    val links =
        ScanLinks(
            highlight,
            onClick,
            keys::goToPage,
            keys::leavePageField,
            { page -> editor?.statusOf(page)?.name },
        )
    // The scan follows the cursor: a line that is out of view is brought into it.
    LaunchedEffect(links.highlight) { links.highlight?.let { viewer.reveal(it.box) } }
    ProjectViewer(project, viewer, Modifier.fillMaxSize(), DefaultPageSourceFactory, links)
}
