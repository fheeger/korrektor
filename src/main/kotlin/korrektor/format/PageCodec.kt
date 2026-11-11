package korrektor.format

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads and writes a [PageLayer] as the text of a page file (`pages/0001.json`, `pages/0001.ocr.json`).
 *
 * Layout: one member per line at the top, one block per region, and exactly one line of the book per line
 * of the file, so correcting a word changes one line in a diff. See `docs/project-format.md`.
 */
object PageCodec {
    private val PAGE_KEYS = setOf("pdfPage", "width", "height", "regions")
    private val REGION_KEYS = setOf("id", "type", "order", "box", "lines")
    private val LINE_KEYS = setOf("id", "box", "origin", "words")
    private val WORD_KEYS = setOf("t", "b")

    fun encode(page: PageLayer): String {
        val members = mutableListOf<String>()
        members += "  \"pdfPage\": ${page.pdfPage}"
        members += "  \"width\": ${CanonicalJson.formatNumber(page.width)}"
        members += "  \"height\": ${CanonicalJson.formatNumber(page.height)}"
        members += "  \"regions\": " + encodeRegions(page.regions)
        members += extraMembers(page.extra).map { "  $it" }
        return "{\n" + members.joinToString(",\n") + "\n}\n"
    }

    fun decode(text: String): PageLayer {
        val root = CanonicalJson.parse(text) as? JsonObject ?: throw FormatException("Page file: expected an object")
        try {
            return PageLayer(
                pdfPage = root.int("pdfPage", "page"),
                width = root.number("width", "page"),
                height = root.number("height", "page"),
                regions = root.array("regions", "page").mapIndexed { i, r -> decodeRegion(r, "regions[$i]") },
                extra = root.extra(PAGE_KEYS),
            )
        } catch (e: IllegalArgumentException) {
            throw FormatException("Page file: ${e.message}", e)
        }
    }

    private fun encodeRegions(regions: List<Region>): String {
        if (regions.isEmpty()) return "[]"
        return "[\n" + regions.joinToString(",\n") { encodeRegion(it) } + "\n  ]"
    }

    private fun encodeRegion(region: Region): String {
        val head = StringBuilder("    {\"id\": ${CanonicalJson.quote(region.id)}")
        head.append(", \"type\": ${CanonicalJson.quote(region.type.name)}")
        head.append(", \"order\": ${region.order}")
        region.box?.let { head.append(", \"box\": ${encodeBox(it)}") }
        head.append(", \"lines\": [")
        val lines =
            if (region.lines.isEmpty()) {
                "]"
            } else {
                "\n" + region.lines.joinToString(",\n") { "      " + encodeLine(it) } + "\n    ]"
            }
        val extra = extraMembers(region.extra).joinToString("") { ", $it" }
        return "$head$lines$extra}"
    }

    private fun encodeLine(line: Line): String {
        val sb = StringBuilder("{\"id\": ${CanonicalJson.quote(line.id)}")
        line.box?.let { sb.append(", \"box\": ${encodeBox(it)}") }
        line.origin?.let { sb.append(", \"origin\": ${CanonicalJson.quote(it)}") }
        sb.append(", \"words\": [${line.words.joinToString(", ") { encodeWord(it) }}]")
        extraMembers(line.extra).forEach { sb.append(", ").append(it) }
        return sb.append('}').toString()
    }

    /** A word is `["text", x, y, w, h]` or `["text"]`. With unknown fields it needs the object form. */
    private fun encodeWord(word: Word): String {
        if (word.extra.isNotEmpty()) {
            val members = mutableListOf("\"t\": ${CanonicalJson.quote(word.text)}")
            word.box?.let { members += "\"b\": ${encodeBox(it)}" }
            members += extraMembers(word.extra)
            return "{" + members.joinToString(", ") + "}"
        }
        val box = word.box ?: return "[${CanonicalJson.quote(word.text)}]"
        return "[${CanonicalJson.quote(word.text)}, ${boxNumbers(box)}]"
    }

    private fun encodeBox(box: Box) = "[${boxNumbers(box)}]"

    private fun boxNumbers(box: Box) = listOf(box.x, box.y, box.width, box.height).joinToString(", ") { CanonicalJson.formatNumber(it) }

