package korrektor.viewer

import androidx.compose.ui.graphics.ImageBitmap
import korrektor.pdf.PixelRegion
import kotlinx.coroutines.CompletableDeferred
import java.util.Collections

/** What the viewer asked a fake source for. */
data class RenderRequest(
    val page: Int,
    val scale: Double,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/**
 * A [PageSource] for tests. It answers with an empty picture of the asked size, but only once [gate] is completed
 * (open by default), fails with [failure] if that is set, and records every request and whether it was closed.
 */
class FakePageSource(
    private val size: PageSize = PageSize(200.0, 300.0),
    private val sizes: Map<Int, PageSize> = emptyMap(),
    gate: CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { it.complete(Unit) },
    private val failure: Exception? = null,
    private val native: Double? = null,
) : PageSource {
    /** Requests wait for this; a test can replace it to hold the next ones back. */
    @Volatile
    var gate: CompletableDeferred<Unit> = gate

    val requests: MutableList<RenderRequest> = Collections.synchronizedList(mutableListOf())

    /** The requests that were answered, not cancelled while waiting for the [gate]. */
    val completed: MutableList<RenderRequest> = Collections.synchronizedList(mutableListOf())

    @Volatile
    var closed = false
        private set

    override suspend fun size(page: Int): PageSize = sizes[page] ?: size

    override suspend fun nativeScale(page: Int): Double? = native

    override suspend fun render(
        page: Int,
        scale: Double,
        region: PixelRegion,
    ): ImageBitmap {
        requests += RenderRequest(page, scale, region.x, region.y, region.width, region.height)
        gate.await()
        failure?.let { throw it }
        completed += requests.last { it.page == page && it.width == region.width && it.scale == scale }
        return ImageBitmap(region.width, region.height)
    }

    override fun close() {
        closed = true
    }
}
