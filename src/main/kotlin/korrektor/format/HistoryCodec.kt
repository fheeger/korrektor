package korrektor.format

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The entries of `history/book.jsonl` as JSON objects and back (S08). The writing rules are those of every file
 * (`CanonicalJson`: alphabetical keys). A line inside a change is written as in a page file, so the same code reads
 * and writes both. An entry or a change of a type this version does not know is kept as it is.
 *
 * ```
 * {"changes": [...], "seq": 7, "type": "edit"}
 * {"changes": [...], "seq": 8, "step": 7, "type": "undo"}
 * {"changes": [...], "seq": 9, "step": 7, "type": "redo"}
 * {"type": "saved", "upTo": 9}
 * ```
 */
object HistoryCodec {
    private const val LINES = "lines"
    private const val STATUS = "status"

    fun encode(entry: HistoryEntry): JsonObject =
        when (entry) {
            is EditEntry -> entryObject("edit", entry.seq, null, entry.changes)
            is UndoEntry -> entryObject("undo", entry.seq, entry.step, entry.changes)
            is RedoEntry -> entryObject("redo", entry.seq, entry.step, entry.changes)
            is SavedEntry -> JsonObject(mapOf("type" to JsonPrimitive("saved"), "upTo" to JsonPrimitive(entry.upTo)))
            is UnknownEntry -> entry.json
        }

    /** @throws FormatException if a known entry is damaged. */
    fun decode(json: JsonObject): HistoryEntry =
        try {
            when (json.string("type")) {
                "edit" -> EditEntry(json.int("seq"), json.changes())
                "undo" -> UndoEntry(json.int("seq"), json.int("step"), json.changes())
                "redo" -> RedoEntry(json.int("seq"), json.int("step"), json.changes())
                "saved" -> SavedEntry(json.int("upTo"))
                else -> UnknownEntry(json)
            }
        } catch (e: FormatException) {
            throw FormatException("History entry: ${e.message?.removePrefix("Page file: ")}", e)
        } catch (e: IllegalArgumentException) {
            throw FormatException("History entry: ${e.message}", e)
        }

    private fun entryObject(
        type: String,
        seq: Int,
        step: Int?,
        changes: List<Change>,
    ) = JsonObject(
        buildMap {
            put("type", JsonPrimitive(type))
            put("seq", JsonPrimitive(seq))
            step?.let { put("step", JsonPrimitive(it)) }
            put("changes", JsonArray(changes.map(::encodeChange)))
        },
    )

    private fun encodeChange(change: Change): JsonElement =
        when (change) {
            is LineChange ->
                JsonObject(
                    buildMap {
                        put("type", JsonPrimitive(LINES))
                        put("page", JsonPrimitive(change.page))
                        put("region", JsonPrimitive(change.region))
                        put("index", JsonPrimitive(change.index))
                        put("removed", JsonArray(change.removed.map(::encodeLine)))
                        put("inserted", JsonArray(change.inserted.map(::encodeLine)))
                        // Written only when a region is made or goes: nearly every change leaves it as it is.
                        if (!change.regionBefore) put("regionBefore", JsonPrimitive(false))
                        if (!change.regionAfter) put("regionAfter", JsonPrimitive(false))
                    },
                )
            is StatusChange ->
                JsonObject(
                    mapOf(
                        "type" to JsonPrimitive(STATUS),
                        "page" to JsonPrimitive(change.page),
                        "from" to JsonPrimitive(change.from.name),
                        "to" to JsonPrimitive(change.to.name),
                    ),
                )
            is UnknownChange -> change.json
        }

    private fun decodeChange(element: JsonElement): Change {
        val o = element.asObject("changes[]")
        return when (o.string("type")) {
            LINES ->
                LineChange(
                    page = o.int("page"),
                    region = o.string("region"),
                    index = o.int("index"),
                    removed = o.lines("removed"),
                    inserted = o.lines("inserted"),
                    regionBefore = o.flag("regionBefore"),
                    regionAfter = o.flag("regionAfter"),
                )
            STATUS -> StatusChange(o.int("page"), PageStatus(o.string("from")), PageStatus(o.string("to")))
            else -> UnknownChange(o.int("page"), o)
        }
    }

    // A line is what a page file has for it, read back as JSON: the same encoder and decoder, so nothing can differ.
    private fun encodeLine(line: Line): JsonElement = CanonicalJson.parse(PageEncoder.encodeLine(line))

    private fun JsonObject.lines(key: String): List<Line> =
        array(key).mapIndexed { i, l -> PageDecoder.decodeLine(l, "$key[$i]") }

    private fun JsonObject.changes(): List<Change> = array("changes").map(::decodeChange)
}

private fun JsonObject.array(key: String): JsonArray =
    this[key] as? JsonArray ?: throw FormatException("\"$key\" must be an array")

private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw FormatException("\"$key\" must be a string")

private fun JsonObject.int(key: String): Int =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
        ?: throw FormatException("\"$key\" must be an integer")

/** A flag that is true unless it says false. */
private fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.content != "false"
