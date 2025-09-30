package com.reservation.menu.usecase

import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.giveMe
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.menu.port.input.request.UpdateMenuCommand
import com.reservation.menu.port.output.ChangeMenu
import com.reservation.menu.port.output.LoadMenuById
import com.reservation.menu.port.output.LoadMenuById.LoadMenu
import com.reservation.menu.port.output.UploadMenuImageFile
import com.reservation.menu.service.ChangeMenuDomainService
import com.reservation.menu.snapshot.MenuSnapshot
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile

@ExtendWith(MockKExtension::class)
@DisplayName("Menu를 수정할 떄,")
class ChangeMenuServiceTest {
    @MockK
    private lateinit var loadMenuById: LoadMenuById

    @MockK
    private lateinit var changeMenuDomainService: ChangeMenuDomainService

    @MockK
    private lateinit var uploadMenuImageFile: UploadMenuImageFile

    @MockK
    private lateinit var changeMenu: ChangeMenu

    @InjectMockKs
    private lateinit var changeMenuService: ChangeMenuService

    @DisplayName("이미지가 없는 수정 사항이 전달되면")
    @Nested
    inner class `No image request` {
        private lateinit var pureMonkey: FixtureMonkey

        @BeforeEach
        fun setPureMonkey() {
            pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        }

        @DisplayName("저장된 메뉴를 찾지 못해서 NoSuchPersistedElementException가 발생한다.")
        @Test
        fun `throws NoSuchPersistedElementException`() {
            val command =
                pureMonkey.giveMeBuilder<UpdateMenuCommand>()
                    .set("photos", listOf<MultipartFile>())
                    .sample()

            every {
                loadMenuById.loadById(any())
            } throws NoSuchPersistedElementException()

            assertThrows<NoSuchPersistedElementException> { changeMenuService.execute(command) }

            verify(exactly = 1) {
                loadMenuById.loadById(any())
            }

            verify(exactly = 0) {
                changeMenuDomainService.updateMenu(any(), any())
                changeMenu.command(any())
                uploadMenuImageFile.execute(any())
            }
        }

        @DisplayName("이미지 업로드 없이 수정 사항 반영이 진행된다.")
        @Test
        fun `apply update request`() {
            val menu = pureMonkey.giveMeOne<LoadMenu>()
            val snapshot =
                pureMonkey.giveMeBuilder<MenuSnapshot>()
                    .set("id", UuidGenerator.generate())
                    .sample()
            val command =
                pureMonkey.giveMeBuilder<UpdateMenuCommand>()
                    .set("photos", listOf<MultipartFile>())
                    .sample()

            every {
                loadMenuById.loadById(any())
            } returns menu

            every {
                changeMenuDomainService.updateMenu(any(), any())
            } returns snapshot

            every {
                changeMenu.command(any())
            } returns true

            val result = changeMenuService.execute(command)

            assertTrue(result)

            verify(exactly = 1) {
                loadMenuById.loadById(any())
                changeMenuDomainService.updateMenu(any(), any())
                changeMenu.command(any())
            }

            verify(exactly = 0) {
                uploadMenuImageFile.execute(any())
            }
        }
    }

    @DisplayName("이미지가 있는 수정 사항이 전달되면")
    @Nested
    inner class `With image request` {
        private lateinit var pureMonkey: FixtureMonkey

        @BeforeEach
        fun setPureMonkey() {
            pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        }

        @DisplayName("저장된 메뉴를 찾지 못해서 NoSuchPersistedElementException가 발생한다.")
        @Test
        fun `throws NoSuchPersistedElementException`() {
            val command =
                pureMonkey.giveMeBuilder<UpdateMenuCommand>()
                    .set(
                        "photos",
                        listOf(
                            MockMultipartFile("img.jpg", "".toByteArray()),
                        ),
                    )
                    .sample()

            every {
                loadMenuById.loadById(any())
            } throws NoSuchPersistedElementException()

            assertThrows<NoSuchPersistedElementException> { changeMenuService.execute(command) }

            verify(exactly = 1) {
                loadMenuById.loadById(any())
            }

            verify(exactly = 0) {
                changeMenuDomainService.updateMenu(any(), any())
                changeMenu.command(any())
                uploadMenuImageFile.execute(any())
            }
        }

        @DisplayName("이미지 업로드 이후 수정 사항 반영이 진행된다.")
        @Test
        fun `apply update request`() {
            val menu = pureMonkey.giveMeOne<LoadMenu>()
            val snapshot =
                pureMonkey.giveMeBuilder<MenuSnapshot>()
                    .set("id", UuidGenerator.generate())
                    .sample()
            val command =
                pureMonkey.giveMeBuilder<UpdateMenuCommand>()
                    .set(
                        "photos",
                        listOf(
                            MockMultipartFile("img.jpg", "".toByteArray()),
                        ),
                    )
                    .sample()

            val savedImage = pureMonkey.giveMe<String>(4)

            every {
                loadMenuById.loadById(any())
            } returns menu

            every {
                uploadMenuImageFile.execute(any())
            } returns savedImage

            every {
                changeMenuDomainService.updateMenu(any(), any())
            } returns snapshot

            every {
                changeMenu.command(any())
            } returns true

            val result = changeMenuService.execute(command)

            assertTrue(result)

            verify(exactly = 1) {
                loadMenuById.loadById(any())
                uploadMenuImageFile.execute(any())
                changeMenuDomainService.updateMenu(any(), any())
                changeMenu.command(any())
            }
        }
    }
}
