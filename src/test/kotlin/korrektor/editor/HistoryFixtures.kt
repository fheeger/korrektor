package korrektor.editor

import korrektor.format.EditEntry
import korrektor.format.HistoryCodec
import korrektor.format.HistoryEntry
import korrektor.format.Metadata
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.format.SavedEntry
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import java.nio.file.Files
import kotlin.io.path.readBytes
import kotlin.io.path.walk

// What the tests of the history (S08) have in common: a project made by the real import, edits through a real editor,
// and the files of a project as they are.

/** A project of [pages] pages with two lines each ("Seite n oben", "unten"), deleted afterwards. */
fun withHistoryProject(
    pages: Int = 4,
    block: (Project) -> Unit,
) {
    val dir = Files.createTempDirectory("history")
    try {
        val pdf = dir.resolve("book.pdf")
        TestPdfs.write(
            pdf,
            List(pages) { n ->
                TestPage(texts = listOf(TestText("Seite ${n + 1} oben", 30f, 250f), TestText("unten", 30f, 230f)))
            },
        )
        val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
        PdfImport.run(project)
        block(project)
    } finally {
        dir.toFile().walkBottomUp().forEach {
            it.setWritable(true)
            it.delete()
        }
    }
}

/** Every file of the project with its bytes, by path. */
fun filesOf(project: Project): Map<String, ByteArray> =
    project.folder
        .walk()
        .filter { Files.isRegularFile(it) && it.fileName.toString() != ".lock" }
        .associate { project.folder.relativize(it).toString() to it.readBytes() }

/** The entries of the project's history file, as the code reads them. */
fun historyOf(project: Project): List<HistoryEntry> = project.historyLog.readAll().map(HistoryCodec::decode)

/** The number of the entries of [project]'s history that are not [SavedEntry] and not [EditEntry]: undo and redo. */
fun undoRedoCount(project: Project): Int = historyOf(project).count { it !is SavedEntry && it !is EditEntry }

/** A page layer without its history number, for comparing what is on the page. */
fun PageLayer.plain(): PageLayer = copy(history = null)

/** The text of the lines of [page] of [project] in reading order. */
fun Project.texts(page: Int): List<String> = PageEdits.readingOrder(readPage(page)).map { it.text }
