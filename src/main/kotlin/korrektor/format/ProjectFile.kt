package korrektor.format

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Reads and writes the text of `project.json`. */
internal object ProjectFile {
    private const val FILE = Project.PROJECT_FILE

    /** What `project.json` holds. */
    class Parsed(
        val pages: List<PageInfo>,
        val metadata: Metadata?,
        val extra: Map<String, JsonElement>,
    )

    fun encode(
        pages: List<PageInfo>,
        metadata: Metadata?,
        extra: Map<String, JsonElement>,
    ): String {
        val members =
            mutableListOf("  \"format\": ${CanonicalJson.quote(Project.FORMAT)}", "  \"version\": ${Project.VERSION}")
        metadata?.let { members += "  \"metadata\": ${encodeMetadata(it)}" }
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

    /** One line; empty author, year and source are left out. */
    private fun encodeMetadata(m: Metadata): String {
        val members = mutableListOf("\"title\": ${CanonicalJson.quote(m.title)}")
        if (m.author.isNotEmpty()) members += "\"author\": ${CanonicalJson.quote(m.author)}"
        if (m.year.isNotEmpty()) members += "\"year\": ${CanonicalJson.quote(m.year)}"
        if (m.source.isNotEmpty()) members += "\"source\": ${CanonicalJson.quote(m.source)}"
        m.extra.entries
            .sortedBy { it.key }
            .forEach { members += "${CanonicalJson.quote(it.key)}: ${CanonicalJson.write(it.value)}" }
        return "{" + members.joinToString(", ") + "}"
    }

    private fun decodeMetadata(element: JsonElement): Metadata {
        val o = element as? JsonObject ?: throw FormatException("$FILE: \"metadata\" must be an object")
        val title = metadataText(o, "title")
        if (title.isNullOrBlank()) throw FormatException("$FILE: metadata.title must be a non-blank string")
        return Metadata(
            title,
            metadataText(o, "author") ?: "",
            metadataText(o, "year") ?: "",
            metadataText(o, "source") ?: "",
            o.filterKeys { it !in Metadata.KNOWN_KEYS },
        )
    }

    private fun metadataText(
        o: JsonObject,
        key: String,
    ): String? {
        val v = o[key]
        return when {
            v == null -> null
            v is JsonPrimitive && v.isString -> v.content
            else -> throw FormatException("$FILE: metadata.$key must be a string")
        }
    }

    fun decode(text: String): Parsed {
        val root = CanonicalJson.parse(text) as? JsonObject ?: throw FormatException("$FILE: expected an object")
        checkFormat(root)
        checkVersion(root)
        val pages =
            (
                root["pages"] as? JsonArray
                    ?: throw FormatException("$FILE: \"pages\" must be an array")
            ).mapIndexed { i, p -> decodePage(p, i) }
        return Parsed(
            pages,
            root["metadata"]?.let { decodeMetadata(it) },
            root.filterKeys { it !in setOf("format", "version", "metadata", "pages") },
        )
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
