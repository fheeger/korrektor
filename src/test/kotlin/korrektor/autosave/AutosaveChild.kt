package korrektor.autosave

import korrektor.editor.AutoSaver
import korrektor.editor.BookEditor
import korrektor.format.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

/**
 * The process that `CrashTest` kills. It opens the project in the first argument, runs the real saver with the real
 * clock and keeps editing until it is killed, saying on its output what it did: "<counter> <time in ms>" per step.
 * Arguments: the project folder, the mode, the first counter.
 *
 * - `typing`: one line of page 1 is replaced every 50 ms by `edit <counter>`, in bursts of ten edits with a pause of
 *   1.2 s after each, as a person types: the pauses are when the saver writes.
 * - `continuous`: the same edits without a pause (the saver can only write because of its ten-second limit).
 * - `saving`: all pages are changed to `p<page> r<counter>`, a save is asked for, and again (so that most kills land
 *   in the middle of a save of many pages).
 */
fun main(args: Array<String>) {
    val project = Project.open(Path.of(args[0]))
    val mode = args[1]
    val first = args[2].toInt()
    val editor = BookEditor(project::readPage, Dispatchers.IO)
    val saver = AutoSaver(editor, Dispatchers.IO, write = project::writePage)
    editor.onUnsaved = saver::changed
    runBlocking {
        launch { saver.run() }
        when (mode) {
            "typing" -> type(editor, first, pauseEvery = BURST)
            "continuous" -> type(editor, first, pauseEvery = Int.MAX_VALUE)
            else -> changeAllPages(editor, saver, project.pages.size, first)
        }
    }
}

private suspend fun type(
    editor: BookEditor,
    first: Int,
    pauseEvery: Int,
) {
    editor.show(1)
    var counter = first
    while (true) {
        counter++
        editor.setLineText("l1", "edit $counter")
        report(counter)
        delay(if ((counter - first) % pauseEvery == 0) PAUSE_MS else TYPING_INTERVAL_MS)
    }
}

private suspend fun changeAllPages(
    editor: BookEditor,
    saver: AutoSaver,
    pages: Int,
    first: Int,
) {
    var round = first
    while (true) {
        round++
        for (page in 1..pages) {
            editor.show(page)
            editor.setLineText("l1", "p$page r$round")
        }
        saver.requestFlush()
        report(round)
        delay(ROUND_INTERVAL_MS)
    }
}

private fun report(counter: Int) {
    println("$counter ${System.currentTimeMillis()}")
    System.out.flush()
}

private const val TYPING_INTERVAL_MS = 50L
private const val PAUSE_MS = 1_200L
private const val BURST = 10
private const val ROUND_INTERVAL_MS = 20L
