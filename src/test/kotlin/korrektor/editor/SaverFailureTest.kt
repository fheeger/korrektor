package korrektor.editor

import korrektor.format.Box
import korrektor.format.Line
import korrektor.format.PageLayer
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A failed save: the changes are kept, saving is retried, and the warning is one (T060). */
@OptIn(ExperimentalCoroutinesApi::class)
class SaverFailureTest {
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
        val attempts: MutableList<Long>,
        val written: MutableList<String>,
    ) {
        var failing = true
    }

    private fun TestScope.rig(): Rig {
        val editor = BookEditor({ layer(it) }, StandardTestDispatcher(testScheduler))
        val attempts = mutableListOf<Long>()
        val written = mutableListOf<String>()
        lateinit var rig: Rig
        val saver =
            AutoSaver(editor, StandardTestDispatcher(testScheduler), now = { currentTime }) { page, layer ->
                attempts += currentTime
                if (rig.failing) throw IOException("disk full")
                written += "$page:" + PageEdits.readingOrder(layer).first().text
            }
        rig = Rig(editor, saver, attempts, written)
        editor.onUnsaved = saver::changed
        backgroundScope.launch { saver.run() }
        return rig
    }

    @Test
    fun aFailedSaveKeepsTheChangesAndSetsTheWarning() =
        runTest {
            val rig = rig()
            rig.editor.show(1)
            rig.editor.setLineText("l1", "neu")
            delay(1_100)
            runCurrent()
            assertEquals(listOf(1_000L), rig.attempts)
            assertEquals(setOf(1), rig.editor.changedPages, "kept")
            assertEquals(
                "neu",
                rig.editor.lines
                    .first()
                    .text,
            )
            assertTrue(rig.saver.warning!!.contains("disk full"), rig.saver.warning)
        }

    @Test
    fun saveIsTriedAgainAfterFiveSecondsAndWorksOnceTheCauseIsGone() =
        runTest {
            val rig = rig()
            rig.editor.show(1)
            rig.editor.setLineText("l1", "neu")
            delay(1_100)
            runCurrent()
            rig.failing = false // the disk has room again
            delay(5_000)
            runCurrent()
            assertEquals(listOf(1_000L, 6_000L), rig.attempts)
            assertEquals(listOf("1:neu"), rig.written)
            assertEquals(emptySet(), rig.editor.changedPages)
            assertNull(rig.saver.warning, "the warning goes by itself")
        }

    @Test
    fun manyFailedRetriesAreOneWarningAndTheRetriesKeepComingEveryFiveSeconds() =
        runTest {
            val rig = rig()
            rig.editor.show(1)
            rig.editor.setLineText("l1", "neu")
            delay(26_000)
            runCurrent()
            assertEquals(listOf(1_000L, 6_000L, 11_000L, 16_000L, 21_000L, 26_000L), rig.attempts)
            assertNotNull(rig.saver.warning)
            assertEquals(setOf(1), rig.editor.changedPages)
        }

    @Test
    fun aChangeWhileSavingFailsIsKeptAndWrittenWithTheRestOnceItWorks() =
        runTest {
            val rig = rig()
            rig.editor.show(1)
            rig.editor.setLineText("l1", "erste")
            delay(1_100)
            runCurrent()
            rig.editor.show(2)
            rig.editor.setLineText("l1", "zweite") // while the saving is failing
            rig.failing = false
            delay(1_200) // the change starts the normal timer: a second, not five
            runCurrent()
            assertEquals(listOf("1:erste", "2:zweite"), rig.written)
            assertEquals(emptySet(), rig.editor.changedPages)
        }

    @Test
    fun dismissingHidesTheWarningUntilTheNextFailureAfterASuccess() =
        runTest {
            val rig = rig()
            rig.editor.show(1)
            rig.editor.setLineText("l1", "a")
            delay(1_100)
            runCurrent()
            assertNotNull(rig.saver.warning)
            rig.saver.dismissWarning()
            assertNull(rig.saver.warning)
            delay(5_000) // another failed retry: still dismissed
            runCurrent()
            assertNull(rig.saver.warning)
            assertNotNull(rig.saver.error, "the save is still failing")

            rig.failing = false
            delay(5_000)
            runCurrent()
            assertEquals(emptySet(), rig.editor.changedPages)

            rig.failing = true
            rig.editor.setLineText("l1", "b")
            delay(1_100)
            runCurrent()
            assertNotNull(rig.saver.warning, "a new failure shows the warning again")
        }

    @Test
    fun aFlushAfterAFailureTriesAtOnce() =
        runTest {
            val rig = rig()
            rig.editor.show(1)
            rig.editor.setLineText("l1", "a")
            delay(1_100)
            runCurrent()
            rig.failing = false
            rig.saver.requestFlush()
            runCurrent()
            assertEquals(listOf("1:a"), rig.written)
        }
}
