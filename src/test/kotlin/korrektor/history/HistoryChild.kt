package korrektor.history

import korrektor.editor.AutoSaver
import korrektor.editor.BookEditor
import korrektor.editor.HistoryRecovery
import korrektor.editor.UndoHistory
import korrektor.editor.redo
import korrektor.editor.setStatus
import korrektor.editor.undo
import korrektor.format.HistoryCodec
import korrektor.format.PageStatus
import korrektor.format.Project
import korrektor.format.setStatuses
import korrektor.search.SearchModel
import korrektor.search.SearchQuery
import korrektor.search.replaceSelected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

/**
 * The process that `HistoryCrashTest` kills (S08). It opens the project in the first argument as the app does (the
 * history is repaired and loaded), runs the real saver with the history and keeps changing text on all pages, with
 * splits, added lines, page statuses, a replace on all pages (S10), undo and redo, until it is killed. It says
 * "<round> <time in ms>" for each round.
 * Arguments: the project folder, the first round.
 */
fun main(args: Array<String>) {
    val project = Project.open(Path.of(args[0]))
    val first = args[1].toInt()
    HistoryRecovery.recover(project)
    val editor =
        BookEditor(
            project::readPage,
            Dispatchers.IO,
            history = UndoHistory.load(project.historyLog),
            initialStatuses = project.statuses,
        )
    val saver =
        AutoSaver(
            editor,
            Dispatchers.IO,
            appendHistory = { entries -> project.historyLog.appendAll(entries.map(HistoryCodec::encode)) },
            writeStatuses = project::setStatuses,
            write = project::writePage,
        )
    editor.onUnsaved = saver::changed
    runBlocking {
        launch { saver.run() }
        var round = first
        while (true) {
            round++
            for (page in 1..project.pages.size) {
                editor.show(page)
                editor.setLineText("l1", "p$page r$round")
                if (page % 4 ==
                    0
                ) {
                    editor.setStatus(page, if (round % 2 == 0) PageStatus.CORRECTED else PageStatus.UNCHECKED)
                }
                if (round % 3 == 0) editor.splitLine("l1", 2)
                if (round % 5 == 0) editor.addLineAfter("l1", "neu $round")
                if (round % 4 == 0) {
                    editor.undo()
                    editor.redo()
                }
                if (round % 7 == 0) editor.undo()
            }
            if (round % REPLACE_EVERY == 0) replaceEverywhere(editor, project.pages.size)
            saver.requestFlush()
            println("$round ${System.currentTimeMillis()}")
            System.out.flush()
            delay(ROUND_INTERVAL_MS)
        }
    }
}

private const val ROUND_INTERVAL_MS = 15L
private const val REPLACE_EVERY = 6

/** A "replace all" over every page (S10): each `r` with the round number after it becomes `R`, in one step. */
private suspend fun replaceEverywhere(
    editor: BookEditor,
    pages: Int,
) {
    val search = SearchModel(editor, pages, Dispatchers.Default, Dispatchers.IO, pause = 0)
    search.change(SearchQuery("""r(\d+)""", regex = true, matchCase = true))
    while (search.searching) delay(2)
    search.replacement = "R$1"
    search.replaceSelected()
    search.close()
}
