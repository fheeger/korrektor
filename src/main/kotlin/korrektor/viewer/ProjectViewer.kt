package korrektor.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import korrektor.format.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The viewer of an open project: one [PageSource] for the project's own copy of the PDF, made when the project
 * appears and closed when it goes away, so that the PDF is not held open and the project folder can be deleted.
 */
@Composable
fun ProjectViewer(
    project: Project,
    state: ViewerState,
    modifier: Modifier = Modifier,
    pageSources: PageSourceFactory = DefaultPageSourceFactory,
    highlight: LineHighlight? = null,
) {
    val source = remember(project) { pageSources.open(project.sourcePdf) }
    DisposableEffect(source) { onDispose { source.close() } }
    PageViewer(source, modifier, state, remember(project) { projectBoxes(project) }, highlight)
}

/**
 * The boxes of the **original** OCR layer of a page, which never changes. The corrected layer is what the user edits:
 * an edited line has no word boxes, and the overlay must not change while the user types.
 */
private fun projectBoxes(project: Project) =
    PageBoxSource { page ->
        withContext(Dispatchers.IO) {
            val regions = project.readOriginalPage(page).regions
            val lines = regions.flatMap { it.lines }
            PageBoxes(lines.mapNotNull { it.box }, lines.flatMap { it.words }.mapNotNull { it.box })
        }
    }
