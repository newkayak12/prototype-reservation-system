package com.reservation.schedule

import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class EntityTest : BehaviorSpec({
    val fixtureMonkey: FixtureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

    given("Table 엔티티가 주어졌을 때") {
        `when`("snapshot을 생성하면") {
            then("모든 필드가 올바르게 매핑된다") {
                val table =
                    Table(
                        id = "table-id",
                        restaurantId = "restaurant-id",
                        tableNumber = 1,
                        tableSize = 4,
                    )

                val snapshot = table.snapshot()

                snapshot.id shouldBe "table-id"
                snapshot.restaurantId shouldBe "restaurant-id"
                snapshot.tableNumber shouldBe 1
                snapshot.tableSize shouldBe 4
            }
        }

        `when`("fixture monkey로 생성된 데이터로 snapshot을 생성하면") {
            then("모든 필드가 올바르게 매핑된다") {
                val restaurantId = fixtureMonkey.giveMeOne<String>()
                val tableNumber = fixtureMonkey.giveMeOne<Int>()
                val tableSize = fixtureMonkey.giveMeOne<Int>()

                val table =
                    Table(
                        restaurantId = restaurantId,
                        tableNumber = tableNumber,
                        tableSize = tableSize,
                    )

                val snapshot = table.snapshot()

                snapshot.id shouldBe null
                snapshot.restaurantId shouldBe restaurantId
                snapshot.tableNumber shouldBe tableNumber
                snapshot.tableSize shouldBe tableSize
            }
        }
    }

    given("TimeSpan 엔티티가 주어졌을 때") {
        `when`("snapshot을 생성하면") {
            then("모든 필드가 올바르게 매핑된다") {
                val timeSpan =
                    TimeSpan(
                        id = "timespan-id",
                        restaurantId = "restaurant-id",
                        day = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(18, 0),
                    )

                val snapshot = timeSpan.snapshot()

                snapshot.id shouldBe "timespan-id"
                snapshot.restaurantId shouldBe "restaurant-id"
                snapshot.day shouldBe DayOfWeek.MONDAY
                snapshot.startTime shouldBe LocalTime.of(9, 0)
                snapshot.endTime shouldBe LocalTime.of(18, 0)
            }
        }

        `when`("fixture monkey로 생성된 데이터로 snapshot을 생성하면") {
            then("모든 필드가 올바르게 매핑된다") {
                val restaurantId = fixtureMonkey.giveMeOne<String>()
                val day = fixtureMonkey.giveMeOne<DayOfWeek>()
                val startTime = fixtureMonkey.giveMeOne<LocalTime>()
                val endTime = fixtureMonkey.giveMeOne<LocalTime>()

                val timeSpan =
                    TimeSpan(
                        restaurantId = restaurantId,
                        day = day,
                        startTime = startTime,
                        endTime = endTime,
                    )

                val snapshot = timeSpan.snapshot()

                snapshot.id shouldBe null
                snapshot.restaurantId shouldBe restaurantId
                snapshot.day shouldBe day
                snapshot.startTime shouldBe startTime
                snapshot.endTime shouldBe endTime
            }
        }

        `when`("equals와 hashCode를 테스트하면") {
            then("같은 값으로 생성된 객체들은 동등하다") {
                val timeSpan1 =
                    TimeSpan(
                        restaurantId = "restaurant-id",
                        day = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(18, 0),
                    )
                val timeSpan2 =
                    TimeSpan(
                        restaurantId = "restaurant-id",
                        day = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(18, 0),
                    )

                (timeSpan1 == timeSpan2) shouldBe true
                timeSpan1.hashCode() shouldBe timeSpan2.hashCode()
            }

            then("다른 값으로 생성된 객체들은 동등하지 않다") {
                val timeSpan1 =
                    TimeSpan(
                        restaurantId = "restaurant-id-1",
                        day = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(18, 0),
                    )
                val timeSpan2 =
                    TimeSpan(
                        restaurantId = "restaurant-id-2",
                        day = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(18, 0),
                    )

                (timeSpan1 == timeSpan2) shouldBe false
            }
        }
    }

    given("Holiday 엔티티가 주어졌을 때") {
        `when`("snapshot을 생성하면") {
            then("모든 필드가 올바르게 매핑된다") {
                val holiday =
                  Holiday(
                        id = "holiday-id",
                        restaurantId = "restaurant-id",
                        date = LocalDate.of(2024, 12, 25),
                    )

                val snapshot = holiday.snapshot()

                snapshot.id shouldBe "holiday-id"
                snapshot.restaurantId shouldBe "restaurant-id"
                snapshot.date shouldBe LocalDate.of(2024, 12, 25)
            }
        }

        `when`("fixture monkey로 생성된 데이터로 snapshot을 생성하면") {
            then("모든 필드가 올바르게 매핑된다") {
                val restaurantId = fixtureMonkey.giveMeOne<String>()
                val date = fixtureMonkey.giveMeOne<LocalDate>()

                val holiday =
                  Holiday(
                        restaurantId = restaurantId,
                        date = date,
                    )

                val snapshot = holiday.snapshot()

                snapshot.id shouldBe null
                snapshot.restaurantId shouldBe restaurantId
                snapshot.date shouldBe date
            }
        }

        `when`("equals와 hashCode를 테스트하면") {
            then("같은 값으로 생성된 객체들은 동등하다") {
                val holiday1 =
                  Holiday(
                        id = "holiday-id",
                        restaurantId = "restaurant-id",
                        date = LocalDate.of(2024, 12, 25),
                    )
                val holiday2 =
                  Holiday(
                        id = "holiday-id",
                        restaurantId = "restaurant-id",
                        date = LocalDate.of(2024, 12, 25),
                    )

                (holiday1 == holiday2) shouldBe true
                holiday1.hashCode() shouldBe holiday2.hashCode()
            }

            then("다른 값으로 생성된 객체들은 동등하지 않다") {
                val holiday1 =
                  Holiday(
                        restaurantId = "restaurant-id",
                        date = LocalDate.of(2024, 12, 25),
                    )
                val holiday2 =
                  Holiday(
                        restaurantId = "restaurant-id",
                        date = LocalDate.of(2024, 12, 24),
                    )

                (holiday1 == holiday2) shouldBe false
            }
        }
    }
})
