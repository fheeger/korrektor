package korrektor.format

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException

/**
 * A short reason for the user. The message of a file system exception is often only the path
 * (`AccessDeniedException: C:\Books\x`), which says nothing about what went wrong.
 */
fun IOException.reason(): String =
    when {
        this is FileSystemException && !reason.isNullOrBlank() -> reason!!
        this is AccessDeniedException -> "access denied"
        this is FileAlreadyExistsException -> "it already exists"
        this is NoSuchFileException -> "no such file or folder"
        this is NotDirectoryException -> "not a folder"
        this is DirectoryNotEmptyException -> "the folder is not empty"
        else -> message ?: javaClass.simpleName
    }
