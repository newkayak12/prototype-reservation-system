package com.reservation.queue.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.queue.port.input.AdmitSlotUseCase
import com.reservation.queue.port.output.AdmitWaitingTickets
import com.reservation.queue.port.output.AdmitWaitingTickets.AdmitInquiry
import com.reservation.queue.vo.WaitingQueueSlot
import org.springframework.beans.factory.annotation.Value
import java.time.Duration

/**
 * 승격의 최소 단위. 정원과 수명을 설정에서 읽어 [AdmitWaitingTickets]에 넘기는 것이 전부다.
 *
 * ## 정원(capacity)은 어떻게 정하는가
 *
 * 임의로 고르면 안 되는 값이다. 대기열이 폴링 기반이므로 permit 하나의 회전 주기는
 *
 * ```
 * 승격 → 사용자가 다음 폴링에서 알아챔(폴링간격/2) → 예약 호출(약 40ms) → 반납
 * ```
 *
 * 이고, 따라서 처리량 상한이 `capacity ÷ 회전주기`로 고정된다. 뒤집으면
 *
 * ```
 * capacity = 목표처리량 × (폴링간격/2 + 예약지연)
 * ```
 *
 * 이 산식이 이 값의 근거다. 폴링 간격(=CDN TTL)을 늘리면 capacity를 비례해서 올려
 * 갚아야 처리량이 유지된다 — 둘은 따로 만질 수 있는 손잡이가 아니다.
 *
 * 다만 capacity는 곧 "예약 API에 동시에 들어와 있는 요청 수"이기도 해서, 서블릿
 * 스레드 풀보다 크게 잡으면 배압이 앱 바깥이 아니라 Tomcat 큐에 쌓인다. 대기열을 둔
 * 이유가 사라지므로 그 아래에 두어야 한다.
 */
@UseCase
class AdmitSlotService(
    private val admitWaitingTickets: AdmitWaitingTickets,
    @Value("\${reservation.queue.admission-capacity:100}")
    private val admissionCapacity: Int,
    @Value("\${reservation.queue.admission-time-to-live-seconds:300}")
    private val admissionTimeToLiveSeconds: Long,
) : AdmitSlotUseCase {
    override fun execute(slot: WaitingQueueSlot): Int =
        admitWaitingTickets.admit(
            AdmitInquiry(
                slot = slot,
                capacity = admissionCapacity,
                admissionTimeToLive = Duration.ofSeconds(admissionTimeToLiveSeconds),
            ),
        )
}
