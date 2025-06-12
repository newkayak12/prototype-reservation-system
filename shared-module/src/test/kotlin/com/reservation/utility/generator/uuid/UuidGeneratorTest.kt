package com.reservation.utility.generator.uuid

import com.reservation.utilities.generator.uuid.UuidGenerator
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class UuidGeneratorTest {
    @DisplayName("uuid 생성 확인")
    @Test
    fun `uuid is not empty`() {
        assertThat(UuidGenerator.generate()).isNotEmpty()
    }
}
