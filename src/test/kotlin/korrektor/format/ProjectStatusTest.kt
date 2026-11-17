package korrektor.format

import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The page status in `project.json` (S09, T076). */
class ProjectStatusTest {
    private fun withProject(
        pages: Int = 4,
        block: (Project) -> Unit,
    ) {
        val dir = Files.createTempDirectory("status")
        try {
            val pdf = dir.resolve("b.pdf").also { it.writeBytes("%PDF-1.4 stand-in".toByteArray()) }
            block(Project.create(dir.resolve("p.korrektur"), pdf, (1..pages).map { PageInfo(it) }, Metadata("T")))
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun Project.file(): Path = folder.resolve("project.json")

    @Test
    fun aNewProjectHasNoStatusFieldAndEveryPageIsUnchecked() =
        withProject { project ->
            assertTrue("status" !in project.file().readText())
            assertEquals(emptyMap(), project.statuses)
            assertTrue(project.pages.all { it.status == PageStatus.UNCHECKED })
        }

    @Test
    fun statusesAreWrittenAndReadBackAndUncheckedWritesNothing() =
        withProject { project ->
            project.setStatuses(mapOf(2 to PageStatus.CORRECTED, 4 to PageStatus.CORRECTED))

            val text = project.file().readText()
            assertTrue("{\"pdfPage\": 2, \"status\": \"corrected\"}" in text, text)
            assertTrue("{\"pdfPage\": 3}" in text, text)
            val reopened = Project.open(project.folder)
            assertEquals(mapOf(2 to PageStatus.CORRECTED, 4 to PageStatus.CORRECTED), reopened.statuses)

            reopened.setStatuses(mapOf(4 to PageStatus.CORRECTED))
            assertEquals(mapOf(4 to PageStatus.CORRECTED), Project.open(project.folder).statuses)
            reopened.setStatuses(emptyMap())
            assertTrue("status" !in project.file().readText())
        }

    @Test
    fun unknownFieldsOfAPageEntryAreKeptAndSortedWithTheStatus() =
        withProject(1) { project ->
            project.file().writeText(
                project.file().readText().replace("{\"pdfPage\": 1}", "{\"pdfPage\": 1, \"zeta\": 7, \"alpha\": true}"),
            )
            val opened = Project.open(project.folder)
            opened.setStatuses(mapOf(1 to PageStatus.CORRECTED))

            assertTrue(
                "{\"pdfPage\": 1, \"alpha\": true, \"status\": \"corrected\", \"zeta\": 7}" in opened.file().readText(),
            )
            assertEquals(
                mapOf("alpha" to JsonPrimitive(true), "zeta" to JsonPrimitive(7)),
                Project
                    .open(project.folder)
                    .pages
                    .single()
                    .extra,
            )
        }

    @Test
    fun aStatusOfALaterVersionSurvivesAsItIs() =
        withProject(2) { project ->
            project.file().writeText(
                project.file().readText().replace(
                    "{\"pdfPage\": 2}",
                    "{\"pdfPage\": 2, \"status\": \"needs attention\"}",
                ),
            )
            val opened = Project.open(project.folder)
            assertEquals(mapOf(2 to PageStatus("needs attention")), opened.statuses)
            opened.saveProjectFile()
            assertTrue("\"status\": \"needs attention\"" in opened.file().readText())
        }

    @Test
    fun aStatusThatIsNotANonBlankStringIsAnErrorNamingTheEntry() =
        withProject(2) { project ->
            for (bad in listOf("1", "\"\"", "null", "[]")) {
                project.file().writeText(
                    project.file().readText().replace(
                        Regex("""\{"pdfPage": 2[^}]*}"""),
                        "{\"pdfPage\": 2, \"status\": $bad}",
                    ),
                )
                val e = assertFailsWith<FormatException>(bad) { Project.open(project.folder) }
                assertTrue("pages[1].status" in e.message!!, e.message)
            }
        }

    @Test
    fun settingStatusesWritesOnlyTheProjectFile() =
        withProject { project ->
            project.writePage(1, PageLayer(1, 100.0, 200.0, emptyList()))
            val before = project.folder.resolve("pages/0001.json").readText()
            project.setStatuses(mapOf(1 to PageStatus.CORRECTED))
            assertEquals(before, project.folder.resolve("pages/0001.json").readText())
        }

    @Test
    fun ifWritingFailsTheOldStatusesStay() =
        withProject { project ->
            project.setStatuses(mapOf(1 to PageStatus.CORRECTED))
            // A directory where the temporary file goes makes the atomic write fail.
            Files.createDirectory(project.folder.resolve("project.json.tmp"))
            assertFailsWith<IOException> { project.setStatuses(mapOf(2 to PageStatus.CORRECTED)) }
            assertEquals(mapOf(1 to PageStatus.CORRECTED), project.statuses)
            assertEquals(mapOf(1 to PageStatus.CORRECTED), Project.open(project.folder).statuses)
        }

    @Test
    fun aBlankStatusCannotBeMade() {
        assertFailsWith<IllegalArgumentException> { PageStatus(" ") }
        assertFailsWith<IllegalArgumentException> { PageInfo(1, mapOf("status" to JsonPrimitive("x"))) }
    }
}
