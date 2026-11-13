package korrektor.format

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import kotlin.test.Test
import kotlin.test.assertEquals

class IoErrorsTest {
    @Test
    fun fileSystemExceptionsWithoutAReasonGetReadableText() {
        assertEquals("access denied", AccessDeniedException("C:/Books/x").reason())
        assertEquals("it already exists", FileAlreadyExistsException("C:/Books/x").reason())
        assertEquals("no such file or folder", NoSuchFileException("C:/Books/x").reason())
        assertEquals("not a folder", NotDirectoryException("C:/Books/x").reason())
        assertEquals("the folder is not empty", DirectoryNotEmptyException("C:/Books/x").reason())
    }

    @Test
    fun aReasonFromTheSystemIsUsedAsItIs() {
        val busy = FileSystemException("C:/Books/x", null, "The file is being used by another process")
        assertEquals("The file is being used by another process", busy.reason())
        assertEquals("access denied", AccessDeniedException("C:/Books/x", null, " ").reason())
    }

    @Test
    fun otherExceptionsKeepTheirMessage() {
        assertEquals("disk full", IOException("disk full").reason())
        assertEquals("IOException", IOException().reason())
    }
}
