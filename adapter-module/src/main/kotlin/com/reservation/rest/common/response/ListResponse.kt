package com.reservation.rest.common.response

import com.reservation.rest.common.response.ListResponse.ListResultContainer
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

data class ListResponse<E> private constructor(
    val body: ListResultContainer<E>,
    val httpStatus: HttpStatus,
) : ResponseEntity<ListResultContainer<E>>(
        body,
        httpStatus,
    ) {
    data class ListResultContainer<E>(
        val list: List<E>,
    )

    companion object {
        fun <T> created(result: List<T>): ListResponse<T> =
            ListResponse(ListResultContainer(result), HttpStatus.CREATED)

        fun <T> ok(result: List<T>): ListResponse<T> =
            ListResponse(ListResultContainer(result), HttpStatus.OK)

        fun <T> resetContents(result: List<T>): ListResponse<T> =
            ListResponse(ListResultContainer(result), HttpStatus.RESET_CONTENT)
    }
}
