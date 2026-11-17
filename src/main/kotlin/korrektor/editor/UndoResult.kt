package korrektor.editor

/** What [undo] or [redo] came to. */
sealed interface UndoResult {
    /** There was no step to take back (or bring back). */
    data object NothingToDo : UndoResult

    /** The step was undone (or redone) on [page]; the cursor belongs at [focus] there, if the step has a place. */
    data class Done(
        val page: Int,
        val focus: Focus?,
    ) : UndoResult

    /** The step did not fit the pages as they are (they were changed outside the app): it was dropped. */
    data object DoesNotFit : UndoResult

    /** A page of the step could not be read. The step is kept. */
    data class Failed(
        val reason: String,
    ) : UndoResult
}
