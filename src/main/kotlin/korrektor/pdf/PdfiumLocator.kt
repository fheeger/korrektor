package korrektor.pdf

import java.io.File

/**
 * Where the PDFium library is looked for. In the packaged app (S12) it lies in the app's resources folder, which the
 * Compose launcher names in `compose.application.resources.dir`; in development and in tests it is in the folder of
 * `jna.library.path` (the build downloads it to `build/pdfium`). The packaged place comes first, so that a
 * development setting cannot hide the library that was shipped.
 */
internal object PdfiumLocator {
    /** The file name of the library on this system, for example `pdfium.dll`. */
    val fileName: String = System.mapLibraryName("pdfium")

    /** The places to look in, in order; places that are not set are left out. */
    fun directories(
        resourcesDir: String? = System.getProperty("compose.application.resources.dir"),
        libraryPath: String? = System.getProperty("jna.library.path"),
    ): List<File> =
        buildList {
            resourcesDir?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
            libraryPath
                ?.split(File.pathSeparator)
                ?.filter { it.isNotBlank() }
                ?.forEach { add(File(it)) }
        }

    /** The first of [directories] that holds the library, or null. */
    fun find(directories: List<File>): File? = directories.firstOrNull { File(it, fileName).isFile }

    /** What to tell the user when the library cannot be loaded: the file name and the places that were searched. */
    fun searched(directories: List<File>): String =
        if (directories.isEmpty()) {
            "$fileName was not looked for in a folder next to the app, and no library path is set"
        } else {
            "$fileName was not found in ${directories.joinToString(", ") { it.absolutePath }}"
        }
}
