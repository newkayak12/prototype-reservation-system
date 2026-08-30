package com.reservation.queue.config

import com.reservation.queue.service.IssueWaitingTicketDomainService
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component

@Component
class QueueServiceFactory {
    @Bean
    fun issueWaitingTicketDomainService() = IssueWaitingTicketDomainService()
}
