package korrektor.pdf

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfiumLocatorTest {
    private fun folderWithLibrary(): File =
        Files.createTempDirectory("pdfium-locator").toFile().also { File(it, PdfiumLocator.fileName).writeText("x") }

    @Test
    fun theAppResourcesComeBeforeTheLibraryPath() {
        val resources = folderWithLibrary()
        val dev = folderWithLibrary()
        val directories = PdfiumLocator.directories(resources.path, dev.path)
        assertEquals(listOf(resources, dev), directories)
        assertEquals(resources, PdfiumLocator.find(directories))
    }

    @Test
    fun placesThatAreNotSetAreLeftOutAndAPathListIsSplit() {
        assertEquals(emptyList(), PdfiumLocator.directories(null, null))
        assertEquals(emptyList(), PdfiumLocator.directories(" ", ""))
        val two = "a${File.pathSeparator}b"
        assertEquals(listOf(File("a"), File("b")), PdfiumLocator.directories(null, two))
    }

    @Test
    fun aFolderWithoutTheLibraryIsSkipped() {
        val empty = Files.createTempDirectory("pdfium-empty").toFile()
        val dev = folderWithLibrary()
        assertEquals(dev, PdfiumLocator.find(listOf(empty, dev)))
        assertNull(PdfiumLocator.find(listOf(empty)))
    }

    @Test
    fun aMissingLibraryIsReportedWithTheFileNameAndThePlacesSearched() {
        val empty = Files.createTempDirectory("pdfium-empty").toFile()
        val message = PdfiumLocator.searched(listOf(empty))
        assertTrue(PdfiumLocator.fileName in message, message)
        assertTrue(empty.absolutePath in message, message)
        assertTrue(PdfiumLocator.fileName in PdfiumLocator.searched(emptyList()))
    }

    @Test
    fun theLibraryOfTheBuildIsFoundInTheTestSetup() {
        // The test task sets jna.library.path to build/pdfium; this is the folder that the packaged app copies from.
        assertNotNull(PdfiumLocator.find(PdfiumLocator.directories()))
    }
}
