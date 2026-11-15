package korrektor.viewer

import korrektor.pdf.PixelRegion
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PageCacheTest {
    /** A picture of this region takes width x height x 4 bytes: 10 x 10 pixels are 400 bytes. */
    private val small = PixelRegion(0, 0, 10, 10)

    private fun renders(source: FakePageSource) = source.requests.size

    @Test
    fun aPictureIsRenderedOnceAndThenComesFromTheCache() =
        runBlocking {
            val source = FakePageSource()
            val cache = PageCache(source, budgetBytes = 10_000)

            val first = cache.render(1, 2.0, small)
            val second = cache.render(1, 2.0, small)

            assertSame(first, second)
            assertEquals(1, renders(source))
            assertEquals(400, cache.usedBytes)
        }

    @Test
    fun theKeyIsPageScaleAndRegion() =
        runBlocking {
            val source = FakePageSource()
            val cache = PageCache(source, budgetBytes = 100_000)

            cache.render(1, 2.0, small)
            cache.render(2, 2.0, small)
            cache.render(1, 3.0, small)
            cache.render(1, 2.0, PixelRegion(5, 0, 10, 10))

            assertEquals(4, renders(source), "each differs in one part of the key")
            assertEquals(4, cache.pictureCount)
        }

    @Test
    fun theLeastRecentlyUsedPictureGoesFirstAndAHitMakesAPictureRecent() =
        runBlocking {
            val source = FakePageSource()
            val cache = PageCache(source, budgetBytes = 1200) // three pictures of 400 bytes
            for (page in 1..3) cache.render(page, 1.0, small)
            cache.render(1, 1.0, small) // a hit: page 1 is the most recent now, page 2 the oldest
            assertEquals(3, renders(source))

            cache.render(4, 1.0, small) // does not fit: page 2 goes

            assertEquals(1200, cache.usedBytes)
            cache.render(1, 1.0, small)
            cache.render(3, 1.0, small)
            cache.render(4, 1.0, small)
            assertEquals(4, renders(source), "pages 1, 3 and 4 are still there")
            cache.render(2, 1.0, small)
            assertEquals(5, renders(source), "page 2 was dropped")
        }

    @Test
    fun theCacheNeverHoldsMoreThanItsBudget() =
        runBlocking {
            val source = FakePageSource()
            val cache = PageCache(source, budgetBytes = 2000)

            repeat(200) { page ->
                cache.render(page + 1, 1.0, small)
                assertTrue(cache.usedBytes <= 2000, "after page ${page + 1}: ${cache.usedBytes}")
            }

            assertEquals(5, cache.pictureCount, "2000 bytes hold five pictures of 400")
        }

    @Test
    fun aPictureLargerThanTheBudgetIsReturnedButNotKept() =
        runBlocking {
            val source = FakePageSource()
            val cache = PageCache(source, budgetBytes = 300)

            val picture = cache.render(1, 1.0, small)

            assertEquals(10 to 10, picture.width to picture.height)
            assertEquals(0, cache.usedBytes)
            assertEquals(0, cache.pictureCount)
            cache.render(1, 1.0, small)
            assertEquals(2, renders(source), "so it is rendered again")
        }

    @Test
    fun aLargePictureThrowsOutSeveralSmallOnes() =
        runBlocking {
            val source = FakePageSource()
            val cache = PageCache(source, budgetBytes = 2000)
            for (page in 1..5) cache.render(page, 1.0, small)

            cache.render(6, 1.0, PixelRegion(0, 0, 20, 20)) // 1600 bytes

            assertTrue(cache.usedBytes <= 2000)
            assertEquals(2, cache.pictureCount, "the 1600 bytes and one small picture")
        }

    @Test
    fun sizesAndScanResolutionsAreAskedForOnce() =
        runBlocking {
            val source = FakePageSource(PageSize(200.0, 300.0), native = 4.0)
            val withoutScan = FakePageSource()
            val cache = PageCache(source)
            val cacheWithoutScan = PageCache(withoutScan)

            repeat(3) {
                assertEquals(PageSize(200.0, 300.0), cache.size(1))
                assertEquals(4.0, cache.nativeScale(1))
                assertNull(cacheWithoutScan.nativeScale(1))
            }

            assertEquals(1, source.sizeCalls.get())
            assertEquals(1, source.nativeCalls.get())
            assertEquals(1, withoutScan.nativeCalls.get(), "a page without a scan is remembered too")
        }

    @Test
    fun closingEmptiesTheCacheAndClosesTheSource() =
        runBlocking {
            val source = FakePageSource()
            val cache = PageCache(source)
            cache.render(1, 1.0, small)

            cache.close()

            assertEquals(0, cache.usedBytes)
            assertEquals(0, cache.pictureCount)
            assertTrue(source.closed)
        }

    @Test
    fun aCacheSaysItCachesAndAPlainSourceDoesNot() {
        assertTrue(PageCache(FakePageSource()).caches)
        assertEquals(false, FakePageSource().caches)
    }
}
