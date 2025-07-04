package com.reservation.category.tag

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.mockk.spyk
import io.mockk.verify

class ChangeTitleTest : FunSpec(
    {

        test("새로운 태그 이름을 지정하고 변경한다.") {
            val tag =
                spyk(
                    Tag(
                        0L,
                        "#파티_분위기",
                    ),
                )
            val target = "#아늑함"

            tag.changeTitle(target)

            tag.title shouldBeEqual target
            verify(exactly = 1) { tag.changeTitle(any()) }
        }
    },
)
