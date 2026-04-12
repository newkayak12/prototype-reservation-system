package com.reservation.kafka.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.enumeration.OutboxEventType.TIME_TABLE_OCCUPIED
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.httpinterface.timetable.FindTimeTableOccupancyHttpInterface
import com.reservation.httpinterface.timetable.response.FindTimeTableOccupancyInternallyHttpInterfaceResponse
import com.reservation.kafka.adapter.TimeTableOccupancyKafkaListener
import com.reservation.kafka.event.TimeTableOccupancyReceivedEvent
import com.reservation.reservation.port.input.CreateReservationUseCase
import com.reservation.reservation.port.input.IsReservationExistsUseCase
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.confluent.parallelconsumer.ParallelStreamProcessor
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jqwik.api.Arbitraries
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.kafka.core.KafkaTemplate
import java.time.LocalDateTime

@DisplayName("Kafka Listener로 이벤트를 전달 받았을 때 ")
class TimeTableOccupancyKafkaListenerTest : FunSpec(
    {
        val httpInterface = mockk<FindTimeTableOccupancyHttpInterface>()
        val createReservationUseCase = mockk<CreateReservationUseCase>()
        val isReservationExistsUseCase = mockk<IsReservationExistsUseCase>()
        val parallelEventConsumer = mockk<ParallelStreamProcessor<String, String>>(relaxed = true)
        val objectMapper = ObjectMapper()
        val kafkaTemplate = mockk<KafkaTemplate<String, String>>(relaxed = true)

        val kafkaListener =
            TimeTableOccupancyKafkaListener(
                httpInterface = httpInterface,
                createReservationUseCase = createReservationUseCase,
                isReservationExistsUseCase = isReservationExistsUseCase,
                parallelEventConsumer = parallelEventConsumer,
                objectMapper = objectMapper,
                kafkaTemplate = kafkaTemplate,
            )

        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

        beforeTest {
            clearAllMocks()
        }

        test("HttpInterface에서 NoSuchPersistedElementException가 발생한다.") {
            val event =
                TimeTableOccupancyReceivedEvent(
                    eventType = TIME_TABLE_OCCUPIED,
                    eventVersion = 1.0,
                    timeTableId = UuidGenerator.generate(),
                    timeTableOccupancyId = UuidGenerator.generate(),
                    eventId = UuidGenerator.generate(),
                    occurredAt = LocalDateTime.now(),
                )

            every {
                isReservationExistsUseCase.execute(any())
            } returns false

            every {
                httpInterface.findTimeTableOccupancyInternally(any(), any())
            } throws NoSuchPersistedElementException()

            assertThrows<NoSuchPersistedElementException> {
                kafkaListener.onEventHandler(event)
            }

            verify(exactly = 1) { httpInterface.findTimeTableOccupancyInternally(any(), any()) }
        }

        test("HttpInterface에서 조회가 완료됐지만 예약 생성에 실패한다.") {
            val event =
                TimeTableOccupancyReceivedEvent(
                    eventType = TIME_TABLE_OCCUPIED,
                    eventVersion = 1.0,
                    timeTableId = UuidGenerator.generate(),
                    timeTableOccupancyId = UuidGenerator.generate(),
                    eventId = UuidGenerator.generate(),
                    occurredAt = LocalDateTime.now(),
                )

            val response =
                pureMonkey
                    .giveMeOne<FindTimeTableOccupancyInternallyHttpInterfaceResponse>()

            every {
                isReservationExistsUseCase.execute(any())
            } returns false

            every {
                httpInterface.findTimeTableOccupancyInternally(any(), any())
            } returns ResponseEntity.ok(response)

            every {
                createReservationUseCase.execute(any())
            } throws DataIntegrityViolationException(Arbitraries.strings().sample())

            assertThrows<DataIntegrityViolationException> {
                kafkaListener.onEventHandler(event)
            }

            verify(exactly = 1) { httpInterface.findTimeTableOccupancyInternally(any(), any()) }
        }

        test("HttpInterface에서 조회가 완료됐고 예약 생성에 성공한다.") {
            val event =
                TimeTableOccupancyReceivedEvent(
                    eventType = TIME_TABLE_OCCUPIED,
                    eventVersion = 1.0,
                    timeTableId = UuidGenerator.generate(),
                    timeTableOccupancyId = UuidGenerator.generate(),
                    eventId = UuidGenerator.generate(),
                    occurredAt = LocalDateTime.now(),
                )
            val response =
                pureMonkey
                    .giveMeOne<FindTimeTableOccupancyInternallyHttpInterfaceResponse>()

            every {
                isReservationExistsUseCase.execute(any())
            } returns false

            every {
                httpInterface.findTimeTableOccupancyInternally(any(), any())
            } returns ResponseEntity.ok(response)

            every {
                createReservationUseCase.execute(any())
            } returns true

            kafkaListener.onEventHandler(event)

            verify(exactly = 1) { httpInterface.findTimeTableOccupancyInternally(any(), any()) }
        }
    },
)
