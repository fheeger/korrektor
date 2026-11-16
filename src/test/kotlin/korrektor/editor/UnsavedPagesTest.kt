package korrektor.editor

import korrektor.format.Box
import korrektor.format.Line
import korrektor.format.PageLayer
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Which pages have corrections that are not on disk yet, and the callback for the saver (T057). */
class UnsavedPagesTest {
    private fun layer(
        number: Int,
        vararg texts: String,
    ) = PageLayer(
        number,
        300.0,
        200.0,
        listOf(
            Region(
                "r1",
                RegionType.TEXT,
                1,
                null,
                texts.mapIndexed { i, t ->
                    Line("l${i + 1}", Box(10.0, 10.0 + 14.0 * i, 130.0, 10.0), "l${i + 1}", listOf(Word(t)))
                },
            ),
        ),
    )

    private val pages = (1..3).associateWith { layer(it, "eins $it", "zwei $it") }

    private fun editor(shown: Int = 1) =
        BookEditor({ pages.getValue(it) }, Dispatchers.Unconfined).also { runBlocking { it.show(shown) } }

    @Test
    fun aNewEditorHasNothingUnsaved() {
        val editor = editor()
        assertEquals(emptyMap(), editor.unsaved())
        assertEquals(emptySet(), editor.changedPages)
    }

    @Test
    fun anEditMakesItsPageUnsavedAndAnEditThatChangesNothingDoesNot() {
        val editor = editor()
        editor.setLineText("l1", "eins 1") // the same text
        assertEquals(emptyMap(), editor.unsaved())
        editor.setLineText("l1", "geändert")
        assertEquals(setOf(1), editor.unsaved().keys)
        assertSame(editor.layer, editor.unsaved().getValue(1))
    }

    @Test
    fun unsavedHoldsTheCurrentLayerOfEveryUnsavedPageAndNothingElse() {
        val editor = editor()
        editor.setLineText("l1", "a")
        runBlocking { editor.show(2) }
        editor.setLineText("l2", "b")
        runBlocking { editor.show(3) } // looked at, not changed
        val unsaved = editor.unsaved()
        assertEquals(listOf(1, 2), unsaved.keys.toList())
        assertEquals("a", PageEdits.readingOrder(unsaved.getValue(1)).first().text)
        assertEquals("b", PageEdits.readingOrder(unsaved.getValue(2)).last().text)
    }

    @Test
    fun markSavedWithTheSnapshotLayerClearsThePage() {
        val editor = editor()
        editor.setLineText("l1", "a")
        val snapshot = editor.unsaved()
        editor.markSaved(1, snapshot.getValue(1))
        assertEquals(emptyMap(), editor.unsaved())
        assertEquals(emptySet(), editor.changedPages)
    }

    @Test
    fun aPageEditedWhileItWasBeingWrittenStaysUnsaved() {
        val editor = editor()
        editor.setLineText("l1", "a")
        val snapshot = editor.unsaved()
        editor.setLineText("l1", "ab") // typing goes on while the saver writes "a"
        editor.markSaved(1, snapshot.getValue(1))
        assertEquals(setOf(1), editor.changedPages)
        val now = editor.unsaved().getValue(1)
        assertNotSame(snapshot.getValue(1), now)
        assertEquals("ab", PageEdits.readingOrder(now).first().text)
        editor.markSaved(1, now)
        assertEquals(emptySet(), editor.changedPages)
    }

    @Test
    fun markSavedForAPageThatIsNotUnsavedDoesNothing() {
        val editor = editor()
        editor.markSaved(2, pages.getValue(2))
        editor.markSaved(1, pages.getValue(1))
        assertEquals(emptySet(), editor.changedPages)
    }

    @Test
    fun aPageChangedSavedAndChangedAgainIsUnsavedAgain() {
        val editor = editor()
        editor.setLineText("l1", "a")
        editor.markSaved(1, editor.unsaved().getValue(1))
        assertEquals(emptySet(), editor.changedPages)
        editor.setLineText("l1", "b")
        assertEquals(setOf(1), editor.changedPages)
    }

    @Test
    fun aSavedPageStaysInMemoryAndComesBackAsCorrected() {
        val editor = editor()
        editor.setLineText("l1", "a")
        editor.markSaved(1, editor.unsaved().getValue(1))
        runBlocking { editor.show(2) }
        runBlocking { editor.show(1) }
        assertEquals("a", editor.lines.first().text)
    }

    @Test
    fun theCallbackIsCalledOncePerEditThatChangedALayerOnEveryKindOfEdit() {
        val editor = editor()
        var calls = 0
        editor.onUnsaved = { calls++ }
        editor.setLineText("l1", "a")
        assertEquals(1, calls)
        editor.setLineText("l1", "a") // no change
        assertEquals(1, calls)
        editor.splitLine("l1", 0)
        editor.joinWithNext("l1")
        editor.addLineAfter("l1")
        editor.deleteLine("l2")
        assertEquals(5, calls)
        assertTrue(editor.changedPages.contains(1))
    }

    @Test
    fun showingAndActivatingDoNotCallTheCallback() {
        val editor = editor()
        var calls = 0
        editor.onUnsaved = { calls++ }
        runBlocking { editor.show(2) }
        editor.activate("l1")
        editor.requestFocus("l2", 0)
        editor.moveLine(1)
        assertEquals(0, calls)
    }
}
