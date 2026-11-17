package korrektor.editor

import korrektor.format.Box
import korrektor.format.Change
import korrektor.format.EditEntry
import korrektor.format.HistoryCodec
import korrektor.format.HistoryLog
import korrektor.format.Line
import korrektor.format.LineChange
import korrektor.format.SavedEntry
import korrektor.format.Word
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The history of a project is read from its file when it is opened, and further back on demand (T073). */
class UndoHistoryLoadTest {
    private fun withLog(block: (HistoryLog, Path) -> Unit) {
        val dir = Files.createTempDirectory("historyload")
        try {
            val file = dir.resolve("history").resolve("book.jsonl")
            block(HistoryLog(file), file)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun line(text: String) =
        Line("l1", Box(1.0, 2.0, 3.0, 4.0), "l1", listOf(Word(text, Box(1.0, 2.0, 3.0, 4.0))))

    private fun change(k: Int): Change = LineChange(1 + k % 5, "r1", k % 3, listOf(line("a$k")), listOf(line("b$k")))

    /** A session that can be driven by random operations and written to a history file. */
    private class Session(
        val history: UndoHistory,
    ) {
        var k = 0
        var edits = 0

        fun edit() = history.record(listOf(change(++k)))

        fun undo(): Boolean {
            val step = history.peek(true) ?: return false
            history.commit(true, step.changes.reversed().map(Change::inverted))
            return true
        }

        fun redo(): Boolean {
            val step = history.peek(false) ?: return false
            history.commit(false, step.changes)
            return true
        }

        fun randomOp(
            random: Random,
            undoShare: Double,
        ) {
            val r = random.nextDouble()
            when {
                r < undoShare -> undo()
                r < undoShare + (1 - undoShare) / 3 -> redo()
                else -> edit()
            }
        }

        private fun change(n: Int): Change =
            LineChange(
                1 + n % 5,
                "r1",
                n % 3,
                listOf(Line("l1", null, null, listOf(Word("a$n")))),
                listOf(Line("l1", null, null, listOf(Word("b$n")))),
            )
    }

    private fun HistoryLog.write(
        history: UndoHistory,
        saved: Boolean = false,
    ) {
        val entries = history.drainPending()
        appendAll(entries.map(HistoryCodec::encode))
        if (saved &&
            entries.isNotEmpty()
        ) {
            append(HistoryCodec.encode(SavedEntry(entries.maxOf { (it as? EditEntry)?.seq ?: 0 })))
        }
    }

    private fun UndoHistory.state() = undoSteps.map { it.seq to it.changes } to redoSteps.map { it.seq to it.changes }

    @Test
    fun aLoadedHistoryHasTheStacksOfTheSessionThatWroteIt() {
        for (seed in 1..40) {
            withLog { log, _ ->
                val random = Random(seed)
                val live = Session(UndoHistory())
                repeat(random.nextInt(10, 90)) { live.randomOp(random, undoShare = 0.3) }
                log.write(live.history, saved = seed % 2 == 0)

                val loaded = UndoHistory.load(log)

                // Everything of a short history; the last 100 steps (and more of the block) of a long one.
                val (liveUndo, liveRedo) = live.history.state()
                val (loadedUndo, loadedRedo) = loaded.state()
                assertEquals(liveRedo, loadedRedo, "redo steps, seed $seed")
                assertEquals(liveUndo.takeLast(loadedUndo.size), loadedUndo, "undo steps, seed $seed")
                assertTrue(loadedUndo.size >= minOf(liveUndo.size, UndoHistory.HISTORY_WINDOW), "seed $seed")
                assertEquals(live.history.canUndo, loaded.canUndo)
                assertEquals(live.history.canRedo, loaded.canRedo)
            }
        }
    }

    @Test
    fun aLoadedHistoryGoesOnLikeTheSessionThatWroteIt() {
        for (seed in 1..40) {
            withLog { log, _ ->
                val random = Random(seed)
                val live = Session(UndoHistory())
                repeat(random.nextInt(20, 160)) { live.randomOp(random, undoShare = 0.25) }
                log.write(live.history)
                val loaded = Session(UndoHistory.load(log)).also { it.k = live.k }

                // The same operations on both: the steps on top are the same, and so are the numbers of new entries.
                repeat(250) {
                    val r = random.nextDouble()
                    when {
                        r < 0.55 -> assertEquals(live.undo(), loaded.undo(), "undo, seed $seed")
                        r < 0.7 -> assertEquals(live.redo(), loaded.redo(), "redo, seed $seed")
                        else -> assertEquals(live.edit(), loaded.edit(), "the number of a new entry, seed $seed")
                    }
                    assertEquals(
                        live.history.undoSteps
                            .lastOrNull()
                            ?.seq,
                        loaded.history.undoSteps
                            .lastOrNull()
                            ?.seq,
                    )
                    assertEquals(live.history.redoSteps.map { it.seq }, loaded.history.redoSteps.map { it.seq })
                    assertEquals(live.history.canUndo, loaded.history.canUndo, "seed $seed")
                }
            }
        }
    }

    @Test
    fun undoGoesBackToTheFirstStepEverMadeAlsoOverStepsThatCannotBeRedone() {
        withLog { log, _ ->
            val random = Random(7)
            val live = Session(UndoHistory())
            repeat(350) { n ->
                live.edit()
                // Now and then two steps are undone and a new one made: the two are dead (they cannot be redone).
                if (n % 7 == 6) {
                    live.undo()
                    live.undo()
                    live.edit()
                }
                if (random.nextInt(5) == 0) log.write(live.history, saved = true)
            }
            log.write(live.history)
            val expected = live.history.undoSteps.map { it.seq }

            val loaded = Session(UndoHistory.load(log))
            val undone = mutableListOf<Int>()
            while (true) {
                val step = loaded.history.peek(true) ?: break
                undone += step.seq
                loaded.undo()
            }
            assertEquals(expected.reversed(), undone)
            assertFalse(loaded.history.canUndo)
            assertTrue(undone.size > UndoHistory.HISTORY_WINDOW * 2, "only ${undone.size} steps")
        }
    }

    @Test
    fun aHistoryOf100000StepsIsReadOnlyAtItsEnd() {
        withLog { log, file ->
            val steps = 100_000
            val k = Session(UndoHistory())
            repeat(10) {
                repeat(steps / 10) { k.edit() }
                log.write(k.history)
            }
            val total = file.readBytes().size
            val reader = HistoryLog(file)

            val loaded = UndoHistory.load(reader)

            assertTrue(reader.bytesRead < total / 100, "read ${reader.bytesRead} of $total bytes")
            assertTrue(loaded.undoSteps.size in UndoHistory.HISTORY_WINDOW..UndoHistory.HISTORY_WINDOW + 20)
            assertEquals(steps, loaded.undoSteps.last().seq)
            assertEquals(emptyList(), loaded.redoSteps)
            // The next entry goes on from the file.
            assertEquals(steps + 1, loaded.record(listOf(change(1))))
        }
    }

    @Test
    fun aTornLastLineIsIgnoredAndTheNextEntryGetsTheNextNumber() {
        withLog { log, file ->
            val live = Session(UndoHistory())
            repeat(5) { live.edit() }
            log.write(live.history)
            file.toFile().appendBytes("{\"changes\": [{\"ind".toByteArray())

            val loaded = UndoHistory.load(log)

            assertEquals(listOf(1, 2, 3, 4, 5), loaded.undoSteps.map { it.seq })
            assertEquals(6, loaded.record(listOf(change(9))))
            // The torn line was cut off by the append that follows; the history reads again.
            log.write(loaded)
            assertEquals(listOf(1, 2, 3, 4, 5, 6), UndoHistory.load(log).undoSteps.map { it.seq })
        }
    }

    @Test
    fun aProjectWithoutAHistoryStartsEmpty() {
        withLog { log, _ ->
            val loaded = UndoHistory.load(log)
            assertFalse(loaded.canUndo)
            assertFalse(loaded.canRedo)
            assertNull(loaded.peek(true))
            assertEquals(1, loaded.record(listOf(change(1))))
        }
    }

    @Test
    fun theSavedEntryAtTheEndGivesTheNumberToContinueFrom() {
        withLog { log, _ ->
            val live = Session(UndoHistory())
            repeat(3) { live.edit() }
            log.write(live.history, saved = true)
            assertEquals(4, UndoHistory.load(log).record(listOf(change(1))))
        }
    }

    @Test
    fun aDamagedEntryIsAnErrorNotASkippedStep() {
        withLog { log, file ->
            val live = Session(UndoHistory())
            repeat(3) { live.edit() }
            log.write(live.history)
            val lines = file.toFile().readLines()
            file.toFile().writeText(
                lines.joinToString("\n", postfix = "\n") {
                    if (it.contains("\"seq\": 2,")) "{\"seq\": 2}" else it
                },
            )
            val e = kotlin.test.assertFailsWith<korrektor.format.FormatException> { UndoHistory.load(log) }
            assertTrue("History entry" in e.message!!, e.message)
        }
    }
}
