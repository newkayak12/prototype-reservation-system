package com.reservation.queue

import com.reservation.enumeration.QueueStatus.ADMITTED
import com.reservation.enumeration.QueueStatus.WAITING
import com.reservation.queue.policy.exceptions.InvalidWaitingTicketException
import com.reservation.queue.service.IssueWaitingTicketDomainService
import com.reservation.queue.vo.WaitingQueueSlot
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDate
import java.time.LocalTime

class IssueWaitingTicketDomainServiceTest : BehaviorSpec(
    {
        val domainService = IssueWaitingTicketDomainService()

        fun slot(restaurantId: String = UuidGenerator.generate()) =
            WaitingQueueSlot(
                restaurantId = restaurantId,
                date = LocalDate.of(2026, 8, 26),
                startTime = LocalTime.of(11, 0),
            )

        Given("빈 userId가 주어졌을 때") {
            When("대기열 티켓 발급을 요청하면") {
                Then("InvalidWaitingTicketException이 발생한다") {
                    shouldThrow<InvalidWaitingTicketException> {
                        domainService.issue("", slot())
                    }
                }
            }
        }

        Given("빈 restaurantId가 주어졌을 때") {
            When("대기열 티켓 발급을 요청하면") {
                Then("InvalidWaitingTicketException이 발생한다") {
                    shouldThrow<InvalidWaitingTicketException> {
                        domainService.issue(UuidGenerator.generate(), slot(""))
                    }
                }
            }
        }

        Given("동일한 사용자와 동일한 슬롯이 주어졌을 때") {
            val userId = UuidGenerator.generate()
            val target = slot()

            When("대기열 티켓 발급을 두 번 요청하면") {
                val first = domainService.issue(userId, target)
                val second = domainService.issue(userId, target)

                // ticketId는 nonce다. 같은 입력이라도 매번 새 값이 나오는 것이 정상이며,
                // "같은 사용자 = 같은 티켓"을 보장하는 책임은 도메인이 아니라 진입 시점의
                // TICKET_OF 선점(EnterWaitingQueue)에 있다.
                Then("매번 새로운 ticketId가 발급된다") {
                    first.ticketId shouldNotBe second.ticketId
                }

                Then("ticketId는 userId를 그대로 노출하지 않는다") {
                    first.ticketId.contains(userId) shouldBe false
                }

                Then("최초 상태는 WAITING이다") {
                    first.getStatus shouldBe WAITING
                    first.getPosition shouldBe null
                }
            }

            When("이미 실려 있던 ticketId로 티켓을 되살리면") {
                val restored = domainService.restore(userId, target, "fixed-ticket-id")

                Then("주어진 ticketId를 그대로 쓴다") {
                    restored.ticketId shouldBe "fixed-ticket-id"
                    restored.getStatus shouldBe WAITING
                }
            }

            When("빈 userId로 티켓을 되살리려 하면") {
                Then("발급 경로와 동일하게 정책 검증에 걸린다") {
                    shouldThrow<InvalidWaitingTicketException> {
                        domainService.restore("", target, "fixed-ticket-id")
                    }
                }
            }
        }

        Given("서로 다른 사용자가 같은 슬롯에 진입할 때") {
            val target = slot()

            When("각각 티켓 발급을 요청하면") {
                val first = domainService.issue(UuidGenerator.generate(), target)
                val second = domainService.issue(UuidGenerator.generate(), target)

                Then("서로 다른 ticketId가 발급된다") {
                    first.ticketId shouldNotBe second.ticketId
                }
            }
        }

        Given("발급된 티켓이 주어졌을 때") {
            val ticket = domainService.issue(UuidGenerator.generate(), slot())

            When("대기열에 등록되면") {
                ticket.enqueued(3)

                Then("WAITING 상태와 순번을 갖는다") {
                    ticket.getStatus shouldBe WAITING
                    ticket.getPosition shouldBe 3
                }
            }

            When("입장이 허용되면") {
                ticket.admitted()

                Then("ADMITTED 상태가 되고 순번은 사라진다") {
                    ticket.getStatus shouldBe ADMITTED
                    ticket.getPosition shouldBe null
                }
            }
        }

        Given("슬롯 키가 주어졌을 때") {
            val target = slot()

            When("키를 다시 파싱하면") {
                val restored = WaitingQueueSlot.from(target.key())

                Then("원본 슬롯과 동일하다") {
                    restored shouldBe target
                }
            }
        }
    },
)
