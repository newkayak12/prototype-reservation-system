package com.reservation.rest.queue.enter

import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderUseCase
import com.reservation.queue.port.input.EnterWaitingQueueUseCase
import com.reservation.rest.queue.WaitingQueueUrl
import com.reservation.rest.queue.request.EnterWaitingQueueRequest
import com.reservation.rest.queue.response.EnterWaitingQueueResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class EnterWaitingQueueController(
    private val enterWaitingQueueUseCase: EnterWaitingQueueUseCase,
    private val extractIdentifierFromHeaderUseCase: ExtractIdentifierFromHeaderUseCase,
) {
    @PostMapping(WaitingQueueUrl.QUEUE)
    @ResponseStatus(HttpStatus.CREATED)
    fun enterWaitingQueue(
        @RequestHeader header: HttpHeaders,
        @PathVariable("restaurantId") restaurantId: String,
        @RequestBody request: EnterWaitingQueueRequest,
    ): EnterWaitingQueueResponse {
        val userId =
            extractIdentifierFromHeaderUseCase.execute(
                header.getFirst(HttpHeaders.AUTHORIZATION),
            )

        return EnterWaitingQueueResponse.from(
            enterWaitingQueueUseCase.execute(request.toCommand(userId, restaurantId)),
        )
    }
}
