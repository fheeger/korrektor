package korrektor.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import korrektor.pdf.PdfDocument
import korrektor.pdf.PixelRegion
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.Executors

/** The real [PageSourceFactory]: the pages of the PDF, drawn by PDFium. */
val DefaultPageSourceFactory = PageSourceFactory { PdfPageSource(it) }

/**
 * The pages of a PDF, drawn by PDFium on a thread of their own. PDFium runs one call at a time anyway (D02), so one
 * thread loses nothing, and the UI thread is never blocked by a render. The PDF is opened on the first request and
 * closed by [close]; a page is loaded for each request and closed again, so nothing native is kept between them.
 */
class PdfPageSource(
    private val pdf: Path,
) : PageSource {
    private val executor =
        Executors.newSingleThreadExecutor { task -> Thread(task, "page-renderer").also { it.isDaemon = true } }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val lazyDocument = lazy { PdfDocument.open(pdf) }
    private val document get() = lazyDocument.value

    override suspend fun size(page: Int): PageSize =
        withContext(dispatcher) { document.page(page - 1).use { PageSize(it.width, it.height) } }

    override suspend fun nativeScale(page: Int): Double? =
        withContext(dispatcher) { document.page(page - 1).use { it.largestImageDensity() } }

    override suspend fun render(
        page: Int,
        scale: Double,
        region: PixelRegion,
    ): ImageBitmap =
        withContext(dispatcher) {
            document.page(page - 1).use { it.renderRegion(scale, region) }.toComposeImageBitmap()
        }

    override fun close() {
        // Closing is queued behind a render that is running, which finishes first; later requests find it closed.
        executor.execute { if (lazyDocument.isInitialized()) document.close() }
        executor.shutdown()
    }
}
