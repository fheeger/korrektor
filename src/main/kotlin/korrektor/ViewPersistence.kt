package korrektor

import androidx.compose.runtime.snapshotFlow
import korrektor.viewer.saved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext

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
 * view as it was, causes no write. The write happens on the IO threads. Does not return: run it from an effect
 * that is cancelled when the viewer changes.
 */
suspend fun AppModel.keepViewSaved(delayMs: Long = VIEW_SAVE_DELAY_MS) {
    val open = project ?: return
    val view = viewer ?: return
    // The first value is the view as it is now. Dropping it (not filtering equal values) means that a change and a
    // return to the starting view within the delay writes the starting view, not the change between.
    snapshotFlow { view.saved() }
        .drop(1)
        .collectLatest {
            delay(delayMs)
            // The write waits for the disk (an fsync), so it is not done on the UI thread.
            withContext(Dispatchers.IO) { settings.rememberView(open.folder, it) }
        }
}
