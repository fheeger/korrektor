package korrektor.search

import korrektor.format.Metadata
import korrektor.format.PageInfo
import korrektor.format.Project
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes

/** The words that a generated page is made of: a bit of the Abhandlung, so that the lines are about as long. */
private val WORDS =
    "Der kleine Krieg ist die Fortsetzung der Politik mit anderen Mitteln und der Anführer eines leichten Corps".split(
        ' ',
    )

/**
 * A book of [pages] pages written straight into a new project (no PDF import: it would take minutes), 30 lines of 10
 * words on each. Every page has the rare word "Zebra" once, in line 7, and "der" many times. For the speed of the
 * search (S10): it is read from page files like a real book.
 */
fun withBigBook(
    pages: Int,
    block: (Project) -> Unit,
) {
    val dir = Files.createTempDirectory("bigbook")
    try {
        val pdf: Path = dir.resolve("book.pdf").also { it.writeBytes("%PDF-1.4 stand-in".toByteArray()) }
        val project = Project.create(dir.resolve("b.korrektur"), pdf, (1..pages).map { PageInfo(it) }, Metadata("B"))
        for (n in 1..pages) {
            val lines =
                (1..LINES).map { line ->
                    val words = (0 until WORDS_PER_LINE).map { WORDS[(line + it + n) % WORDS.size] }.toMutableList()
                    if (line == ZEBRA_LINE) words[2] = "Zebra"
                    words.joinToString(" ")
                }
            project.writePage(n, layerOf(*lines.toTypedArray(), number = n))
        }
        block(project)
    } finally {
        dir.toFile().walkBottomUp().forEach {
            it.setWritable(true)
            it.delete()
        }
    }
}

private const val LINES = 30
private const val WORDS_PER_LINE = 10
const val ZEBRA_LINE = 7
