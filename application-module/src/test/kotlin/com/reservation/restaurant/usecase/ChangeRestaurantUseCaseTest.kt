package com.reservation.restaurant.usecase

import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.restaurant.Restaurant
import com.reservation.restaurant.port.input.UpdateRestaurantCommand.ChangeRestaurantCommandDto
import com.reservation.restaurant.port.output.ChangeRestaurant
import com.reservation.restaurant.port.output.LoadRestaurant
import com.reservation.restaurant.port.output.LoadRestaurant.LoadRestaurantResult
import com.reservation.restaurant.port.output.UploadRestaurantImageFile
import com.reservation.restaurant.service.ChangeRestaurantService
import com.reservation.restaurant.snapshot.RestaurantSnapshot
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile

@ExtendWith(MockKExtension::class)
@DisplayName("ChangeRestaurantUseCase의 execute 메소드는")
class ChangeRestaurantUseCaseTest {
    @Nested
    @DisplayName("존재하지 않는 레스토랑 ID가 주어졌을 때")
    inner class `Context with non existent restaurant id` {
        @MockK
        private lateinit var changeRestaurantService: ChangeRestaurantService

        @MockK
        private lateinit var loadRestaurant: LoadRestaurant

        @MockK
        private lateinit var changeRestaurant: ChangeRestaurant

        @MockK
        private lateinit var uploadRestaurantImageFile: UploadRestaurantImageFile

        @InjectMockKs
        private lateinit var useCase: ChangeRestaurantUseCase

        @Test
        @DisplayName("NoSuchPersistedElementException을 던진다")
        fun `it throws NoSuchPersistedElementException`() {
            // given
            val restaurantId = "1"
            val userId = "1"
            val request = mockk<ChangeRestaurantCommandDto>()

            every { request.id } returns restaurantId
            every { request.userId } returns userId
            every { loadRestaurant.query(any()) } returns null

            // when & then
            assertThrows<NoSuchPersistedElementException> {
                useCase.execute(request)
            }

            verify(exactly = 0) {
                changeRestaurantService.change(any(), any())
                changeRestaurant.command(any())
                uploadRestaurantImageFile.execute(any())
            }
        }
    }

