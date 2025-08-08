package com.reservation.company.useacse

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.company.port.input.query.request.FindCompaniesByIdQuery
import com.reservation.company.port.output.FindCompany
import com.reservation.company.port.output.FindCompany.FindCompanyInquiry
import com.reservation.company.port.output.FindCompany.FindCompanyResult
import com.reservation.company.usecase.FindCompanyService
import com.reservation.fixture.FixtureMonkeyFactory
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class FindCompanyServiceTest {
    @MockK
    private lateinit var findCompany: FindCompany

    @InjectMockKs
    private lateinit var findCompanyService: FindCompanyService

    @DisplayName(value = "ID로 겁색했지만 결과 값이 없었고 NoSuchPersistedElementException이 발생한다.")
    @Test
    fun `throw NoSuchPersistedElementException`() {
        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        val mockQuery = mockk<FindCompaniesByIdQuery>()
        val query = pureMonkey.giveMeOne<FindCompanyInquiry>()

        every {
            mockQuery.toInquiry()
        } returns query

        every {
            findCompany.query(query)
        } returns null

        assertThrows<NoSuchPersistedElementException> {
            findCompanyService.execute(mockQuery)
        }

        verify(exactly = 1) { findCompany.query(query) }
    }

    @DisplayName(value = "ID로 검색헀고 적절한 결과가 도출되어 반환된다.")
    @Test
    fun `return proper element`() {
        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        val mockQuery = mockk<FindCompaniesByIdQuery>()
        val query = pureMonkey.giveMeOne<FindCompanyInquiry>()
        val result = pureMonkey.giveMeOne<FindCompanyResult>()

        every {
            mockQuery.toInquiry()
        } returns query

        every {
            findCompany.query(query)
        } returns result

        val serviceResult = findCompanyService.execute(mockQuery)

        assertThat(serviceResult).isNotNull
        verify(exactly = 1) { findCompany.query(query) }
    }
}
