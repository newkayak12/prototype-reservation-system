package com.reservation.contract.event.reservation

// 취소 행위자는 reservation 계약에 닫힌 도메인 의미이므로 shared-module이나 String이 아닌
// contract-module 로컬 enum으로 제한한다.
// 근거: docs/v2/modules/02b-contract-module-phase7-1-reservation-event-catalog-decision.md §5.
enum class CancelledBy {
    GUEST,
    OWNER,
}
