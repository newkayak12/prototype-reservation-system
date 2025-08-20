package com.reservation.rest.common.response

import com.reservation.rest.common.response.PageResponse.PageResultContainer
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

class PageResponse<E> private constructor(
    body: PageResultContainer<E>,
    httpStatus: HttpStatus,
) : ResponseEntity<PageResultContainer<E>>(
        body,
        httpStatus,
    ) {
    data class PageResultContainer<E>(
        val list: List<E>,
        val hasNext: Boolean,
    )

    companion object {
        fun <E> ok(
            list: List<E>,
            hasNext: Boolean,
        ) = PageResponse(PageResultContainer(list, hasNext), HttpStatus.OK)
    }
}
