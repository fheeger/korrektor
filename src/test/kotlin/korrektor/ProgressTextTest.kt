package korrektor

import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressTextTest {
    @Test
    fun theProgressIsSaidWithARoundedPercentage() {
        assertEquals("57 of 165 corrected (35 %)", progressText(57, 165))
        assertEquals("0 of 165 corrected (0 %)", progressText(0, 165))
        assertEquals("0 of 0 corrected (0 %)", progressText(0, 0))
    }

    @Test
    fun itNeverSaysDoneBeforeAllAreAndNeverZeroOnceOneIs() {
        assertEquals("1 of 1000 corrected (1 %)", progressText(1, 1000))
        assertEquals("999 of 1000 corrected (99 %)", progressText(999, 1000))
        assertEquals("1000 of 1000 corrected (100 %)", progressText(1000, 1000))
        assertEquals("1 of 100000 corrected (1 %)", progressText(1, 100_000))
    }
}
