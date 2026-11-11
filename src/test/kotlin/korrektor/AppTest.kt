package korrektor

import kotlin.test.Test
import kotlin.test.assertEquals

class AppTest {
    // Guards the toolchain: if the test setup breaks, CI fails here first.
    @Test
    fun appNameIsKorrektor() {
        assertEquals("korrektor", APP_NAME)
    }
}
