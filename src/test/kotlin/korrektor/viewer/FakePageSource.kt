package korrektor.viewer

import androidx.compose.ui.graphics.ImageBitmap
import korrektor.pdf.PixelRegion
import kotlinx.coroutines.CompletableDeferred
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

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
    override val caches: Boolean = false,
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

    /** How often the size of a page and its scan resolution were asked for. */
    val sizeCalls = AtomicInteger()
    val nativeCalls = AtomicInteger()

    /** If set, only renders of these pages wait for the [gate]; the others are answered at once. */
    @Volatile
    var onlyPages: Set<Int>? = null

    override suspend fun size(page: Int): PageSize {
        sizeCalls.incrementAndGet()
        return sizes[page] ?: size
    }

    override suspend fun nativeScale(page: Int): Double? {
        nativeCalls.incrementAndGet()
        return native
    }

    override suspend fun render(
        page: Int,
        scale: Double,
        region: PixelRegion,
    ): ImageBitmap {
        requests += RenderRequest(page, scale, region.x, region.y, region.width, region.height)
        if (onlyPages?.contains(page) != false) gate.await()
        failure?.let { throw it }
        completed += requests.last { it.page == page && it.width == region.width && it.scale == scale }
        return ImageBitmap(region.width, region.height)
    }

    override fun close() {
        closed = true
    }
}