    @Nested
    @DisplayName("사진 업로드에 실패했을 때")
    inner class `Context with failed photo upload` {
        @MockK
        private lateinit var changeRestaurantService: ChangeRestaurantService

        @MockK
        private lateinit var loadRestaurant: LoadRestaurant

        @MockK
        private lateinit var changeRestaurant: ChangeRestaurant

        @MockK
        private lateinit var uploadRestaurantImageFile: UploadRestaurantImageFile

        @InjectMockKs
        private lateinit var useCase: ChangeRestaurantUseCase

        @Test
        @DisplayName("관련 예외를 던지고, 레스토랑 정보는 수정되지 않는다")
        fun `it throws an exception and does not update the restaurant`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

            // given
            val restaurantId = "1"
            val userId = "1"
            val request = mockk<ChangeRestaurantCommandDto>()
            val loadResult = mockk<LoadRestaurantResult>()
            val domain = pureMonkey.giveMeOne<Restaurant>()

            every { request.id } returns restaurantId
            every { request.userId } returns userId
            every { loadRestaurant.query(any()) } returns loadResult
            every { loadResult.toDomain() } returns domain
            every { uploadRestaurantImageFile.execute(any()) } throws RuntimeException()

            // when & then
            assertThrows<RuntimeException> {
                useCase.execute(request)
            }
            verify(exactly = 0) { changeRestaurantService.change(any(), any()) }
        }
    }

    @Nested
    @DisplayName("데이터베이스 업데이트에 실패했을 때")
    inner class `Context with failed database update` {
        @MockK
        private lateinit var changeRestaurantService: ChangeRestaurantService

        @MockK
        private lateinit var loadRestaurant: LoadRestaurant

        @MockK
        private lateinit var changeRestaurant: ChangeRestaurant

        @MockK
        private lateinit var uploadRestaurantImageFile: UploadRestaurantImageFile

        @InjectMockKs
        private lateinit var useCase: ChangeRestaurantUseCase

        @Test
        @DisplayName("관련 예외를 던지고, 변경 사항은 롤백된다")
        fun `it throws an exception and rolls back the changes`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            // given
            val restaurantId = "1"
            val userId = "1"
            val request = mockk<ChangeRestaurantCommandDto>()
            val restaurantSnapshot = mockk<RestaurantSnapshot>()
            val loadResult =
                pureMonkey.giveMeBuilder<LoadRestaurantResult>()
                    .set("userId", userId)
                    .sample()

            every { request.id } returns restaurantId
            every { request.userId } returns userId
            every { loadRestaurant.query(any()) } returns loadResult
            every { uploadRestaurantImageFile.execute(any()) } returns listOf()
            every { changeRestaurantService.change(any(), any()) } returns restaurantSnapshot
            every { changeRestaurant.command(any()) } throws RuntimeException()

            // when & then
            assertThrows<RuntimeException> {
                useCase.execute(request)
            }

            verify(exactly = 1) {
                loadRestaurant.query(any())
            }
            verify(exactly = 0) {
                uploadRestaurantImageFile.execute(any())
                changeRestaurantService.change(any(), any())
            }
        }
    }

    @Nested
    @DisplayName("유효한 요청이 주어졌을 때")
    inner class `Context with valid request` {
        @MockK
        private lateinit var changeRestaurantService: ChangeRestaurantService

        @MockK
        private lateinit var loadRestaurant: LoadRestaurant

        @MockK
        private lateinit var changeRestaurant: ChangeRestaurant

        @MockK
        private lateinit var uploadRestaurantImageFile: UploadRestaurantImageFile

        @InjectMockKs
        private lateinit var useCase: ChangeRestaurantUseCase

        @Test
        @DisplayName("레스토랑 정보를 성공적으로 수정하고 true를 반환한다")
        fun `it returns true on successful update`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            // given
            val userId = "1"
            val request =
                pureMonkey.giveMeBuilder<ChangeRestaurantCommandDto>()
                    .set("photos", listOf<MultipartFile>())
                    .set("userId", userId)
                    .sample()
            val loadResult = mockk<LoadRestaurantResult>()
            val domain = pureMonkey.giveMeOne<Restaurant>()
            val restaurantSnapshot = domain.snapshot()

            every { loadRestaurant.query(any()) } returns loadResult
            every { loadResult.userId } returns userId
            every { loadResult.toDomain() } returns domain
            every { uploadRestaurantImageFile.execute(any()) } returns listOf()
            every { changeRestaurantService.change(any(), any()) } returns restaurantSnapshot
            every { changeRestaurant.command(any()) } returns true

            // when
            val result = useCase.execute(request)

            // then
            assertThat(result).isTrue()

            verify(exactly = 0) {
                uploadRestaurantImageFile.execute(any())
            }

            verify(exactly = 1) {
                loadRestaurant.query(any())
                changeRestaurantService.change(any(), any())
                changeRestaurant.command(any())
            }
        }

        @Test
        @DisplayName("사진 업로드에 성공하고, URL을 포함하여 레스토랑 정보를 수정한다")
        fun `it updates restaurant with uploaded photo URLs`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            // given
            val userId = "1"
            val request =
                pureMonkey.giveMeBuilder<ChangeRestaurantCommandDto>()
                    .set("photos", listOf(pureMonkey.giveMeOne<MockMultipartFile>()))
                    .set("userId", userId)
                    .sample()
            val photoUrl = "https://image.com/1.jpg"
            val loadResult = mockk<LoadRestaurantResult>()
            val domain = pureMonkey.giveMeOne<Restaurant>()
            val restaurantSnapshot = domain.snapshot()

            every { loadResult.userId } returns userId
            every { loadRestaurant.query(any()) } returns loadResult
            every { loadResult.toDomain() } returns domain
            every { uploadRestaurantImageFile.execute(any()) } returns listOf(photoUrl)
            every { changeRestaurantService.change(any(), any()) } returns restaurantSnapshot
            every { changeRestaurant.command(any()) } returns true

            // when
            useCase.execute(request)

            // then
            verify(exactly = 1) {
                loadRestaurant.query(any())
                uploadRestaurantImageFile.execute(any())
                changeRestaurantService.change(any(), any())
                changeRestaurant.command(any())
            }
        }
    }
}
