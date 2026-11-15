package korrektor.viewer

import androidx.compose.ui.graphics.ImageBitmap
import korrektor.pdf.PixelRegion

/** What identifies a picture: the page, the scale and the part of the page. */
private data class PictureKey(
    val page: Int,
    val scale: Double,
    val region: PixelRegion,
)

/**
 * A [PageSource] that remembers what its [source] delivered, so that going back to a picture costs nothing.
 *
 * - **Pictures** are kept by page, scale and region. When together they take more than [budgetBytes] (4 bytes per
 *   pixel), the least recently used go first. A picture larger than the whole budget is returned but not kept.
 * - **Sizes and scan resolutions** of pages are small and never change, so they are kept for good.
 * - Nothing native is kept: the source loads a page for each render and closes it again.
 *
 * Several callers may ask at once (the page shown and a prefetch). The lock is held only while the map is looked at
 * or changed, never while a picture is being rendered, so a slow render does not hold up a cache hit.
 */
class PageCache(
    private val source: PageSource,
    val budgetBytes: Long = DEFAULT_BUDGET_BYTES,
) : PageSource {
    private val pictures = LinkedHashMap<PictureKey, ImageBitmap>(INITIAL_CAPACITY, LOAD_FACTOR, true)
    private val sizes = HashMap<Int, PageSize>()
    private val natives = HashMap<Int, NativeScale>()

    /** A scan resolution that may be "none": a page without a scan is known too. */
    private class NativeScale(
        val value: Double?,
    )

    /** The bytes the kept pictures take. Never more than [budgetBytes]. */
    @Volatile
    var usedBytes: Long = 0
        private set

    /** How many pictures are kept. */
    val pictureCount: Int get() = synchronized(pictures) { pictures.size }

    override val caches: Boolean get() = true

    override suspend fun size(page: Int): PageSize =
        synchronized(sizes) { sizes[page] } ?: source.size(page).also { synchronized(sizes) { sizes[page] = it } }

    override suspend fun nativeScale(page: Int): Double? {
        synchronized(natives) { natives[page] }?.let { return it.value }
        return source.nativeScale(page).also { synchronized(natives) { natives[page] = NativeScale(it) } }
    }

    override suspend fun render(
        page: Int,
        scale: Double,
        region: PixelRegion,
    ): ImageBitmap {
        val key = PictureKey(page, scale, region)
        synchronized(pictures) { pictures[key] }?.let { return it }
        val picture = source.render(page, scale, region)
        keep(key, picture)
        return picture
    }

    private fun keep(
        key: PictureKey,
        picture: ImageBitmap,
    ) {
        val bytes = bytesOf(picture)
        if (bytes > budgetBytes) return
        synchronized(pictures) {
            pictures.put(key, picture)?.let { usedBytes -= bytesOf(it) }
            usedBytes += bytes
            val oldest = pictures.entries.iterator()
            while (usedBytes > budgetBytes && oldest.hasNext()) {
                val entry = oldest.next()
                usedBytes -= bytesOf(entry.value)
                oldest.remove()
            }
        }
    }

    private fun bytesOf(picture: ImageBitmap): Long = picture.width.toLong() * picture.height * BYTES_PER_PIXEL

    /** Forgets everything and closes the source. */
    override fun close() {
        synchronized(pictures) {
            pictures.clear()
            usedBytes = 0
        }
        source.close()
    }

    private companion object {
        const val DEFAULT_BUDGET_BYTES = 256L * 1024 * 1024
        const val BYTES_PER_PIXEL = 4
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
