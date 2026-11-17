package korrektor

import korrektor.editor.BookEditor
import korrektor.editor.HistoryRecovery
import korrektor.editor.SaveOutcome
import korrektor.editor.unsaved
import korrektor.format.FormatException
import korrektor.format.HistoryCodec
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.format.ProjectLock
import korrektor.format.ProjectLockedException
import korrektor.format.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

private const val MAX_ROUNDS = 5

/**
 * Closes the open project and gives its lock back, so that it can be opened again (also in this app). The view is
 * remembered. What is not saved yet is not handled here: the caller flushes first (`requestClose`, S07).
 */
fun AppModel.closeProject() {
    saveView()
    lock?.close()
    lock = null
    project = null
    viewer = null
    editor = null
    saver = null
    metadata = null
}

/**
 * The window is to be closed (S07). The corrections are saved first, without asking; then the app exits
 * ([AppModel.exit]). If saving fails the app stays open and [AppModel.closeProblem] says why: the user is warned and
 * offered to save the project somewhere else ([saveElsewhere]) or to close without saving. Does nothing while a
 * project is being made (it must not be left half-made) or while another close request is working. Returns whether
 * the app is closing.
 */
suspend fun AppModel.requestClose(): Boolean {
    if (!canClose || closing) return false
    closing = true
    try {
        var outcome = saver?.saveNow() ?: SaveOutcome.Done
        // Typing goes on while the save runs: what was changed meanwhile is saved too, before the app exits.
        var rounds = 0
        while (outcome is SaveOutcome.Done && editor?.changedPages?.isNotEmpty() == true && rounds++ < MAX_ROUNDS) {
            outcome = saver?.saveNow() ?: SaveOutcome.Done
        }
        if (outcome is SaveOutcome.Failed) {
            val pages = editor?.changedPages?.size ?: 0
            val count = if (pages == 1) "1 page" else "$pages pages"
            closeProblem = "The changes of $count could not be saved: ${outcome.reason}"
        } else {
            saveView()
            exit()
        }
        return outcome !is SaveOutcome.Failed
    } finally {
        closing = false
    }
}

/**
 * Before another project is opened: saves the changes of the open one, which is about to be closed. Returns false, with
 * [AppModel.error] saying why and the open project and its changes untouched, if that failed.
 */
internal fun AppModel.flushBeforeSwitch(): Boolean {
    val outcome = saver?.let { runBlocking { it.saveNow() } }
    if (outcome is SaveOutcome.Failed) {
        error =
            "The open project cannot be closed yet: its changes could not be saved (${outcome.reason}). They are kept."
    }
    return outcome !is SaveOutcome.Failed
}

/** Locks [created], a project that was just made, and opens it. Returns false, with [AppModel.error] set, if not. */
internal fun AppModel.openLocked(created: Project): Boolean =
    try {
        open(created, ProjectLock.acquire(created.folder))
        true
    } catch (e: ProjectLockedException) {
        error = e.message
        false
    } catch (e: FormatException) {
        error = e.message
        false
    }

/**
 * Saves the project to a new folder inside [parent]: the whole project is copied (`Project.copyTo`), the changes that
 * could not be saved are written into the copy, and the copy is opened in place of the project. The old folder is left
 * as it is. Returns whether it worked; if not, [AppModel.closeProblem] says why and nothing has changed.
 */
suspend fun AppModel.saveElsewhere(parent: Path): Boolean {
    val open = project
    if (open == null || copying) return false // (copying: the button was pressed again while the copy is made)
    copying = true
    try {
        return copyAndOpen(open, parent)
    } finally {
        copying = false
    }
}

private fun isInside(
    parent: Path,
    folder: Path,
): Boolean =
    try {
        parent.toRealPath().startsWith(folder.toRealPath())
    } catch (_: IOException) {
        false
    }

private suspend fun AppModel.copyAndOpen(
    open: Project,
    parent: Path,
): Boolean {
    val target = freeFolderIn(parent, open.folder.fileName.toString())
    val problem =
        if (isInside(parent, open.folder)) {
            "${open.folder.fileName} cannot be saved into itself: choose a folder outside of it"
        } else {
            // No save may run meanwhile: it would add to the history that is being copied.
            val idle = saver
            if (idle != null) {
                idle.whileIdle { copyWithChanges(open, target, editor) }
            } else {
                copyWithChanges(open, target, editor)
            }
        }
    if (problem == null) {
        closeProject()
        closeProblem = null
        openProject(target)
        if (error == null) notice = "Your changes were saved in a copy of the project: $target. The copy is open now."
    } else {
        target.toFile().deleteRecursively() // it did not exist before
        closeProblem = "The project could not be saved to another location either: $problem"
    }
    return problem == null
}

/**
 * Copies [open] to [target], and writes the unsaved pages of [editor] and its pending history entries into the copy
 * (the entries first, as a save does), so that the copy's pages and history agree and what was typed can be undone
 * there. Returns why it failed, or null.
 */
private suspend fun copyWithChanges(
    open: Project,
    target: Path,
    editor: BookEditor?,
): String? =
    try {
        withContext(Dispatchers.IO) {
            open.copyTo(target)
            val copy = Project.open(target)
            // The layers and the entries they contain are taken together, as for a save (but not drained).
            val (unsaved, entries) =
                editor?.let { synchronized(it.changedLock) { it.unsaved() to it.history.pendingEntries } }
                    ?: (emptyMap<Int, PageLayer>() to emptyList())
            if (entries.isNotEmpty()) copy.historyLog.appendAll(entries.map(HistoryCodec::encode))
            for ((page, layer) in unsaved) copy.writePage(page, layer)
            HistoryRecovery.recover(copy) // nothing to repair; it adds the `saved` entry
        }
        null
    } catch (e: FormatException) {
        e.message ?: "unknown error"
    } catch (e: IOException) {
        e.message ?: "unknown error"
    }

/** A folder in [parent] that does not exist: [name], or [name] with " (copy)", " (copy 2)", … before `.korrektur`. */
private fun freeFolderIn(
    parent: Path,
    name: String,
): Path {
    val base = name.removeSuffix(".korrektur")
    return generateSequence(0) { it + 1 }
        .map { n ->
            val suffix =
                when (n) {
                    0 -> ""
                    1 -> " (copy)"
                    else -> " (copy $n)"
                }
            parent.resolve("$base$suffix.korrektur")
        }.first { !Files.exists(it) }
}
