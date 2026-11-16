package korrektor

/**
 * Closes the open project and gives its lock back, so that it can be opened again (also in this app). The view is
 * remembered. What is not saved yet is not handled here: the caller flushes first (`requestClose`, S07).
 */
fun AppModel.closeProject() {
    saveView()
    lock?.close()
    lock = null
    project = null
    viewer = null
    editor = null
    saver = null
    metadata = null
}
