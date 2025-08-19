package com.reservation.menu.usecase

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.menu.exceptions.InvalidateMenuElementException
import com.reservation.menu.port.input.request.CreateMenuCommand
import com.reservation.menu.port.output.CreateMenu
import com.reservation.menu.port.output.UploadMenuImageFile
import com.reservation.menu.port.usecase.CreateMenuService
import com.reservation.menu.service.CreateMenuDomainService
import com.reservation.menu.snapshot.MenuSnapshot
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
import org.springframework.mock.web.MockMultipartFile

@ExtendWith(MockKExtension::class)
@DisplayName("CreateMenuService의 execute 메소드는")
class CreateMenuServiceTest {
    @DisplayName("잘못된 요청이 제공됐을 떄")
    @Nested
    inner class `Invalid Form provided` {
        @MockK
        private lateinit var createMenuDomainService: CreateMenuDomainService

        @MockK
        private lateinit var uploadMenuImageFile: UploadMenuImageFile

        @MockK
        private lateinit var createMenu: CreateMenu

        @InjectMockKs
        private lateinit var createMenuService: CreateMenuService

        @DisplayName("그래서 InvalidateMenuElementException이 발생한다.")
        @Test
        fun `so creation is failed`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val command = pureMonkey.giveMeOne<CreateMenuCommand>()

            every { uploadMenuImageFile.execute(any()) } returns emptyList()
            every {
                createMenuDomainService.createMenu(any())
            } throws InvalidateMenuElementException("")

            assertThrows<InvalidateMenuElementException> {
                createMenuService.execute(command)
            }

            verify(atMost = 1, atLeast = 0) { uploadMenuImageFile.execute(any()) }
            verify(exactly = 1) { createMenuDomainService.createMenu(any()) }
        }
    }

    @DisplayName("올바른 요청이 제공됐을 때")
    @Nested
    inner class `Proper From provided` {
        @MockK
        private lateinit var createMenuDomainService: CreateMenuDomainService

        @MockK
        private lateinit var uploadMenuImageFile: UploadMenuImageFile

        @MockK
        private lateinit var createMenu: CreateMenu

        @InjectMockKs
        private lateinit var createMenuService: CreateMenuService

        @DisplayName("이미지 파일 없이  저장에 성공한다.")
        @Test
        fun `upload without photos`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val command =
                pureMonkey.giveMeBuilder<CreateMenuCommand>()
                    .set("photoUrl", listOf<String>())
                    .sample()
            val snapshot = pureMonkey.giveMeOne<MenuSnapshot>()

            every { uploadMenuImageFile.execute(any()) } returns emptyList()
            every { createMenuDomainService.createMenu(any()) } returns snapshot
            every { createMenu.command(any()) } returns true

            val result = createMenuService.execute(command)

            assertThat(result).isTrue()
            verify(exactly = 0) { uploadMenuImageFile.execute(any()) }
            verify(exactly = 1) { createMenuDomainService.createMenu(any()) }
            verify(exactly = 1) { createMenu.command(any()) }
        }

        @DisplayName("이미지 파일이 첨부되어 업로드되고 저장에 성공한다.")
        @Test
        fun `upload with photos`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val photosResult = pureMonkey.giveMe<String>(5)
            val command =
                pureMonkey.giveMeBuilder<CreateMenuCommand>()
                    .set(
                        "photoUrl",
                        listOf(
                            MockMultipartFile("1", "".toByteArray()),
                            MockMultipartFile("1", "".toByteArray()),
                        ),
                    )
                    .sample()
            val snapshot = pureMonkey.giveMeOne<MenuSnapshot>()

            every { uploadMenuImageFile.execute(any()) } returns photosResult
            every { createMenuDomainService.createMenu(any()) } returns snapshot
            every { createMenu.command(any()) } returns true

            val result = createMenuService.execute(command)

            assertThat(result).isTrue()
            verify(exactly = 1) { uploadMenuImageFile.execute(any()) }
            verify(exactly = 1) { createMenuDomainService.createMenu(any()) }
            verify(exactly = 1) { createMenu.command(any()) }
        }
    }
}
