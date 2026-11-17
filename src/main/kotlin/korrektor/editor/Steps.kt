package korrektor.editor

import korrektor.format.LineChange
import korrektor.format.PageLayer
import korrektor.format.Region
import korrektor.format.RegionType

/**
 * The changes that turn [before] into [after], both layers of page [page] of the book (S08): one [LineChange] for each
 * region whose lines differ. The common start and the common end of the two lists of lines are cut off, so a change
 * names only the lines that are different. Equal layers give no change.
 */
fun diff(
    page: Int,
    before: PageLayer,
    after: PageLayer,
): List<LineChange> {
    val ids = (before.regions.map { it.id } + after.regions.map { it.id }).distinct()
    return ids.mapNotNull { id ->
        lineChange(page, id, before.regions.firstOrNull { it.id == id }, after.regions.firstOrNull { it.id == id })
    }
}

/** The change of region [id] from [old] to [new] (null: the region is not there), or null if nothing is different. */
private fun lineChange(
    page: Int,
    id: String,
    old: Region?,
    new: Region?,
): LineChange? {
    val before = old?.lines.orEmpty()
    val after = new?.lines.orEmpty()
    val start = before.zip(after).takeWhile { (a, b) -> a == b }.size
    val end =
        generateSequence(0) { it + 1 }
            .takeWhile {
                it < minOf(before.size, after.size) - start &&
                    before[before.size - 1 - it] == after[after.size - 1 - it]
            }.count()
    val unchanged = start == before.size && start == after.size && (old == null) == (new == null)
    return if (unchanged) {
        null
    } else {
        LineChange(
            page,
            id,
            start,
            before.subList(start, before.size - end),
            after.subList(start, after.size - end),
            old != null,
            new != null,
        )
    }
}

/**
 * Makes [change] on [layer] (the layer of the change's page): the lines at its place are replaced. Returns null, and
 * changes nothing, if the layer is not as the change expects: the region or the lines [LineChange.removed] are not
 * there. A region that is to be made is made last in the reading order; one that is to go goes once it has no lines.
 * Applying a change and then its inverse gives the layer back.
 */
fun apply(
    layer: PageLayer,
    change: LineChange,
): PageLayer? {
    val region = layer.regions.firstOrNull { it.id == change.region }
    val fits =
        if (region == null) {
            !change.regionBefore && change.removed.isEmpty() && change.index == 0
        } else {
            change.regionBefore && region.holds(change)
        }
    return if (fits) rebuild(layer, region, change) else null
}

private fun Region.holds(change: LineChange): Boolean {
    val end = change.index + change.removed.size
    return end <= lines.size && lines.subList(change.index, end) == change.removed
}

private fun rebuild(
    layer: PageLayer,
    region: Region?,
    change: LineChange,
): PageLayer? =
    try {
        val regions =
            if (region == null) {
                val order = (layer.regions.maxOfOrNull { it.order } ?: 0) + 1
                layer.regions + Region(change.region, RegionType.TEXT, order, null, change.inserted)
            } else {
                val lines =
                    region.lines.take(change.index) + change.inserted +
                        region.lines.drop(change.index + change.removed.size)
                layer.regions.mapNotNull {
                    when {
                        it !== region -> it
                        !change.regionAfter && lines.isEmpty() -> null
                        else -> it.copy(lines = lines)
                    }
                }
            }
        layer.copy(regions = regions)
    } catch (_: IllegalArgumentException) {
        null // the change would make the page invalid (a line id twice): it does not fit this page
    }
