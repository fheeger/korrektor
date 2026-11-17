package korrektor.editor

import korrektor.AppModel
import korrektor.closeProject
import korrektor.format.EditEntry
import korrektor.format.HistoryCodec
import korrektor.format.HistoryEntry
import korrektor.format.PageStatus
import korrektor.format.Project
import korrektor.format.RedoEntry
import korrektor.format.SavedEntry
import korrektor.format.StatusChange
import korrektor.format.UndoEntry
import korrektor.format.setStatuses
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** After a crash the page statuses in `project.json` agree with the history again (T077). */
class StatusRecoveryTest {
    private val corrected = PageStatus.CORRECTED
    private val unchecked = PageStatus.UNCHECKED

    private fun change(
        seq: Int,
        page: Int,
        from: PageStatus,
        to: PageStatus,
    ) = EditEntry(seq, listOf(StatusChange(page, from, to)))

    private fun Project.append(vararg entries: HistoryEntry) = historyLog.appendAll(entries.map(HistoryCodec::encode))

    private fun open(project: Project): AppModel =
        AppModel().also {
            it.openProject(project.folder)
            assertEquals(null, it.error)
        }

    @Test
    fun aStatusInTheHistoryThatTheProjectFileLacksIsSetWhenTheProjectIsOpened() =
        withHistoryProject { project ->
            project.append(change(1, 2, unchecked, corrected)) // crash: the entry is there, project.json is not written
            val model = open(project)

            assertEquals(mapOf(2 to corrected), checkNotNull(model.project).statuses)
            assertEquals(mapOf(2 to corrected), checkNotNull(model.editor).statuses)
            assertEquals(mapOf(2 to corrected), Project.open(project.folder).statuses)
            assertEquals(SavedEntry(1), historyOf(project).last())
            model.closeProject()
        }

    @Test
    fun theNewestStatusOfAPageWinsAlsoThroughUndoAndRedoEntries() =
        withHistoryProject { project ->
            project.setStatuses(mapOf(1 to corrected, 3 to corrected)) // some are written, some not
            project.append(
                change(1, 1, unchecked, corrected),
                change(2, 3, unchecked, corrected),
                UndoEntry(3, 2, listOf(StatusChange(3, corrected, unchecked))),
                change(4, 4, unchecked, corrected),
                UndoEntry(5, 4, listOf(StatusChange(4, corrected, unchecked))),
                RedoEntry(6, 4, listOf(StatusChange(4, unchecked, corrected))),
            )
            val model = open(project)

            assertEquals(mapOf(1 to corrected, 4 to corrected), checkNotNull(model.project).statuses)
            model.closeProject()
        }

    @Test
    fun entriesThatSavedCoversAreNotMadeAgain() =
        withHistoryProject { project ->
            // Saved: the status was set, and afterwards the user marked it as unchecked in a way that the history
            // says as an entry before `saved`: project.json (unchecked) is what counts, the entries are not replayed.
            project.append(change(1, 2, unchecked, corrected), SavedEntry(1))
            val model = open(project)

            assertEquals(emptyMap(), checkNotNull(model.project).statuses)
            model.closeProject()
        }

    @Test
    fun recoveringTwiceGivesTheSameAndAStatusOfAMissingPageIsSkipped() =
        withHistoryProject { project ->
            project.append(change(1, 2, unchecked, corrected), change(2, 99, unchecked, corrected))
            val result1 = HistoryRecovery.recover(project)
            assertEquals(setOf(2), result1.repairedStatuses)
            assertEquals(mapOf(2 to corrected), project.statuses)

            val result2 = HistoryRecovery.recover(project)
            assertEquals(emptySet(), result2.repairedStatuses)
            assertEquals(mapOf(2 to corrected), Project.open(project.folder).statuses)
        }

    @Test
    fun aStatusAndCorrectionsOfOneCrashedSaveAreAllRepaired() =
        withHistoryProject { project ->
            val editor = BookEditor(project::readPage, kotlinx.coroutines.Dispatchers.Unconfined)
            kotlinx.coroutines.runBlocking { editor.show(1) }
            editor.setLineText("l1", "eins korrigiert")
            editor.setStatus(1, corrected)
            val taken = editor.snapshot()
            project.append(*taken.entries.toTypedArray()) // crash right after the history: no page, no status

            val model = open(project)

            val reopened = checkNotNull(model.project)
            assertEquals(mapOf(1 to corrected), reopened.statuses)
            assertEquals("eins korrigiert", reopened.texts(1).first())
            assertTrue(historyOf(project).last() is SavedEntry)
            model.closeProject()
        }
}
