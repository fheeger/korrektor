package korrektor.export

import korrektor.format.Box
import korrektor.format.Line
import korrektor.format.Metadata
import korrektor.format.PageInfo
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word
import java.nio.file.Files
import kotlin.io.path.writeBytes

/** The metadata of [withGoldenBook]. */
val GOLDEN_METADATA = Metadata("Der kleine Krieg", "Ewald", "1785", "Google Books")

private val BOX = Box(10.0, 10.0, 200.0, 12.0)

private fun line(
    id: String,
    text: String,
    box: Box? = BOX,
) = Line(id, box, if (box == null) null else "o$id", text.split(' ').map { Word(it, box) })

private fun layer(
    pdfPage: Int,
    vararg lines: Line,
) = PageLayer(
    pdfPage,
    300.0,
    400.0,
    if (lines.isEmpty()) emptyList() else listOf(Region("r1", RegionType.TEXT, 1, null, lines.toList())),
)

/** The lines of the golden book as they are stored, page by page: what every export of it must contain. */
val GOLDEN_PAGES: List<List<String>> =
    listOf(
        listOf("Abhandlung über den klei-", "nen Krieg, ſo wie ſie ſich", "zu Muͤnſter zutrug."),
        emptyList(),
        listOf("1. Von den Streifcorps", "*Anmerkung* _ſo_ [a](b) \\ <b>", "Zuſatz des Bearbeiters"),
    )

/**
 * A small book of three pages written into a new project: Fraktur characters (long s, a combining letter), a hyphen at
 * the end of a line, an empty page, a line that looks like a list, markup characters, and a line without a box (added
 * by the user, S05). Its PDF pages are 12, 13 and 14, so that a marker can be told from the position in the book.
 */
fun withGoldenBook(block: (Project) -> Unit) {
    val dir = Files.createTempDirectory("goldenbook")
    try {
        val pdf = dir.resolve("book.pdf").also { it.writeBytes("%PDF-1.4 stand-in".toByteArray()) }
        val project =
            Project.create(dir.resolve("g.korrektur"), pdf, listOf(12, 13, 14).map { PageInfo(it) }, GOLDEN_METADATA)
        val (first, _, third) = GOLDEN_PAGES
        project.writePage(1, layer(12, line("l1", first[0]), line("l2", first[1]), line("l3", first[2])))
        project.writePage(2, layer(13))
        project.writePage(3, layer(14, line("l1", third[0]), line("l2", third[1]), line("l3", third[2], box = null)))
        block(project)
    } finally {
        dir.toFile().walkBottomUp().forEach {
            it.setWritable(true)
            it.delete()
        }
    }
}
