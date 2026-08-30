package com.reservation.queue.service

import com.reservation.queue.WaitingTicket
import com.reservation.queue.policy.exceptions.InvalidWaitingTicketException
import com.reservation.queue.policy.validation.WaitingTicketPolicy
import com.reservation.queue.policy.validation.WaitingTicketRestaurantIdIsNotEmptyValidationPolicy
import com.reservation.queue.policy.validation.WaitingTicketUserIdIsNotEmptyValidationPolicy
import com.reservation.queue.vo.WaitingQueueSlot
import java.security.SecureRandom

/**
 * 대기열 티켓을 발급한다.
 *
 * `ticketId`는 서버가 발급하는 난수(nonce)다. 사용자 식별자에서 유도하지 않으므로 티켓만 보고
 * 누구의 것인지 역산할 수 없고, 남의 티켓을 계산해서 만들어낼 수도 없다.
 *
 * ## 여기서 나오는 것은 "후보"다
 *
 * 같은 사용자가 같은 슬롯에 두 번 진입하면 nonce도 두 개가 나온다. 그중 실제로 대기열에 실린
 * 하나를 고르는 것은 [com.reservation.queue.port.output.EnterWaitingQueue]의 몫이다 —
 * `TICKET_OF:{slot}:{userId}`를 선점한 쪽이 이기고, 진 쪽은 이미 실려 있던 티켓을 돌려받는다.
 *
 * 결정적 해시(`SHA-256(userId:slot)`)를 쓰던 시절에는 "같은 입력 → 같은 티켓"이 이 멱등성을
 * 공짜로 주고 있었다. nonce로 바꾸면서 그 역할을 저 인덱스가 넘겨받았다 — 인덱스가 없으면
 * 사용자가 진입을 반복 호출해 대기열에 여러 자리를 잡고 입장 정원을 여러 번 소모할 수 있다.
 */
class IssueWaitingTicketDomainService {
    companion object {
        /** 32자 hex. 기존 해시 절단본과 길이가 같아 저장소 스키마를 건드리지 않는다. */
        private const val NONCE_BYTE_LENGTH = 16
        private const val HEX_MASK = 0xff
        private const val HEX_RADIX = 16
        private const val HEX_PAD_LENGTH = 2
    }

    private val random = SecureRandom()

    private val userIdPolicies: List<WaitingTicketPolicy> =
        listOf(WaitingTicketUserIdIsNotEmptyValidationPolicy())

    private val restaurantIdPolicies: List<WaitingTicketPolicy> =
        listOf(WaitingTicketRestaurantIdIsNotEmptyValidationPolicy())

    private fun List<WaitingTicketPolicy>.validate(target: String) =
        firstOrNull { !it.validate(target) }
            ?.let { throw InvalidWaitingTicketException(it.reason) }

    private fun nonce(): String {
        val bytes = ByteArray(NONCE_BYTE_LENGTH).also(random::nextBytes)

        return bytes.joinToString("") {
            (it.toInt() and HEX_MASK).toString(HEX_RADIX).padStart(HEX_PAD_LENGTH, '0')
        }
    }

    /** 새 후보 티켓을 발급한다. 실제로 대기열에 실릴지는 진입 시점에 결정된다. */
    fun issue(
        userId: String,
        slot: WaitingQueueSlot,
    ): WaitingTicket = restore(userId, slot, nonce())

    /**
     * 이미 대기열에 실려 있던 티켓을 도메인 객체로 되살린다.
     *
     * 진입해 보니 이 사용자에게 먼저 실린 티켓이 있었을 때 쓴다. 발급 경로와 같은 정책 검증을
     * 거치되 `ticketId`만 밖에서 주어진 값을 그대로 쓴다.
     */
    fun restore(
        userId: String,
        slot: WaitingQueueSlot,
        ticketId: String,
    ): WaitingTicket {
        userIdPolicies.validate(userId)
        restaurantIdPolicies.validate(slot.restaurantId)

        return WaitingTicket(
            ticketId = ticketId,
            slot = slot,
            userId = userId,
        )
    }
}
