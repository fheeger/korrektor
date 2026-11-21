package korrektor

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/** The script that takes the text of a release from CHANGELOG.md (T103); the release workflow depends on it. */
class ChangelogSectionTest {
    private class Result(
        val code: Int,
        val out: String,
    )

    /**
     * Git's bash on Windows. A plain "bash" there can be the one of the Windows Subsystem for Linux, which sits earlier
     * in the PATH and fails when no Linux distribution is installed (as on the CI runners).
     */
    private fun bash(): String {
        if (!System.getProperty("os.name").startsWith("Windows")) return "bash"
        val roots =
            listOfNotNull(
                System.getenv("ProgramFiles"),
                System.getenv("ProgramFiles(x86)"),
                System.getenv("LOCALAPPDATA")?.let { "$it/Programs" },
            )
        return roots.map { File(it, "Git/bin/bash.exe") }.firstOrNull { it.isFile }?.path ?: "bash"
    }

    /** The bash of WSL starts but cannot run anything without a distribution; such a bash is not a reason to fail. */
    private fun assumeBashWorks() {
        val works =
            try {
                ProcessBuilder(bash(), "-c", "exit 0").redirectErrorStream(true).start().let {
                    it.inputStream.readBytes()
                    it.waitFor(30, TimeUnit.SECONDS) && it.exitValue() == 0
                }
            } catch (_: java.io.IOException) {
                false
            }
        assumeTrue(works, "bash is not available")
    }

    private fun run(
        version: String,
        changelog: String,
    ): Result {
        assumeBashWorks()
        val file = Files.createTempFile("changelog", ".md").toFile().apply { writeText(changelog) }
        val process =
            ProcessBuilder(bash(), File("scripts/changelog-section.sh").path, version, file.path)
                .redirectErrorStream(true)
                .start()
        val out = process.inputStream.readBytes().toString(Charsets.UTF_8)
        check(process.waitFor(30, TimeUnit.SECONDS)) { "the script did not finish" }
        return Result(process.exitValue(), out)
    }

    private val log =
        """
        # Changelog

        ## Unreleased

        ## 0.2.0

        ### Fixed
        - something

        ## 0.1.0-beta.1

        First beta.

        ### Added
        - a thing

        ## 0.1.0
        - the final
        """.trimIndent()

    @Test
    fun theSectionOfAVersionWithoutItsHeadingAndWithoutBlankLinesAtTheEdges() {
        val result = run("0.1.0-beta.1", log)
        assertEquals(0, result.code)
        assertEquals("First beta.\n\n### Added\n- a thing\n", result.out.replace("\r\n", "\n"))
    }

    @Test
    fun aVersionIsNotFoundByItsStart() {
        // 0.1.0 is not the same as 0.1.0-beta.1, and the last section runs to the end of the file.
        val result = run("0.1.0", log)
        assertEquals(0, result.code)
        assertEquals("- the final\n", result.out.replace("\r\n", "\n"))
    }

    @Test
    fun aMissingOrEmptySectionFails() {
        assertEquals(1, run("0.3.0", log).code)
        assertEquals(1, run("Unreleased", log).code)
    }

    @Test
    fun windowsLineEndingsAreFine() {
        val result = run("0.2.0", log.replace("\n", "\r\n"))
        assertEquals(0, result.code)
        assertEquals("### Fixed\n- something\n", result.out.replace("\r\n", "\n"))
    }

    @Test
    fun theRealChangelogHasNotesForTheVersionOfTheBuild() {
        val result = run(AppInfo.version, File("CHANGELOG.md").readText())
        assertEquals(0, result.code, result.out)
    }
}
