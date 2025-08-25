package com.reservation.menu.usecase

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.menu.port.output.FindMenus
import com.reservation.menu.port.output.FindMenus.FindMenusResult
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
@DisplayName("메뉴 리스트를 조회할 때")
class FindMenusServiceTest {
    @DisplayName("메뉴가 없는 레스토랑의 메뉴를 조회했을 때")
    @Nested
    inner class `Empty Item` {
        @MockK
        private lateinit var findMenus: FindMenus

        @InjectMockKs
        private lateinit var useCase: FindMenusService

        @DisplayName("조회되는 사항이 아무것도 없다.")
        @Test
        fun `Empty Result`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val id = pureMonkey.giveMeOne<String>()
            val queryResult = emptyList<FindMenusResult>()

            every {
                findMenus.query(any())
            } returns queryResult

            val result = useCase.execute(id)

            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("메뉴가 12개 있는 레스토랑에 메뉴를 조회했을 때 ")
    inner class `Exists 12 Items` {
        @MockK
        private lateinit var findMenus: FindMenus

        @InjectMockKs
        private lateinit var useCase: FindMenusService

        @DisplayName("12개의 아이템이 조회된다. 없다.")
        @Test
        fun `Find 12 Result`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val id = pureMonkey.giveMeOne<String>()
            val size = 12
            val queryResult = pureMonkey.giveMe<FindMenusResult>(size)

            every {
                findMenus.query(any())
            } returns queryResult

            val result = useCase.execute(id)

            assertThat(result).isNotEmpty()
            assertThat(result).hasSize(size)
        }
    }
}
