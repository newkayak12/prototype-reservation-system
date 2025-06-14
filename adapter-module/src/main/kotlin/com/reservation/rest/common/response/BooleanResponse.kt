package com.reservation.rest.common.response

import com.reservation.rest.common.response.BooleanResponse.ResultContainer
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

class BooleanResponse private constructor(
    private val result: Boolean,
    private val httpStatus: HttpStatus,
) : ResponseEntity<ResultContainer<Boolean>>(ResultContainer(result), httpStatus) {
    data class ResultContainer<T>(
        val result: T,
    )

    companion object {
        fun created(result: Boolean): BooleanResponse = BooleanResponse(result, HttpStatus.CREATED)

        fun ok(result: Boolean): BooleanResponse = BooleanResponse(result, HttpStatus.OK)

        fun resetContents(result: Boolean): BooleanResponse =
            BooleanResponse(result, HttpStatus.RESET_CONTENT)
    }
}
