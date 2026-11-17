package korrektor.editor

import korrektor.format.PageStatus
import korrektor.format.StatusChange

// The status of the pages in the editor (S09). Extensions, because `BookEditor` has as many functions as the code
// checks allow. A change of status is a step of the undo history like any other (S08).

/** The status of [page]: unchecked unless it was marked. */
fun BookEditor.statusOf(page: Int): PageStatus = statuses[page] ?: PageStatus.UNCHECKED

/** Whether anything is not saved: a page with corrections or the statuses. */
val BookEditor.hasUnsaved: Boolean get() = changedPages.isNotEmpty() || statusesChanged

/**
 * Gives [page] the status [status], as one step of the history. Nothing happens if it has that status already. The
 * statuses become unsaved; the step is on the history like a correction, so it can be undone, also after a restart.
 */
fun BookEditor.setStatus(
    page: Int,
    status: PageStatus,
) {
    synchronized(changedLock) {
        val old = statusOf(page)
        if (old == status) return
        history.record(listOf(StatusChange(page, old, status)))
        putStatus(page, status)
    }
    onUnsaved?.invoke()
}

/** Makes [status] the status of [page] and the statuses unsaved. The step must be recorded under the lock. */
internal fun BookEditor.putStatus(
    page: Int,
    status: PageStatus,
) {
    synchronized(changedLock) {
        statuses = if (status == PageStatus.UNCHECKED) statuses - page else statuses + (page to status)
        statusesChanged = true
    }
}

/** The statuses if they have changes that are not saved, else null. For the snapshot of a save, under the lock. */
internal fun BookEditor.unsavedStatuses(): Map<Int, PageStatus>? = if (statusesChanged) statuses else null

/**
 * Says that [saved] (from [unsavedStatuses]) is in `project.json`. The statuses count as saved only if [saved] is
 * still the current map: if the user changed a status while it was written, they stay unsaved. Called by the saver.
 */
fun BookEditor.markStatusesSaved(saved: Map<Int, PageStatus>) {
    synchronized(changedLock) {
        if (statusesChanged && statuses === saved) statusesChanged = false
    }
}

/**
 * The next page after [page] that is unchecked, in a book of [count] pages, or null. A page with a status that this
 * version does not know is not unchecked. It looks at the statuses in memory, so a status that is not saved yet counts.
 */
fun BookEditor.uncheckedAfter(
    page: Int,
    count: Int,
): Int? = ((page + 1)..count).firstOrNull { statusOf(it) == PageStatus.UNCHECKED }

/** The nearest page before [page] that is unchecked, or null. */
fun BookEditor.uncheckedBefore(page: Int): Int? =
    (page - 1 downTo 1).firstOrNull { statusOf(it) == PageStatus.UNCHECKED }
