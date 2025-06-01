package com.reservation.rest.common.response

import com.reservation.rest.common.response.BooleanResponse.ResultContainer
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

data class BooleanResponse private constructor(
    val result: Boolean,
    val httpStatus: HttpStatus,
) : ResponseEntity<ResultContainer>(ResultContainer(result), httpStatus) {
    data class ResultContainer(
        val result: Boolean,
    )

    companion object {
        fun created(result: Boolean): BooleanResponse = BooleanResponse(result, HttpStatus.CREATED)

        fun ok(result: Boolean): BooleanResponse = BooleanResponse(result, HttpStatus.OK)

        fun resetContents(result: Boolean): BooleanResponse =
            BooleanResponse(result, HttpStatus.RESET_CONTENT)
    }
}
