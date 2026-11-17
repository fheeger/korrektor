package korrektor.editor

import korrektor.format.Change
import korrektor.format.LineChange
import korrektor.format.PageLayer
import korrektor.format.PageStatus
import korrektor.format.StatusChange

// Making the changes of a step on the book, for undo and redo (S08): all of them or none.

/** The pages as the changes leave them, or why they cannot be made. */
internal sealed interface Applied {
    class Layers(
        val layers: Map<Int, PageLayer>,
        val statuses: Map<Int, PageStatus>,
    ) : Applied

    data object DoesNotFit : Applied

    class Unreadable(
        val reason: String,
    ) : Applied
}

internal fun BookEditor.applyAll(changes: List<Change>): Applied {
    val layers = LinkedHashMap<Int, PageLayer>()
    val statuses = LinkedHashMap<Int, PageStatus>()
    var failure: Applied? = null
    for (change in changes) {
        failure = if (change is StatusChange) applyStatus(statuses, change) else applyOne(layers, change)
        if (failure != null) break
    }
    return failure ?: Applied.Layers(layers, statuses)
}

/** Makes [change] in [statuses] (the new statuses of this undo); it fits if the page has the status it came from. */
private fun BookEditor.applyStatus(
    statuses: MutableMap<Int, PageStatus>,
    change: StatusChange,
): Applied? {
    val fits = (statuses[change.page] ?: statusOf(change.page)) == change.from
    if (fits) statuses[change.page] = change.to
    return if (fits) null else Applied.DoesNotFit
}

/** Makes [change] on the layer of its page in [layers] (read first if it is not there); returns why not, or null. */
private fun BookEditor.applyOne(
    layers: MutableMap<Int, PageLayer>,
    change: Change,
): Applied? {
    val current =
        layers[change.page] ?: runCatching { layerOf(change.page) }.getOrElse {
            return Applied.Unreadable(it.message ?: it::class.simpleName ?: "unknown error")
        }
    val made = (change as? LineChange)?.let { apply(current, it) }
    if (made != null) layers[change.page] = made
    return if (made == null) Applied.DoesNotFit else null
}

/** The layer of page [number] as it is now: changed in this session, the one shown, or the one in the project. */
private fun BookEditor.layerOf(number: Int): PageLayer =
    changed[number] ?: layer?.takeIf { number == page } ?: readPage(number)
