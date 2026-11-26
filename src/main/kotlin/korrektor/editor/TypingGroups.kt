package korrektor.editor

import kotlin.math.max
import kotlin.math.min

/** A pause in typing of this many milliseconds ends the undo step that typing makes. */
const val TYPING_PAUSE_MS = 1_000L

/**
 * What one edit of the text of a line did, found by cutting off the common start and end of the old and new text:
 * [removed] characters at [at] were replaced by [inserted] ones. [startsWord] is true if the edit types a space after
 * a character that is not a space: the space begins the next word. [marks] of the inserted characters are combining
 * marks that came with a letter, like the small e of "uͤ".
 */
data class TextEdit(
    val at: Int,
    val removed: Int,
    val inserted: Int,
    val startsWord: Boolean,
    val marks: Int = 0,
) {
    /**
     * One character typed or deleted: what the keys do. A paste or a replaced selection is more. A letter inserted
     * together with its combining mark is one character.
     */
    val isOneKey: Boolean get() = removed + inserted - marks == 1
}

/** The place of typing that is going on: its page and line, when it last changed, and the span it covers. */
data class TypingRun(
    val page: Int,
    val lineId: String,
    val lastTime: Long,
    val start: Int,
    val end: Int,
)

/**
 * How typing is grouped into undo steps (S08): typing in one place of one line is one step, as long as it goes on
 * without a pause of a second, and a new word starts a new step ("Die Welt ist" is undone as " ist", " Welt", "Die").
 * A paste and a replaced selection are steps of their own. Pure functions; the clock is the caller's.
 */
object TypingGroups {
    /** What the edit from [old] to [new] did. */
    fun textEdit(
        old: String,
        new: String,
    ): TextEdit {
        val limit = min(old.length, new.length)
        var at = 0
        while (at < limit && old[at] == new[at]) at++
        var tail = 0
        while (tail < limit - at && old[old.length - 1 - tail] == new[new.length - 1 - tail]) tail++
        val inserted = new.length - at - tail
        val spaceAfterWord = inserted == 1 && new[at] == ' ' && at > 0 && new[at - 1] != ' '
        val marks = new.substring(at, at + inserted).count { it.category == CharCategory.NON_SPACING_MARK }
        // A mark typed on its own is a character like any other.
        return TextEdit(at, old.length - at - tail, inserted, spaceAfterWord, if (marks < inserted) marks else 0)
    }

    /** Whether [edit] on [lineId] of [page] at time [now] goes on with the typing in [run]. */
    fun continues(
        run: TypingRun?,
        page: Int,
        lineId: String,
        edit: TextEdit,
        now: Long,
    ): Boolean =
        run != null &&
            run.page == page &&
            run.lineId == lineId &&
            now - run.lastTime < TYPING_PAUSE_MS &&
            edit.isOneKey &&
            !edit.startsWord &&
            edit.at <= run.end &&
            edit.at + edit.removed >= run.start

    /** [run] after [edit] went on with it (see [continues]): its span grows or shrinks with the edit. */
    fun extended(
        run: TypingRun,
        edit: TextEdit,
        now: Long,
    ): TypingRun =
        run.copy(
            lastTime = now,
            start = min(run.start, edit.at),
            end = max(run.end, edit.at + edit.removed) - edit.removed + edit.inserted,
        )

    /** The run that starts with [edit]; none for a paste or a replaced selection (the next edit starts afresh). */
    fun started(
        page: Int,
        lineId: String,
        edit: TextEdit,
        now: Long,
    ): TypingRun? = if (edit.isOneKey) TypingRun(page, lineId, now, edit.at, edit.at + edit.inserted) else null
}
