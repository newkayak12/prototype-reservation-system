package com.reservation.restaurant.usecase

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.common.exceptions.AlreadyPersistedException
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.restaurant.exceptions.InvalidateRestaurantElementException
import com.reservation.restaurant.port.input.CreateRestaurantCommand.CreateProductCommandDto
import com.reservation.restaurant.port.output.CheckRestaurantDuplicated
import com.reservation.restaurant.port.output.CreateRestaurant
import com.reservation.restaurant.service.CreateRestaurantService
import com.reservation.restaurant.snapshot.RestaurantSnapshot
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import net.jqwik.api.Arbitraries
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class CreateRestaurantUseCaseTest {
    @MockK
    private lateinit var checkRestaurantDuplicated: CheckRestaurantDuplicated

    @MockK
    private lateinit var createRestaurantService: CreateRestaurantService

    @MockK
    private lateinit var createRestaurant: CreateRestaurant

    @InjectMockKs
    private lateinit var useCase: CreateRestaurantUseCase

    @DisplayName("[실패]")
    @Nested
    inner class Failure {
        /**
         * Scenario: 이미 등록되어 있는 음식점을 등록해서 등록에 실패한다.
         * Given: 중복되는 이름을 가진 음식점을 등록 요청하여
         * When: 음식점 등록 시
         * Then: 중복됐음을 알리며 등록에 실패한다. (AlreadyPersistedException)
         */
        @DisplayName("이미 등록되어 있는 음식점을 등록해서 등록에 실패한다.")
        @Test
        fun `fail with duplicated restaurant name`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val request = pureMonkey.giveMeOne<CreateProductCommandDto>()

            every {
                checkRestaurantDuplicated.query(any())
            } returns true

            assertThrows<AlreadyPersistedException> {
                useCase.execute(request)
            }
            verify(exactly = 1) { checkRestaurantDuplicated.query(any()) }
        }

        /**
         * Scenario: 요청 사항 중 정합성에 맞지 않은 필드가 있어서 등록에 실패한다.
         * Given: 정합성에 맞지 않은 양식으로 음식점 등록을 요청하여
         * When: 음식점 등록 시
         * Then: 등록할 수 없음을 알리며 등록에 실패한다. (InvalidateRestaurantElementException)
         */
        @DisplayName("요청 사항 중 정합성에 맞지 않은 필드가 있어서 등록에 실패한다.")
        @Test
        fun `fail with invalid restaurant form`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val request = pureMonkey.giveMeOne<CreateProductCommandDto>()

            every {
                checkRestaurantDuplicated.query(any())
            } returns false

            every {
                createRestaurantService.create(any())
            } throws InvalidateRestaurantElementException(Arbitraries.strings().sample())

            assertThrows<InvalidateRestaurantElementException> {
                useCase.execute(request)
            }

            verify(exactly = 1) { checkRestaurantDuplicated.query(any()) }
            verify(exactly = 1) { createRestaurantService.create(any()) }
        }
    }

    @DisplayName("[성공]")
    @Nested
    inner class Success {
        /**
         * Scenario: 정상적인 요청으로 음식점 등록에 성공한다.
         * Given: 정상적읜 양식으로 음식점 등록을 요청하여
         * When: 음식점 등록 시
         * Then: 등록에 성공한다.
         */
        @DisplayName("정상적인 요청으로 음식점 등록에 성공한다.")
        @Test
        fun createRestaurantSuccessfully() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val request = pureMonkey.giveMeOne<CreateProductCommandDto>()
            val snapshot = pureMonkey.giveMeOne<RestaurantSnapshot>()

            every {
                checkRestaurantDuplicated.query(any())
            } returns false

            every {
                createRestaurantService.create(any())
            } returns snapshot

            every {
                createRestaurant.command(any())
            } returns true

            val result = useCase.execute(request)

            verify(exactly = 1) { checkRestaurantDuplicated.query(any()) }
            verify(exactly = 1) { createRestaurantService.create(any()) }
            verify(exactly = 1) { createRestaurant.command(any()) }
            assertThat(result).isTrue()
        }
    }
}
