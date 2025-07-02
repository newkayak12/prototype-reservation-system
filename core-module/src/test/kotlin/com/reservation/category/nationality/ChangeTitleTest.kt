package com.reservation.category.nationality

import com.reservation.category.shared.CategoryDetail
import com.reservation.enumeration.CategoryType.NATIONALITY
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.mockk.spyk
import io.mockk.verify

class ChangeTitleTest : FunSpec(
    {

        test("새로운 국가 이름을 지정하고 변경한다.") {
            val nationality =
                spyk(
                    Nationality(
                        0L,
                        CategoryDetail("대한민국", NATIONALITY),
                    ),
                )
            val target = "미국"

            nationality.changeTitle(target)

            nationality.title shouldBeEqual target
            verify(exactly = 1) { nationality.changeTitle(any()) }
        }
    },
)
