package korrektor.search

import korrektor.editor.BookEditor
import korrektor.editor.activeLine

// Going from hit to hit (S10): which result comes next or before. Pure functions, and one extension of the model that
// asks the editor where the cursor is.

/**
 * The index of the result to go to from [current] (the result shown last, or -1), or null if there are none. It
 * wraps around at the end and the start of the list. Without a current result the one after (before) [cursor] is
 * taken: the next hit that starts after the cursor, or the last one before it.
 */
fun stepIndex(
    results: List<Match>,
    current: Int,
    forward: Boolean,
    cursor: Place,
): Int? {
    val count = results.size
    return when {
        count == 0 -> null
        current in 0 until count -> Math.floorMod(current + if (forward) 1 else -1, count)
        forward -> results.indexOfFirst { it.place > cursor }.takeIf { it >= 0 } ?: 0
        else -> results.indexOfLast { it.place < cursor }.takeIf { it >= 0 } ?: (count - 1)
    }
}

/** Where the cursor is: the page shown and the line and column of the cursor in it, or the start of the page. */
fun BookEditor.cursorPlace(): Place {
    val line = activeLine
    val number = lines.indexOfFirst { it.id == line?.id } + 1
    return Place(page, number, if (number > 0) caret else 0)
}

/** The result to go to next (or before) from where the user is; null if there is no result. */
fun SearchModel.step(forward: Boolean): Int? = stepIndex(results.toList(), current, forward, editor.cursorPlace())
