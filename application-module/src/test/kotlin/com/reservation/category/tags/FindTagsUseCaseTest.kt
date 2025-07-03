package com.reservation.category.tags

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.category.tag.port.input.FindTagsQuery.FindTagsQueryDto
import com.reservation.category.tag.port.output.FindTags
import com.reservation.category.tag.port.output.FindTags.FindTagsResult
import com.reservation.category.tag.usecase.FindTagsUseCase
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
class FindTagsUseCaseTest {
    @MockK
    private lateinit var findTags: FindTags

    @InjectMockKs
    private lateinit var useCase: FindTagsUseCase

    @DisplayName("태크 조회 요청을 진행하고 18건의 결과가 조회된다.")
    @Test
    fun findNationalities() {
        val size = 18
        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        val request = pureMonkey.giveMeOne<FindTagsQueryDto>()
        val resultList = pureMonkey.giveMe<FindTagsResult>(size)

        every {
            findTags.query(any())
        } returns resultList

        val result = useCase.execute(request)

        assertThat(result).hasSize(size)

        verify(exactly = 1) {
            findTags.query(any())
        }
    }

    @DisplayName("title을 입력해서 태그 조회 요청을 진행하고 10건의 결과가 조회된다.")
    @Test
    fun findTagsByTitle() {
        val size = 10
        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        val request =
            pureMonkey.giveMeBuilder<FindTagsQueryDto>()
                .set("title", Arbitraries.strings().sample())
                .sample()
        val resultList = pureMonkey.giveMe<FindTagsResult>(size)

        every {
            findTags.query(any())
        } returns resultList

        val result = useCase.execute(request)

        assertThat(result).hasSize(size)

        verify(exactly = 1) {
            findTags.query(any())
        }
    }
}
