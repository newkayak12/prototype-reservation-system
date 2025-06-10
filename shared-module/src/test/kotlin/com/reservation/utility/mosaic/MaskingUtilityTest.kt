package com.reservation.utility.mosaic

import com.reservation.utilities.mosaic.MaskingUtility.manipulate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MaskingUtilityTest {
    @DisplayName("strong이라는 글자를 마스킹 한다.")
    @Test
    fun `manipulate should have correct length`() {
        val target = "strong1234"
        val expected = "st******34"

        assertEquals(expected, manipulate(target))
    }
}
