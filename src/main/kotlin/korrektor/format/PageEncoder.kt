package korrektor.format

import kotlinx.serialization.json.JsonElement

/** The writing half of [PageCodec]. */
internal object PageEncoder {
    fun encode(page: PageLayer): String {
        val members = mutableListOf<String>()
        members += "  \"pdfPage\": ${page.pdfPage}"
        members += "  \"width\": ${CanonicalJson.formatNumber(page.width)}"
        members += "  \"height\": ${CanonicalJson.formatNumber(page.height)}"
        members += "  \"regions\": " + encodeRegions(page.regions)
        members += extraMembers(page.extra).map { "  $it" }
        return "{\n" + members.joinToString(",\n") + "\n}\n"
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
        val text = CanonicalJson.quote(word.text)
        return when {
            word.extra.isNotEmpty() -> {
                val members = mutableListOf("\"t\": $text")
                word.box?.let { members += "\"b\": ${encodeBox(it)}" }
                members += extraMembers(word.extra)
                "{" + members.joinToString(", ") + "}"
            }
            word.box == null -> "[$text]"
            else -> "[$text, ${boxNumbers(word.box)}]"
        }
    }

    private fun encodeBox(box: Box) = "[${boxNumbers(box)}]"

    private fun boxNumbers(box: Box) =
        listOf(box.x, box.y, box.width, box.height).joinToString(", ") {
            CanonicalJson.formatNumber(it)
        }

    private fun extraMembers(extra: Map<String, JsonElement>) =
        extra.entries.sortedBy { it.key }.map { "${CanonicalJson.quote(it.key)}: ${CanonicalJson.write(it.value)}" }
}
