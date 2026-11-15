package korrektor.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import korrektor.format.Project

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
) {
    val source = remember(project) { pageSources.open(project.sourcePdf) }
    DisposableEffect(source) { onDispose { source.close() } }
    PageViewer(source, modifier, state)
}
