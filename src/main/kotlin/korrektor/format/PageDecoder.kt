package korrektor.format

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The reading half of [PageCodec]. */
internal object PageDecoder {
    private val PAGE_KEYS = setOf("pdfPage", "width", "height", "regions", "note")
    private val REGION_KEYS = setOf("id", "type", "order", "box", "lines")
    private val LINE_KEYS = setOf("id", "box", "origin", "words")
    private val WORD_KEYS = setOf("t", "b")

    private const val BOX_SIZE = 4
    private const val WORD_WITH_BOX_SIZE = BOX_SIZE + 1

    fun decode(text: String): PageLayer {
        val root = CanonicalJson.parse(text) as? JsonObject ?: throw FormatException("Page file: expected an object")
        try {
            return PageLayer(
                pdfPage = root.int("pdfPage", "page"),
                width = root.number("width", "page"),
                height = root.number("height", "page"),
                regions = root.array("regions", "page").mapIndexed { i, r -> decodeRegion(r, "regions[$i]") },
                extra = root.extra(PAGE_KEYS),
                note = root.optional("note")?.asString("page.note"),
            )
        } catch (e: IllegalArgumentException) {
            throw FormatException("Page file: ${e.message}", e)
        }
    }

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
                if (element.size != 1 && element.size != WORD_WITH_BOX_SIZE) {
                    bad(path, "[text] or [text, x, y, width, height]")
                }
                val box = if (element.size == WORD_WITH_BOX_SIZE) boxAt(element, 1, path) else null
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
        if (a.size != BOX_SIZE) bad(path, "an array [x, y, width, height]")
        return boxAt(a, 0, path)
    }

    /** The box `[x, y, width, height]` that starts at [offset] in [a]. */
    private fun boxAt(
        a: JsonArray,
        offset: Int,
        path: String,
    ): Box {
        val numbers = (0 until BOX_SIZE).map { num(a[offset + it], "$path[${offset + it}]") }.iterator()
        return Box(numbers.next(), numbers.next(), numbers.next(), numbers.next())
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
}
