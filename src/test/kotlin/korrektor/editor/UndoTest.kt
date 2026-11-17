package korrektor.editor

import korrektor.format.Box
import korrektor.format.Line
import korrektor.format.LineChange
import korrektor.format.PageLayer
import korrektor.format.RedoEntry
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.UndoEntry
import korrektor.format.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Undo and redo in the editor (T069). */
class UndoTest {
    private fun line(
        id: String,
        text: String,
    ) = Line(
        id,
        Box(10.0, 10.0, 100.0, 10.0),
        id,
        text.split(' ').mapIndexed { i, w -> Word(w, Box(10.0 + i * 30, 10.0, 28.0, 10.0)) },
    )

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
                    listOf(line("l1", "Von der $number"), line("l2", "Kriegsfüh-"), line("l3", "rung")),
                ),
            ),
        )

    private val disk = (1..3).associateWith { layer(it) } + (4 to PageLayer(4, 300.0, 200.0, emptyList()))
    private var unreadable = emptySet<Int>()
    private var time = 0L
    private var unsavedCalls = 0

    private val editor =
        BookEditor(
            { n -> if (n in unreadable) error("page $n is gone") else disk.getValue(n) },
            Dispatchers.Unconfined,
            now = { time },
        ).also {
            it.onUnsaved = { unsavedCalls++ }
            runBlocking { it.show(1) }
        }

    private fun show(page: Int) = runBlocking { editor.show(page) }

    private fun PageLayer.plain() = copy(history = null)

    /** The layer of [page] as the session has it: changed, or as on disk. */
    private fun current(page: Int): PageLayer = (editor.changed[page] ?: disk.getValue(page)).plain()

    private fun BookEditor.texts() = lines.map { it.text }

    /** The page after [edit], after its undo and after its redo; the undo gives back the page as it was. */
    private fun assertRoundTrip(edit: () -> Unit) {
        val before = editor.layer!!.plain()
        edit()
        val after = editor.layer!!.plain()
        assertEquals(UndoResult.Done::class, editor.undo()::class)
        assertEquals(before, editor.layer!!.plain())
        assertEquals(UndoResult.Done::class, editor.redo()::class)
        assertEquals(after, editor.layer!!.plain())
        editor.undo()
        assertEquals(before, editor.layer!!.plain())
    }

    @Test
    fun everyKindOfEditIsUndoneAndRedoneExactly() {
        assertRoundTrip { editor.setLineText("l1", "Von dem 1") }
        assertRoundTrip { editor.setLineText("l1", "ganz anders und länger") }
        assertRoundTrip { editor.splitLine("l2", 5) }
        assertRoundTrip { editor.joinWithPrevious("l3") }
        assertRoundTrip { editor.joinWithNext("l1") }
        assertRoundTrip { editor.addLineAfter("l1", "neu") }
        assertRoundTrip { editor.addLineAfter(null, "am Ende") }
        assertRoundTrip { editor.deleteLine("l2") }
        assertRoundTrip {
            editor.oneStep {
                editor.setLineText("l1", "Von")
                editor.splitLine("l1", 3)
            }
        }
    }

    @Test
    fun aPageWithoutRegionsIsGivenBackWithoutOne() {
        show(4)
        assertRoundTrip { editor.addLineAfter(null, "erste") }
        assertEquals(emptyList(), editor.layer!!.regions)
    }

    @Test
    fun undoUndoRedoGoesBackToTheStateAfterTheFirstUndoAndANewEditClearsTheRedoSteps() {
        editor.setLineText("l1", "eins 1")
        editor.setLineText("l2", "zwei")
        editor.setLineText("l3", "drei")
        editor.undo()
        editor.undo()
        assertEquals(listOf("eins 1", "Kriegsfüh-", "rung"), editor.texts())
        editor.redo()
        assertEquals(listOf("eins 1", "zwei", "rung"), editor.texts())
        assertTrue(editor.history.canRedo)
        editor.setLineText("l3", "vier") // a new edit
        assertFalse(editor.history.canRedo)
        assertEquals(emptyList(), editor.history.redoSteps)
        assertEquals(UndoResult.NothingToDo, editor.redo())
        assertEquals(listOf("eins 1", "zwei", "vier"), editor.texts())
    }

    @Test
    fun nothingToUndoDoesNothing() {
        assertEquals(UndoResult.NothingToDo, editor.undo())
        assertEquals(UndoResult.NothingToDo, editor.redo())
        assertEquals(emptySet(), editor.changedPages)
        assertEquals(0, unsavedCalls)
    }

    @Test
    fun theLastChangeOnAnotherPageIsUndoneThereAndTheCursorGoesToIt() {
        show(2)
        editor.setLineText("l2", "Krieg")
        show(1)
        val result = editor.undo()
        assertEquals(UndoResult.Done(2, Focus("l2", 10)), result)
        assertEquals(disk.getValue(2), current(2), "page 2 is as it was")
        assertEquals(disk.getValue(1), editor.layer!!, "the page shown is not touched")
        // The caller shows the page; the cursor goes to the change when it is there.
        show(2)
        assertEquals(Focus("l2", 10), editor.focusRequest?.focus)
        assertEquals("l2", editor.activeLineId)
    }

    @Test
    fun theWishForTheCursorBelongsToItsPage() {
        show(2)
        editor.setLineText("l2", "Krieg")
        show(1)
        editor.undo()
        show(3) // not the page of the change: the wish is gone
        assertEquals(null, editor.focusRequest)
        show(2)
        assertEquals(null, editor.focusRequest)
    }

    @Test
    fun aPageThatWasNeverShownInThisSessionIsChangedFromTheProject() {
        val before = disk.getValue(3)
        val after = PageEdits.setLineText(before, "l1", "neu 3").layer
        // The step was made on page 3 without showing it, for example by a "replace all".
        editor.recordStep(diff(3, before, after), mapOf(3 to after))
        assertEquals(setOf(3), editor.changedPages)
        editor.undo()
        assertEquals(before, current(3))
        editor.redo()
        assertEquals(after.plain(), current(3))
    }

    @Test
    fun aStepOfSeveralPagesIsUndoneAndRedoneOnAllOfThem() {
        val change1 = PageEdits.setLineText(disk.getValue(1), "l2", "eins").layer
        val change2 = PageEdits.setLineText(disk.getValue(2), "l2", "zwei").layer
        editor.recordStep(
            diff(1, disk.getValue(1), change1) + diff(2, disk.getValue(2), change2),
            mapOf(
                1 to change1,
                2 to change2,
            ),
        )
        editor.undo()
        assertEquals(disk.getValue(1), current(1))
        assertEquals(disk.getValue(2), current(2))
        assertEquals(disk.getValue(1), editor.layer!!.plain(), "the page shown is changed too")
        editor.redo()
        assertEquals(change1.plain(), current(1))
        assertEquals(change2.plain(), current(2))
        assertEquals(change1.plain(), editor.layer!!.plain())
    }

    @Test
    fun theCursorGoesWhereTheChangeWas() {
        // typing: after the restored text, after the typed text
        editor.setLineText("l1", "Von der 1!")
        assertEquals(UndoResult.Done(1, Focus("l1", 9)), editor.undo())
        assertEquals(UndoResult.Done(1, Focus("l1", 10)), editor.redo())
        editor.undo()
        // a split: undoing it, the cursor is at the cut; redoing it, at the start of the second part
        editor.splitLine("l2", 5)
        assertEquals(UndoResult.Done(1, Focus("l2", 5)), editor.undo())
        assertEquals(UndoResult.Done(1, Focus("l4", 0)), editor.redo())
        editor.undo()
        // a join: undoing it, at the start of the second part; redoing it, at the junction
        editor.joinWithPrevious("l3")
        assertEquals(UndoResult.Done(1, Focus("l3", 0)), editor.undo())
        assertEquals(UndoResult.Done(1, Focus("l2", 10)), editor.redo())
        editor.undo()
        // add: undoing it, at the end of the line above; redoing, at the start of the new line
        editor.addLineAfter("l1", "neu")
        assertEquals(UndoResult.Done(1, Focus("l1", 9)), editor.undo())
        assertEquals(UndoResult.Done(1, Focus("l4", 0)), editor.redo())
        editor.undo()
        // delete: undoing it, at the start of the restored line; redoing it, at the end of the line above
        editor.deleteLine("l2")
        assertEquals(UndoResult.Done(1, Focus("l2", 0)), editor.undo())
        assertEquals(UndoResult.Done(1, Focus("l1", 9)), editor.redo())
    }

    @Test
    fun theCursorIsPutInTheLineOnThePageShown() {
        editor.setLineText("l1", "Von der 1!")
        editor.undo()
        assertEquals(Focus("l1", 9), editor.focusRequest?.focus)
        assertEquals("l1", editor.activeLineId)
    }

    @Test
    fun undoAndRedoMakeThePagesUnsavedAndSaysSo() {
        editor.setLineText("l1", "Von der 1!")
        val calls = unsavedCalls
        editor.markSaved(1, editor.layer!!)
        assertEquals(emptySet(), editor.changedPages)
        editor.undo()
        assertEquals(setOf(1), editor.changedPages)
        assertEquals(calls + 1, unsavedCalls)
        editor.markSaved(1, editor.layer!!)
        editor.redo()
        assertEquals(setOf(1), editor.changedPages)
        assertEquals(calls + 2, unsavedCalls)
    }

    @Test
    fun undoAndRedoAreEntriesOfTheHistoryAndTheirLayersCarryTheirNumbers() {
        editor.setLineText("l1", "Von der 1!") // entry 1
        editor.undo() // entry 2
        assertEquals(2, editor.layer?.history)
        editor.redo() // entry 3
        assertEquals(3, editor.layer?.history)
        val entries = editor.history.pendingEntries
        assertEquals(3, entries.size)
        val undo = entries[1] as UndoEntry
        val redo = entries[2] as RedoEntry
        assertEquals(listOf(2, 1), listOf(undo.seq, undo.step))
        assertEquals(listOf(3, 1), listOf(redo.seq, redo.step))
        // Applying the changes of an entry forward is what the entry did.
        val typed = PageEdits.setLineText(disk.getValue(1), "l1", "Von der 1!").layer
        val undone = undo.changes.fold<_, PageLayer?>(typed) { l, c -> l?.let { apply(it, c as LineChange) } }
        assertEquals(disk.getValue(1), undone)
        assertEquals(typed, redo.changes.fold<_, PageLayer?>(undone) { l, c -> l?.let { apply(it, c as LineChange) } })
    }

    @Test
    fun aStepThatDoesNotFitThePageIsDroppedAndTheNextOneWorks() {
        editor.setLineText("l2", "zwei")
        editor.setLineText("l3", "drei")
        // The page was changed behind the editor's back.
        editor.changed[1] = PageEdits.setLineText(editor.layer!!, "l3", "etwas ganz anderes").layer
        editor.layer = editor.changed[1]
        assertEquals(UndoResult.DoesNotFit, editor.undo())
        assertEquals(1, editor.history.undoSteps.size)
        assertIs<UndoResult.Done>(editor.undo())
        assertEquals("Kriegsfüh-", editor.texts()[1])
    }

    @Test
    fun aPageThatCannotBeReadKeepsTheStepAndSaysWhy() {
        val before = disk.getValue(3)
        val after = PageEdits.setLineText(before, "l1", "neu 3").layer
        editor.recordStep(diff(3, before, after), mapOf(3 to after))
        editor.changed.remove(3) // as if the page were not in memory
        unreadable = setOf(3)
        val result = editor.undo()
        assertIs<UndoResult.Failed>(result)
        assertTrue(result.reason.contains("page 3 is gone"))
        assertTrue(editor.history.canUndo)
    }

    @Test
    fun typingAfterAnUndoIsAStepOfItsOwn() {
        editor.setLineText("l1", "Von der 1!")
        time += 10
        editor.undo()
        time += 10
        editor.setLineText("l1", "Von der 1?")
        assertEquals(1, editor.history.undoSteps.size)
        assertEquals(listOf("Von der 1?", "Kriegsfüh-", "rung"), editor.texts())
        assertFalse(editor.history.canRedo)
    }
}
