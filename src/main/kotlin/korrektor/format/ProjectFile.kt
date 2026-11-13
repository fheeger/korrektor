package korrektor.format

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Reads and writes the text of `project.json`. */
internal object ProjectFile {
    private const val FILE = Project.PROJECT_FILE

    fun encode(
        pages: List<PageInfo>,
        extra: Map<String, JsonElement>,
    ): String {
        val members =
            mutableListOf("  \"format\": ${CanonicalJson.quote(Project.FORMAT)}", "  \"version\": ${Project.VERSION}")
        members +=
            if (pages.isEmpty()) {
                "  \"pages\": []"
            } else {
                "  \"pages\": [\n" + pages.joinToString(",\n") { "    " + encodePage(it) } + "\n  ]"
            }
        extra.entries.sortedBy { it.key }.forEach {
            members += "  ${CanonicalJson.quote(it.key)}: ${CanonicalJson.write(it.value)}"
        }
        return "{\n" + members.joinToString(",\n") + "\n}\n"
    }

    private fun encodePage(page: PageInfo): String {
        val members = mutableListOf("\"pdfPage\": ${page.pdfPage}")
        page.extra.entries
            .sortedBy { it.key }
            .forEach { members += "${CanonicalJson.quote(it.key)}: ${CanonicalJson.write(it.value)}" }
        return "{" + members.joinToString(", ") + "}"
    }

    fun decode(text: String): Pair<List<PageInfo>, Map<String, JsonElement>> {
        val root = CanonicalJson.parse(text) as? JsonObject ?: throw FormatException("$FILE: expected an object")
        checkFormat(root)
        checkVersion(root)
        val pages =
            (
                root["pages"] as? JsonArray
                    ?: throw FormatException("$FILE: \"pages\" must be an array")
            ).mapIndexed { i, p -> decodePage(p, i) }
        return pages to root.filterKeys { it !in setOf("format", "version", "pages") }
    }

    private fun checkFormat(root: JsonObject) {
        val format = (root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (format != Project.FORMAT) {
            throw FormatException("$FILE: this is not a korrektur project (format is ${format ?: "missing"})")
        }
    }

    private fun checkVersion(root: JsonObject) {
        val version = (root["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
        if (version == null || version < 1) throw FormatException("$FILE: \"version\" must be a positive integer")
        if (version > Project.VERSION) {
            throw FormatException(
                "This project has format version $version; " +
                    "this app supports up to ${Project.VERSION}. Please update the app.",
            )
        }
    }

    private fun decodePage(
        element: JsonElement,
        index: Int,
    ): PageInfo {
        val o = element as? JsonObject ?: throw FormatException("$FILE: pages[$index] must be an object")
        val pdfPage = (o["pdfPage"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
        if (pdfPage == null || pdfPage < 1) {
            throw FormatException("$FILE: pages[$index].pdfPage must be a positive integer")
        }
        return PageInfo(pdfPage, o.filterKeys { it != "pdfPage" })
    }
}
