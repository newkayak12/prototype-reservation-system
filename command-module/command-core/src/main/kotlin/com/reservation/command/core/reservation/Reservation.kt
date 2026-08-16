package com.reservation.command.core.reservation

import com.reservation.command.core.reservation.command.AutoConfirmVisit
import com.reservation.command.core.reservation.command.CancelReservation
import com.reservation.command.core.reservation.command.ConfirmReservation
import com.reservation.command.core.reservation.command.ConfirmVisit
import com.reservation.command.core.reservation.command.CreateReservation
import com.reservation.command.core.reservation.command.ExpireReservation
import com.reservation.command.core.reservation.command.FailReservation
import com.reservation.command.core.reservation.command.JudgeNoShow
import com.reservation.command.core.reservation.event.RefundRequired
import com.reservation.command.core.reservation.event.ReservationCancelled
import com.reservation.command.core.reservation.event.ReservationConfirmed
import com.reservation.command.core.reservation.event.ReservationCreated
import com.reservation.command.core.reservation.event.ReservationExpired
import com.reservation.command.core.reservation.event.ReservationFailed
import com.reservation.command.core.reservation.event.ReservationNoShow
import com.reservation.command.core.reservation.event.VisitConfirmed
import com.reservation.command.core.support.Command
import com.reservation.command.core.support.DomainEvent
import com.reservation.command.core.support.EventSourcingAggregate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Suppress("LongParameterList", "TooManyFunctions")
data class Reservation private constructor(
    val reservationId: String,
    val userId: String,
    val restaurantId: String,
    val tableNumber: Int,
    val tableSize: Int,
    val slotId: String,
    val date: LocalDate,
    val day: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val status: ReservationStatus,
    val requestedAt: LocalDateTime,
    val confirmedAt: LocalDateTime?,
    val failedAt: LocalDateTime?,
    val expiredAt: LocalDateTime?,
    val cancelledBy: CancelActor?,
    val cancelReason: String?,
    val cancelledAt: LocalDateTime?,
    val judgedAt: LocalDateTime?,
    val visitConfirmedBy: VisitConfirmer?,
    val visitConfirmedAt: LocalDateTime?,
) : EventSourcingAggregate<Reservation>() {
    override fun handle(command: Command): List<DomainEvent> =
        when (command) {
            is ConfirmReservation -> handleConfirmReservation(command)
            is FailReservation -> handleFailReservation(command)
            is ExpireReservation -> handleExpireReservation(command)
            is CancelReservation -> handleCancelReservation(command)
            is JudgeNoShow -> handleJudgeNoShow(command)
            is ConfirmVisit -> handleConfirmVisit(command)
            is AutoConfirmVisit -> handleAutoConfirmVisit(command)
            else -> error("Reservation이 처리할 수 없는 커맨드: $command")
        }

    override fun apply(event: DomainEvent): Reservation =
        when (event) {
            is ReservationConfirmed -> applyReservationConfirmed(event)
            is ReservationFailed -> applyReservationFailed(event)
            is ReservationExpired -> applyReservationExpired(event)
            is ReservationCancelled -> applyReservationCancelled(event)
            is ReservationNoShow -> applyReservationNoShow(event)
            is VisitConfirmed -> applyVisitConfirmed(event)
            else -> this
        }

    private fun reservationDateTime(): LocalDateTime = LocalDateTime.of(date, startTime)

    private fun handleConfirmReservation(command: ConfirmReservation): List<DomainEvent> {
        require(status == ReservationStatus.PENDING || status == ReservationStatus.EXPIRED) {
            "PENDING 또는 EXPIRED 상태의 예약만 결제 확정을 처리할 수 있다"
        }
        return if (status == ReservationStatus.PENDING) {
            listOf(
                ReservationConfirmed(
                    reservationId = reservationId,
                    confirmedAt = command.confirmedAt,
                ),
            )
        } else {
            listOf(RefundRequired(reservationId = reservationId, rejectedAt = command.confirmedAt))
        }
    }

    private fun handleFailReservation(command: FailReservation): List<DomainEvent> {
        require(status == ReservationStatus.PENDING) { "PENDING 상태의 예약만 결제 실패 처리할 수 있다" }
        return listOf(ReservationFailed(reservationId = reservationId, failedAt = command.failedAt))
    }

    private fun handleExpireReservation(command: ExpireReservation): List<DomainEvent> {
        require(status == ReservationStatus.PENDING) { "PENDING 상태의 예약만 만료 처리할 수 있다" }
        return listOf(
            ReservationExpired(reservationId = reservationId, expiredAt = command.expiredAt),
        )
    }

    private fun handleCancelReservation(command: CancelReservation): List<DomainEvent> {
        require(status == ReservationStatus.PENDING || status == ReservationStatus.CONFIRMED) {
            "PENDING 또는 CONFIRMED 상태의 예약만 취소할 수 있다"
        }
        when (command.cancelledBy) {
            CancelActor.GUEST -> {
                require(command.requesterId == userId) { "예약자 본인만 취소할 수 있다" }
                val guestCancelDeadline =
                    reservationDateTime().minusDays(GUEST_CANCEL_DEADLINE_DAYS)
                require(!command.cancelledAt.isAfter(guestCancelDeadline)) {
                    "손님 취소는 방문 3일 전까지만 가능하다"
                }
            }
            CancelActor.OWNER -> {
                require(command.cancelledAt.isBefore(reservationDateTime())) {
                    "매장 점주 취소는 방문일시 전까지만 가능하다"
                }
                val reasonLength = command.reason?.length ?: 0
                val reasonRange = OWNER_REASON_MIN_LENGTH..OWNER_REASON_MAX_LENGTH
                require(command.reason != null && reasonLength in reasonRange) {
                    "매장 점주 취소 시 사유는 30자 이상 199자 이하이어야 한다"
                }
            }
        }
        return listOf(
            ReservationCancelled(
                reservationId = reservationId,
                cancelledBy = command.cancelledBy,
                reason = command.reason,
                cancelledAt = command.cancelledAt,
            ),
        )
    }

    private fun handleJudgeNoShow(command: JudgeNoShow): List<DomainEvent> {
        require(status == ReservationStatus.CONFIRMED) { "CONFIRMED 상태의 예약만 노쇼 판정할 수 있다" }
        require(command.judgedAt.isAfter(reservationDateTime())) { "노쇼 판정은 예약 시각 경과 후에만 가능하다" }
        return listOf(ReservationNoShow(reservationId = reservationId, judgedAt = command.judgedAt))
    }

    private fun handleConfirmVisit(command: ConfirmVisit): List<DomainEvent> {
        require(status == ReservationStatus.CONFIRMED) { "CONFIRMED 상태의 예약만 방문 확정할 수 있다" }
        require(!command.confirmedAt.isBefore(reservationDateTime())) { "방문 확정은 예약 시각 이후에만 가능하다" }
        return listOf(
            VisitConfirmed(
                reservationId = reservationId,
                confirmedBy = VisitConfirmer.OWNER,
                confirmedAt = command.confirmedAt,
            ),
        )
    }

    private fun handleAutoConfirmVisit(command: AutoConfirmVisit): List<DomainEvent> {
        require(status == ReservationStatus.CONFIRMED) { "CONFIRMED 상태의 예약만 자동 방문 확정할 수 있다" }
        val autoConfirmVisitAt = reservationDateTime().plusDays(AUTO_CONFIRM_VISIT_MIN_DAYS)
        require(!command.confirmedAt.isBefore(autoConfirmVisitAt)) {
            "자동 방문 확정은 예약 시각으로부터 7일이 지난 후에만 가능하다"
        }
        return listOf(
            VisitConfirmed(
                reservationId = reservationId,
                confirmedBy = VisitConfirmer.SYSTEM,
                confirmedAt = command.confirmedAt,
            ),
        )
    }

    private fun applyReservationConfirmed(event: ReservationConfirmed): Reservation =
        copy(status = ReservationStatus.CONFIRMED, confirmedAt = event.confirmedAt)

    private fun applyReservationFailed(event: ReservationFailed): Reservation =
        copy(status = ReservationStatus.FAILED, failedAt = event.failedAt)

    private fun applyReservationExpired(event: ReservationExpired): Reservation =
        copy(status = ReservationStatus.EXPIRED, expiredAt = event.expiredAt)

    private fun applyReservationCancelled(event: ReservationCancelled): Reservation =
        copy(
            status = ReservationStatus.CANCELLED,
            cancelledBy = event.cancelledBy,
            cancelReason = event.reason,
            cancelledAt = event.cancelledAt,
        )

    private fun applyReservationNoShow(event: ReservationNoShow): Reservation =
        copy(status = ReservationStatus.NO_SHOW, judgedAt = event.judgedAt)

    private fun applyVisitConfirmed(event: VisitConfirmed): Reservation =
        copy(
            status = ReservationStatus.VISITED,
            visitConfirmedBy = event.confirmedBy,
            visitConfirmedAt = event.confirmedAt,
        )

    companion object {
        private const val GUEST_CANCEL_DEADLINE_DAYS = 3L
        private const val AUTO_CONFIRM_VISIT_MIN_DAYS = 7L
        private const val OWNER_REASON_MIN_LENGTH = 30
        private const val OWNER_REASON_MAX_LENGTH = 199
        private val UUID_REGEX =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        private fun requireValidId(
            value: String,
            label: String,
        ) {
            require(value.isNotBlank() && UUID_REGEX.matches(value)) { "$label 은 UUID 포맷이어야 한다" }
        }

        fun handle(command: CreateReservation): List<DomainEvent> {
            require(command.date.isAfter(command.requestedAt.toLocalDate())) { "예약 날짜는 미래여야 한다" }
            require(command.startTime.isBefore(command.endTime)) { "시작 시각은 종료 시각보다 빨라야 한다" }
            require(command.date.dayOfWeek == command.day) { "날짜의 요일과 day가 일치해야 한다" }
            require(command.tableNumber >= 1) { "테이블 번호는 1 이상이어야 한다" }
            require(command.tableSize >= 1) { "테이블 인원은 1 이상이어야 한다" }
            requireValidId(command.reservationId, "reservationId")
            requireValidId(command.userId, "userId")
            requireValidId(command.restaurantId, "restaurantId")
            requireValidId(command.slotId, "slotId")
            return listOf(
                ReservationCreated(
                    reservationId = command.reservationId,
                    userId = command.userId,
                    restaurantId = command.restaurantId,
                    tableNumber = command.tableNumber,
                    tableSize = command.tableSize,
                    slotId = command.slotId,
                    date = command.date,
                    day = command.day,
                    startTime = command.startTime,
                    endTime = command.endTime,
                    requestedAt = command.requestedAt,
                ),
            )
        }

        fun from(event: ReservationCreated): Reservation =
            Reservation(
                reservationId = event.reservationId,
                userId = event.userId,
                restaurantId = event.restaurantId,
                tableNumber = event.tableNumber,
                tableSize = event.tableSize,
                slotId = event.slotId,
                date = event.date,
                day = event.day,
                startTime = event.startTime,
                endTime = event.endTime,
                status = ReservationStatus.PENDING,
                requestedAt = event.requestedAt,
                confirmedAt = null,
                failedAt = null,
                expiredAt = null,
                cancelledBy = null,
                cancelReason = null,
                cancelledAt = null,
                judgedAt = null,
                visitConfirmedBy = null,
                visitConfirmedAt = null,
            )
    }
}
