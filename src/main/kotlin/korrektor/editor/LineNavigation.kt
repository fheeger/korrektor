package korrektor.editor

// Moves of the cursor from line to line by the keyboard (S06). They are extensions: they only use what the editor
// shows (`lines`, `activeLineId`, `caret`) and `requestFocus`, the way a click on the scan does.

/**
 * Puts the cursor [delta] lines down (negative: up) from the active line, in the same column where the line is long
 * enough. With no active line the first line (down) or the last line (up) is the target. Past the first or last line
 * it says which page is wanted: the caller changes the page (the editor does not know the book).
 */
fun BookEditor.moveLine(delta: Int): LineMove {
    val list = lines
    if (list.isEmpty()) return LineMove.NOTHING
    val current = list.indexOfFirst { it.id == activeLineId }
    val target =
        when {
            current >= 0 -> current + delta
            delta > 0 -> 0
            else -> list.lastIndex
        }
    val move =
        when {
            target < 0 -> LineMove.PREVIOUS_PAGE
            target > list.lastIndex -> LineMove.NEXT_PAGE
            else -> LineMove.MOVED
        }
    if (move == LineMove.MOVED) requestFocus(list[target].id, caret)
    return move
}

/** Puts the cursor in the first line of the page, in the same column where it is long enough. */
fun BookEditor.firstLine() {
    lines.firstOrNull()?.let { requestFocus(it.id, caret) }
}

/** Puts the cursor in the last line of the page, in the same column where it is long enough. */
fun BookEditor.lastLine() {
    lines.lastOrNull()?.let { requestFocus(it.id, caret) }
}

/**
 * Says that the next time page [number] is shown, the cursor goes to its first or last line ([arrival]). Called just
 * before the page is changed by the keyboard. A wish for a page that is never shown is replaced by the next one.
 */
fun BookEditor.arriveAt(
    number: Int,
    arrival: Arrival,
) {
    arriving = number to arrival
}

/** Carries out the wish of [arriveAt] if it is for page [number], once the page's lines are there. */
internal fun BookEditor.applyArrival(number: Int) {
    val (target, arrival) = arriving ?: return
    if (target != number) return
    arriving = null
    val line = if (arrival == Arrival.LAST) lines.lastOrNull() else lines.firstOrNull()
    if (arrival != Arrival.NONE && line != null) requestFocus(line.id, 0)
}
