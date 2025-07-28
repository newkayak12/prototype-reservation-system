package com.reservation.company.useacse

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.company.port.input.query.request.FindCompaniesQuery
import com.reservation.company.port.output.FindCompanies
import com.reservation.company.port.output.FindCompanies.FindCompaniesResult
import com.reservation.company.usecase.FindCompaniesService
import com.reservation.fixture.FixtureMonkeyFactory
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import net.jqwik.api.Arbitraries
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class FindCompaniesServiceTest {
    @MockK
    private lateinit var findCompanies: FindCompanies

    @InjectMockKs
    private lateinit var useCase: FindCompaniesService

    @DisplayName("조건 없이 회사에 대한 조회 요청을 하고 결과가 총 50건 조회한다.")
    @Test
    fun `find company with no condition then return 50 results`() {
        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        val size = 50

        val queryResults = pureMonkey.giveMe<FindCompaniesResult>(size)
        val request = pureMonkey.giveMeOne<FindCompaniesQuery>()

        every {
            findCompanies.query(any())
        } returns queryResults

        val result = useCase.execute(request)

        assertThat(result).hasSize(size)

        verify(exactly = 1) {
            findCompanies.query(any())
        }
    }

    @DisplayName("BBQ라는 조건으로 조회 요청을 하고 1건 조회된다.")
    @Test
    fun `find company BBQ then return 1 result`() {
        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        val size = 1

        val queryResults = pureMonkey.giveMe<FindCompaniesResult>(size)
        val request =
            pureMonkey.giveMeBuilder<FindCompaniesQuery>()
                .set("companyName", Arbitraries.strings().sample())
                .sample()

        every {
            findCompanies.query(any())
        } returns queryResults

        val result = useCase.execute(request)

        assertThat(result).hasSize(size)

        verify(exactly = 1) {
            findCompanies.query(any())
        }
    }
}
