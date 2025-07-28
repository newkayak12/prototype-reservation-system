package com.reservation.user.self.usecase

import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.user.self.port.output.FindGeneralUser
import com.reservation.user.self.port.output.FindGeneralUser.FindGeneralUserResult
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class FindGeneralUserServiceTest {
    @MockK
    private lateinit var findGeneralUser: FindGeneralUser

    @InjectMockKs
    private lateinit var useCase: FindGeneralUserService

    @DisplayName("찾고자 하는 사용자가 없다.")
    @Test
    fun `there is no user`() {
        val uuid = UuidGenerator.generate()

        every {
            findGeneralUser.query(any())
        } returns null

        assertThrows<NoSuchPersistedElementException> {
            useCase.execute(uuid)
        }
    }

    @DisplayName("사용자를 찾아서 반환한다.")
    @Test
    fun `we can find user`() {
        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        val uuid = UuidGenerator.generate()
        val dbResult =
            pureMonkey.giveMeBuilder<FindGeneralUserResult>()
                .set("id", uuid)
                .sample()

        every {
            findGeneralUser.query(any())
        } returns dbResult

        assertEquals(uuid, useCase.execute(uuid).id)
    }
}
