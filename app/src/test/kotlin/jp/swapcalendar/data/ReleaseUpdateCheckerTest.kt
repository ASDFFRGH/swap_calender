package jp.swapcalendar.data

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReleaseUpdateCheckerTest {
    @Test
    fun `detects newer semantic versions`() {
        assertTrue(ReleaseUpdateChecker.isNewerVersion("0.4.0", "0.3.0"))
        assertTrue(ReleaseUpdateChecker.isNewerVersion("1.0.0", "0.99.99"))
    }

    @Test
    fun `does not prompt for equal or older versions`() {
        assertFalse(ReleaseUpdateChecker.isNewerVersion("0.3.0", "0.3.0"))
        assertFalse(ReleaseUpdateChecker.isNewerVersion("0.2.9", "0.3.0"))
    }
}
