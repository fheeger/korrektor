package korrektor.editor

import korrektor.format.Box
import korrektor.format.Line
import korrektor.format.PageLayer
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** When the saver saves: after a pause, regularly while typing, on a flush, and never without changes (T059). */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveTriggersTest {
    private fun layer(number: Int) =
        PageLayer(
            number,
            300.0,
            200.0,
            listOf(
                Region(
                    "r1",
                    RegionType.TEXT,
                    1,
                    null,
                    listOf(Line("l1", Box(10.0, 10.0, 100.0, 10.0), "l1", listOf(Word("text $number")))),
                ),
            ),
        )

    private class Rig(
        val editor: BookEditor,
        val saver: AutoSaver,
        val writes: MutableList<Pair<Long, Int>>,
        val job: Job,
    )

    /** An editor on page 1 and a saver that runs in virtual time and records when it writes which page. */
    private fun TestScope.rig(): Rig {
        val editor = BookEditor({ layer(it) }, StandardTestDispatcher(testScheduler))
        val writes = mutableListOf<Pair<Long, Int>>()
        val saver =
            AutoSaver(editor, StandardTestDispatcher(testScheduler), now = { currentTime }) { page, _ ->
                writes += currentTime to page
            }
        editor.onUnsaved = saver::changed
        val job = backgroundScope.launch { saver.run() }
        return Rig(editor, saver, writes, job)
    }

    private suspend fun BookEditor.open(page: Int = 1) = show(page)

    private fun BookEditor.type(text: String) = setLineText("l1", text)

    @Test
    fun oneEditIsWrittenASecondLater() =
        runTest {
            val rig = rig()
            rig.editor.open()
            runCurrent()
            rig.editor.type("a")
            runCurrent()
            delay(999)
            assertEquals(emptyList(), rig.writes, "not before a second")
            delay(1)
            runCurrent()
            assertEquals(listOf(1000L to 1), rig.writes)
            assertEquals(emptySet(), rig.editor.changedPages)
        }

    @Test
    fun continuousTypingIsWrittenAtLeastEveryTenSeconds() =
        runTest {
            val rig = rig()
            rig.editor.open()
            val changes = mutableListOf<Long>()
            var count = 0
            while (currentTime < 30_000) {
                rig.editor.type("text ${count++}")
                changes += currentTime
                delay(500)
            }
            delay(1_100) // typing stops: the rest is written a second later
            runCurrent()
            val times = rig.writes.map { it.first }
            assertEquals(10_000L, times.first(), "the first write is ten seconds after the first change")
            assertTrue(times.size >= 3, "written at least three times in 30 s: $times")
            // At every save, the oldest change that was not saved yet is at most ten seconds old.
            var previousSave = -1L
            for (save in times) {
                val oldestUnsaved = changes.first { it > previousSave }
                assertTrue(save - oldestUnsaved <= 10_000, "save at $save, oldest change $oldestUnsaved")
                previousSave = save
            }
        }

    @Test
    fun aPauseOfASecondEndsTheWait() =
        runTest {
            val rig = rig()
            rig.editor.open()
            rig.editor.type("a")
            delay(900)
            rig.editor.type("ab") // the timer starts again
            delay(900)
            assertEquals(emptyList(), rig.writes, "still waiting, 900 ms after the last edit")
            delay(100)
            runCurrent()
            assertEquals(listOf(1900L to 1), rig.writes, "one write, a second after the last edit")
        }

    @Test
    fun aFlushWritesAtOnce() =
        runTest {
            val rig = rig()
            rig.editor.open()
            rig.editor.type("a")
            runCurrent()
            rig.saver.requestFlush()
            runCurrent()
            assertEquals(listOf(0L to 1), rig.writes)
        }

    @Test
    fun aFlushWithNothingUnsavedWritesNothing() =
        runTest {
            val rig = rig()
            rig.editor.open()
            rig.saver.requestFlush()
            runCurrent()
            delay(30_000)
            assertEquals(emptyList(), rig.writes)
        }

    @Test
    fun withoutChangesNothingIsEverWritten() =
        runTest {
            val rig = rig()
            rig.editor.open()
            rig.editor.open(2)
            delay(60_000)
            assertEquals(emptyList(), rig.writes, "looking at pages is not changing them")
        }

    @Test
    fun severalPagesChangedWithinTheWaitAreWrittenTogether() =
        runTest {
            val rig = rig()
            rig.editor.open(1)
            rig.editor.type("a")
            delay(300)
            rig.editor.open(2)
            rig.editor.type("b")
            delay(300)
            rig.editor.open(3)
            rig.editor.type("c")
            delay(1100)
            runCurrent()
            assertEquals(listOf(1, 2, 3), rig.writes.map { it.second })
            assertEquals(
                1,
                rig.writes
                    .map { it.first }
                    .distinct()
                    .size,
                "in one save",
            )
        }

    @Test
    fun aChangeDuringASaveIsWrittenByTheNextOne() =
        runTest {
            val editor = BookEditor({ layer(it) }, StandardTestDispatcher(testScheduler))
            val writes = mutableListOf<Long>()
            lateinit var saver: AutoSaver
            saver =
                AutoSaver(editor, StandardTestDispatcher(testScheduler), now = { currentTime }) { _, _ ->
                    writes += currentTime
                    editor.type("typed while saving") // the user goes on typing during the write
                }
            editor.onUnsaved = saver::changed
            val scope: CoroutineScope = backgroundScope
            scope.launch { saver.run() }
            editor.open()
            editor.type("a")
            delay(1500)
            runCurrent()
            assertEquals(listOf(1000L), writes)
            delay(1000)
            runCurrent()
            assertEquals(2, writes.size, "the text typed during the first save is written by the second")
        }
}
