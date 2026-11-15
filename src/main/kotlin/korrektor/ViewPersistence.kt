package korrektor

import androidx.compose.runtime.snapshotFlow
import korrektor.viewer.saved
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

/** How long the view must stay as it is before it is saved. */
const val VIEW_SAVE_DELAY_MS = 1_000L

/**
 * Writes the view of the open project to the settings now. Called when another project is opened (by [AppModel]) and
 * when the window closes.
 */
fun AppModel.saveView() {
    val open = project ?: return
    val view = viewer ?: return
    settings.rememberView(open.folder, view.saved())
}

/**
 * Saves the view of the open project whenever page or zoom change, once [delayMs] have passed without another
 * change, so that a stream of changes (the wheel, a drag) is written once. A project that was only opened, with its
 * view as it was, causes no write. Does not return: run it from an effect that is cancelled when the viewer changes.
 */
suspend fun AppModel.keepViewSaved(delayMs: Long = VIEW_SAVE_DELAY_MS) {
    val open = project ?: return
    val view = viewer ?: return
    val initial = view.saved()
    snapshotFlow { view.saved() }
        .filter { it != initial }
        .collectLatest {
            delay(delayMs)
            settings.rememberView(open.folder, it)
        }
}
