package korrektor.editor

import korrektor.format.Change
import korrektor.format.EditEntry
import korrektor.format.FormatException
import korrektor.format.HistoryCodec
import korrektor.format.HistoryEntry
import korrektor.format.HistoryLog
import korrektor.format.LineChange
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.format.RedoEntry
import korrektor.format.SavedEntry
import korrektor.format.UndoEntry
import korrektor.format.entrySeq
import java.io.IOException

/**
 * Makes the history and the page files agree after a crash (S08, T072). A save appends its entries to the history, then
 * writes the pages, then appends a `saved` entry; a crash in between leaves entries that some page files do not have
 * yet. [recover] makes those changes on the pages and appends the `saved` entry.
 *
 * A page file says which entries it has: `history`, the number of the last entry that changed it. An entry after the
 * last `saved` entry is made on a page only if its number is above the page's. A change that does not fit the page
 * is skipped: nothing is forced onto a page.
 */
object HistoryRecovery {
    /** How many entries are read back at a time while looking for the last `saved` entry. */
    private const val STEP = 16

    /** What [recover] found: the newest entry's number (0 if the history is empty) and the pages it repaired. */
    class Result(
        val lastSeq: Int,
        val repairedPages: Set<Int>,
    )

    /**
     * Looks at the history of [project] from its end back to the last `saved` entry, repairs the pages that miss
     * entries after it, and appends `saved`. The project must be locked by the caller (pages are written).
     *
     * @throws FormatException if the history or a page cannot be read or written.
     */
    fun recover(project: Project): Result =
        try {
            val log = project.historyLog
            val tail = entriesAfterLastSaved(log)
            val repaired = if (tail.entries.isEmpty()) emptySet() else replay(project, tail.entries)
            val lastSeq = maxOf(tail.lastSeq, tail.entries.maxOfOrNull { it.entrySeq ?: 0 } ?: 0)
            if (tail.entries.isNotEmpty()) log.append(HistoryCodec.encode(SavedEntry(lastSeq)))
            Result(lastSeq, repaired)
        } catch (e: IOException) {
            throw FormatException("The history of ${project.folder} cannot be repaired: ${e.message}", e)
        }

    private class Tail(
        val entries: List<HistoryEntry>,
        val lastSeq: Int,
    )

    /** The entries after the last `saved` one (oldest first), and the newest number found. */
    private fun entriesAfterLastSaved(log: HistoryLog): Tail {
        val found = ArrayDeque<HistoryEntry>()
        var lastSeq = 0
        var at = log.end
        var saved = false
        while (!saved && at > 0) {
            val block = log.readBefore(at, STEP)
            if (block.entries.isEmpty()) break
            at = block.start
            for (json in block.entries.reversed()) {
                val entry = HistoryCodec.decode(json)
                if (lastSeq == 0) lastSeq = (entry as? SavedEntry)?.upTo ?: entry.entrySeq ?: 0
                if (entry is SavedEntry) {
                    saved = true
                    break
                }
                found.addFirst(entry)
            }
        }
        return Tail(found.filter { changesOf(it) != null }, lastSeq)
    }

    private fun changesOf(entry: HistoryEntry): List<Change>? =
        when (entry) {
            is EditEntry -> entry.changes
            is UndoEntry -> entry.changes
            is RedoEntry -> entry.changes
            else -> null
        }

    /** Makes the entries on the pages that miss them, writes those pages once; returns their numbers. */
    private fun replay(
        project: Project,
        entries: List<HistoryEntry>,
    ): Set<Int> {
        val layers = LinkedHashMap<Int, PageLayer>()
        val repaired = LinkedHashSet<Int>()
        for (entry in entries) {
            val seq = entry.entrySeq ?: continue
            val byPage = changesOf(entry).orEmpty().filterIsInstance<LineChange>().groupBy { it.page }
            for ((page, changes) in byPage) {
                val layer = layers.getOrPut(page) { project.readPage(page) }
                if ((layer.history ?: 0) >= seq) continue // the page file has this entry
                val made = changes.fold<LineChange, PageLayer?>(layer) { l, c -> l?.let { apply(it, c) } }
                if (made != null) {
                    layers[page] = made.copy(history = seq)
                    repaired += page
                }
            }
        }
        for (page in repaired) project.writePage(page, layers.getValue(page))
        return repaired
    }
}
