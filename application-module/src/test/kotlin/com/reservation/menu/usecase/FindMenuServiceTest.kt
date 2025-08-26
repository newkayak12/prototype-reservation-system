package com.reservation.menu.usecase

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.menu.port.output.FindMenu
import com.reservation.menu.port.output.FindMenu.FindMenuPhotoResult
import com.reservation.menu.port.output.FindMenu.FindMenuResult
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
@DisplayName("메뉴 리스트 중 하나의 메뉴를 조회한다.")
class FindMenuServiceTest {
    @DisplayName("없는 식별 값으로 메뉴 조회 요청을 하고 ")
    @Nested
    inner class `Request not exists item` {
        @MockK
        private lateinit var findMenu: FindMenu

        @InjectMockKs
        private lateinit var service: FindMenuService

        @DisplayName("결과적으로 NoSuchPersistedElementException를 반환한다. ")
        @Test
        fun throwsNoSuchPersistedElementException() {
            val id = UuidGenerator.generate()

            every { findMenu.query(any()) } returns null

            assertThrows<NoSuchPersistedElementException> {
                service.execute(id)
            }

            verify(exactly = 1) { findMenu.query(any()) }
        }
    }

    @DisplayName("존재하는 식별 값으로 메뉴 조회 요청을 하고 ")
    @Nested
    inner class `Request exists item` {
        @MockK
        private lateinit var findMenu: FindMenu

        @InjectMockKs
        private lateinit var service: FindMenuService

        @DisplayName("결과적으로 사진이 없는 메뉴를 반환한다.")
        @Test
        fun `return no photo result`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val id = UuidGenerator.generate()
            val item =
                pureMonkey.giveMeBuilder<FindMenuResult>()
                    .set("photos", listOf<FindMenuPhotoResult>())
                    .sample()

            every { findMenu.query(any()) } returns item

            val result = service.execute(id)

            assertThat(result).isNotNull
            assertThat(result.photos).isEmpty()
            verify(exactly = 1) { findMenu.query(any()) }
        }

        @DisplayName("결과적으로 사진이 있는 메뉴를 반환한다.")
        @Test
        fun `return result`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val id = UuidGenerator.generate()
            val item =
                pureMonkey.giveMeBuilder<FindMenuResult>()
                    .set("photos", pureMonkey.giveMe<FindMenuPhotoResult>(5))
                    .sample()

            every { findMenu.query(any()) } returns item

            val result = service.execute(id)

            assertThat(result).isNotNull
            assertThat(result.photos).isNotEmpty()
            verify(exactly = 1) { findMenu.query(any()) }
        }
    }
}