    private fun extraMembers(extra: Map<String, JsonElement>) =
        extra.entries.sortedBy { it.key }.map { "${CanonicalJson.quote(it.key)}: ${CanonicalJson.write(it.value)}" }

    private fun decodeRegion(
        element: JsonElement,
        path: String,
    ): Region {
        val o = element.asObject(path)
        return Region(
            id = o.string("id", path),
            type = RegionType(o.string("type", path)),
            order = o.int("order", path),
            box = o.optional("box")?.let { decodeBox(it, "$path.box") },
            lines = o.array("lines", path).mapIndexed { i, l -> decodeLine(l, "$path.lines[$i]") },
            extra = o.extra(REGION_KEYS),
        )
    }

    private fun decodeLine(
        element: JsonElement,
        path: String,
    ): Line {
        val o = element.asObject(path)
        return Line(
            id = o.string("id", path),
            box = o.optional("box")?.let { decodeBox(it, "$path.box") },
            origin = o.optional("origin")?.asString("$path.origin"),
            words = o.array("words", path).mapIndexed { i, w -> decodeWord(w, "$path.words[$i]") },
            extra = o.extra(LINE_KEYS),
        )
    }

    private fun decodeWord(
        element: JsonElement,
        path: String,
    ): Word =
        when (element) {
            is JsonArray -> {
                if (element.size != 1 && element.size != 5) bad(path, "[text] or [text, x, y, width, height]")
                val box =
                    if (element.size == 5) {
                        Box(
                            num(element[1], "$path[1]"),
                            num(element[2], "$path[2]"),
                            num(element[3], "$path[3]"),
                            num(element[4], "$path[4]"),
                        )
                    } else {
                        null
                    }
                Word(element[0].asString("$path[0]"), box)
            }
            is JsonObject ->
                Word(
                    text = element.string("t", path),
                    box = element.optional("b")?.let { decodeBox(it, "$path.b") },
                    extra = element.extra(WORD_KEYS),
                )
            else -> bad(path, "an array or an object")
        }

    private fun decodeBox(
        element: JsonElement,
        path: String,
    ): Box {
        val a = element as? JsonArray ?: bad(path, "an array [x, y, width, height]")
        if (a.size != 4) bad(path, "an array [x, y, width, height]")
        return Box(num(a[0], "$path[0]"), num(a[1], "$path[1]"), num(a[2], "$path[2]"), num(a[3], "$path[3]"))
    }

    // Values are rounded to the stored precision, so what is decoded is what would be written back.
    private fun num(
        element: JsonElement,
        path: String,
    ): Double {
        val v = (element as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
        if (v == null || !v.isFinite()) bad(path, "a number")
        return CanonicalJson.round1(v)
    }

    private fun bad(
        path: String,
        expected: String,
    ): Nothing = throw FormatException("Page file: $path must be $expected")

    private fun JsonElement.asObject(path: String): JsonObject = this as? JsonObject ?: bad(path, "an object")

    private fun JsonElement.asString(path: String): String =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: bad(path, "a string")

    /** The member if it is present and not null. */
    private fun JsonObject.optional(key: String): JsonElement? = this[key]?.takeIf { it !is JsonNull }

    private fun JsonObject.field(
        key: String,
        path: String,
    ): JsonElement = optional(key) ?: throw FormatException("Page file: $path is missing \"$key\"")

    private fun JsonObject.string(
        key: String,
        path: String,
    ) = field(key, path).asString("$path.$key")

    private fun JsonObject.array(
        key: String,
        path: String,
    ) = field(key, path) as? JsonArray ?: bad("$path.$key", "an array")

    private fun JsonObject.number(
        key: String,
        path: String,
    ) = num(field(key, path), "$path.$key")

    private fun JsonObject.int(
        key: String,
        path: String,
    ): Int {
        val p = field(key, path) as? JsonPrimitive
        return p?.takeIf { !it.isString }?.content?.toIntOrNull() ?: bad("$path.$key", "an integer")
    }

    private fun JsonObject.extra(known: Set<String>): Map<String, JsonElement> = filterKeys { it !in known }
}
