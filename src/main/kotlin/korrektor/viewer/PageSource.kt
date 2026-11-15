package korrektor.viewer

import androidx.compose.ui.graphics.ImageBitmap
import korrektor.pdf.PixelRegion
import java.nio.file.Path

/** The size of a page as the user sees it, in points. */
data class PageSize(
    val width: Double,
    val height: Double,
)

/**
 * Where the viewer gets its pictures from. The real one reads the project's copy of the PDF ([PdfPageSource]);
 * UI tests put a fake in its place. Pages are numbered from 1. The functions suspend because rendering takes time
 * and never runs on the UI thread; a cancelled caller stops waiting for the result.
 */
interface PageSource : AutoCloseable {
    /**
     * Whether this source remembers its pictures. Only then does the viewer render the neighbours of the page in
     * the background: for a source without memory that work would be thrown away.
     */
    val caches: Boolean get() = false

    /** The size of [page] in points, after rotation and crop box. */
    suspend fun size(page: Int): PageSize

    /**
     * The scale (screen pixels per point) at which one pixel of the page's scan is one screen pixel, or null if the
     * page has no scan. Zooming in beyond it shows nothing new.
     */
    suspend fun nativeScale(page: Int): Double?

    /**
     * The [region] of [page] in pixels, when the whole page is as wide as its size in points times [scale].
     */
    suspend fun render(
        page: Int,
        scale: Double,
        region: PixelRegion,
    ): ImageBitmap
}

/** Makes the [PageSource] of a project's PDF. The app uses [PdfPageSource]; tests use a fake. */
fun interface PageSourceFactory {
    fun open(pdf: Path): PageSource
}
