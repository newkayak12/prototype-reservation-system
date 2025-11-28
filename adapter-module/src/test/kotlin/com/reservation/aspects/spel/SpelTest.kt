package com.reservation.aspects.spel

import com.reservation.config.aspect.SpelParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class SpelTest {
    private data class TestContainer(
        val userId: String,
        val restaurantId: String,
        val date: LocalDate,
        val startTime: LocalTime,
    )

    @Suppress("UnusedParameter", "EmptyFunctionBlock")
    private fun testMethod(testContainer: TestContainer) {}

    val parser = SpelParser()

    @Test
    fun spElParsingTest() {
        val method = this.javaClass.getDeclaredMethod("testMethod", TestContainer::class.java)
        method.isAccessible = true
        val args: Array<Any?> =
            arrayOf(
                TestContainer(
                    userId = "user123",
                    restaurantId = "restaurant456",
                    date = LocalDate.of(2024, 11, 24),
                    startTime = LocalTime.of(14, 30),
                ),
            )

        val spelExpression = """
          'TIMETABLE:' + #testContainer.restaurantId + ':' +
          #testContainer.date.format(T(java.time.format.DateTimeFormatter).ofPattern('yyyyMMdd')) + ':' +
          #testContainer.startTime.format(T(java.time.format.DateTimeFormatter).ofPattern('HHmm'))
      """

        val parsedKey = parser.parse(spelExpression, method, args)

        println(parsedKey)
        assertThat(parsedKey).isNotBlank
    }
}
