package korrektor.editor

import korrektor.format.Box
import korrektor.format.EditEntry
import korrektor.format.Line
import korrektor.format.LineChange
import korrektor.format.PageLayer
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The edits of the editor become steps of the undo history (T068). */
class RecordingTest {
    private fun line(
        id: String,
        text: String,
    ) = Line(
        id,
        Box(10.0, 10.0, 100.0, 10.0),
        id,
        text.split(' ').mapIndexed {
            i,
            w,
            ->
            Word(w, Box(10.0 + i * 30, 10.0, 28.0, 10.0))
        },
    )

    private fun page(vararg lines: Line) =
        PageLayer(1, 300.0, 200.0, listOf(Region("r1", RegionType.TEXT, 1, null, lines.toList())))

    private val start = page(line("l1", "Von der"), line("l2", "Kriegsfüh-"), line("l3", "rung"))
    private var time = 0L
    private val editor =
        BookEditor({ start }, Dispatchers.Unconfined, now = { time }).also { runBlocking { it.show(1) } }

    private fun BookEditor.texts() = lines.map { it.text }

    private fun BookEditor.steps() = history.undoSteps

    private fun type(text: String) {
        time += 100
        editor.setLineText("l1", text)
    }

    @Test
    fun everyKindOfEditMakesOneStep() {
        editor.setLineText("l1", "Von dem")
        editor.splitLine("l2", 5)
        editor.joinWithPrevious("l3")
        editor.addLineAfter("l1", "neu")
        editor.deleteLine("l1")
        assertEquals(5, editor.steps().size)
        assertEquals(listOf(1, 2, 3, 4, 5), editor.steps().map { it.seq })
        assertTrue(editor.steps().all { step -> step.changes.all { it is LineChange } })
    }

    @Test
    fun anEditThatChangesNothingMakesNoStep() {
        editor.setLineText("l1", "Von der")
        editor.joinWithPrevious("l1") // the first line has nothing to join to
        assertEquals(emptyList(), editor.steps())
        assertEquals(emptyList(), editor.history.pendingEntries)
        assertEquals(false, editor.history.canUndo)
    }

    @Test
    fun theChangesOfAStepSayWhatWasThereAndWhatIsThereNow() {
        editor.splitLine("l1", 3)
        val change =
            editor
                .steps()
                .single()
                .changes
                .single() as LineChange
        assertEquals(page(line("l1", "Von der")).regions[0].lines, change.removed)
        assertEquals(listOf("Von", "der"), change.inserted.map { it.text })
        assertEquals(0, change.index)
        assertEquals("r1", change.region)
    }

    @Test
    fun typingInOneWordIsOneStepFromTheLineBeforeToTheLineAfter() {
        type("Von derk")
        type("Von derke")
        type("Von derkei")
        val step = editor.steps().single()
        val change = step.changes.single() as LineChange
        assertEquals(listOf("Von der"), change.removed.map { it.text })
        assertEquals(listOf("Von derkei"), change.inserted.map { it.text })
        assertEquals(listOf("Von derkei", "Kriegsfüh-", "rung"), editor.texts())
    }

    @Test
    fun aSpaceAPauseAndAnotherLineStartANewStep() {
        type("Von der ") // a space after a word starts a step
        type("Von der K")
        type("Von der Ke")
        type("Von der Ke ") // the next space: a new step
        type("Von der Ke z")
        assertEquals(2, editor.steps().size)
        time += 5000 // a pause
        type("Von der Ke zu")
        assertEquals(3, editor.steps().size)
        editor.setLineText("l2", "Kriegsführung") // another line
        assertEquals(4, editor.steps().size)
    }

    @Test
    fun aKeyPressThatIsNotTypingEndsTheTypingStep() {
        type("Von der!")
        editor.splitLine("l2", 3)
        type("Von der!?")
        assertEquals(3, editor.steps().size)
    }

    @Test
    fun typingAWordAndDeletingItAgainLeavesNoStep() {
        val before = editor.layer
        type("Von derx")
        assertEquals(1, editor.steps().size)
        type("Von der")
        assertEquals(emptyList(), editor.steps())
        assertEquals(emptyList(), editor.history.pendingEntries)
        assertEquals(before, editor.layer, "the page is as it was, with the stamp it had")
        assertNull(editor.layer?.history)
        // The number of the dropped entry is used again: no gap.
        editor.setLineText("l2", "Krieg")
        assertEquals(1, editor.steps().single().seq)
    }

    @Test
    fun oneStepMakesSeveralEditsOneStep() {
        editor.oneStep {
            editor.setLineText("l1", "Von")
            editor.splitLine("l1", 3)
        }
        val change =
            editor
                .steps()
                .single()
                .changes
                .single() as LineChange
        assertEquals(listOf("Von", ""), change.inserted.map { it.text })
        assertEquals(listOf("Von", "", "Kriegsfüh-", "rung"), editor.texts())
    }

    @Test
    fun editsThatCancelOutInOneStepLeaveNothing() {
        val before = editor.layer
        editor.oneStep {
            editor.setLineText("l1", "Von dem")
            editor.setLineText("l1", "Von der")
        }
        assertEquals(emptyList(), editor.steps())
        assertEquals(before?.regions, editor.layer?.regions)
    }

    @Test
    fun theLayerCarriesTheNumberOfTheLastEntryThatChangedIt() {
        editor.setLineText("l2", "Krieg")
        assertEquals(1, editor.layer?.history)
        editor.deleteLine("l3")
        assertEquals(2, editor.layer?.history)
        assertEquals(2, editor.unsaved().getValue(1).history)
    }

    @Test
    fun thePendingEntriesAreTheStepsInOrderAndAMergedStepHasOneEntryWithTheFinalText() {
        type("Von der!")
        type("Von der!?")
        editor.deleteLine("l3")
        val entries = editor.history.pendingEntries.map { it as EditEntry }
        assertEquals(listOf(1, 2), entries.map { it.seq })
        val typed = entries[0].changes.single() as LineChange
        assertEquals(listOf("Von der!?"), typed.inserted.map { it.text })
        assertEquals(editor.steps().map { it.changes }, entries.map { it.changes })
    }

    @Test
    fun drainingGivesTheEntriesOnceAndTypingAfterItStartsANewStep() {
        type("Von der!")
        val drained = editor.history.drainPending()
        assertEquals(1, drained.size)
        assertEquals(emptyList(), editor.history.pendingEntries)
        type("Von der!?") // the first step is in the file: it cannot be changed any more
        assertEquals(2, editor.steps().size)
        assertEquals(listOf(2), editor.history.pendingEntries.map { (it as EditEntry).seq })
        assertEquals(1, drained.size)
    }

    @Test
    fun aStepOfSeveralPagesIsOneStepWithOneEntry() {
        val other =
            PageLayer(2, 300.0, 200.0, listOf(Region("r1", RegionType.TEXT, 1, null, listOf(line("l1", "zwei")))))
        val changed1 = PageEdits.setLineText(editor.layer!!, "l2", "Krieg").layer
        val changed2 = PageEdits.setLineText(other, "l1", "drei").layer
        val changes = diff(1, editor.layer!!, changed1) + diff(2, other, changed2)
        editor.recordStep(changes, mapOf(1 to changed1, 2 to changed2))
        assertEquals(1, editor.steps().size)
        assertEquals(
            2,
            editor
                .steps()
                .single()
                .changes.size,
        )
        assertEquals(setOf(1, 2), editor.changedPages)
        assertEquals(listOf(1, 1), editor.unsaved().values.map { it.history })
        assertEquals(1, editor.history.pendingEntries.size)
    }
}
