package com.reservation.category.nationality

import com.reservation.category.shared.vo.CategoryDetail
import com.reservation.enumeration.CategoryType.NATIONALITY

class Nationality(
    val id: Long,
    title: String,
) {
    private var categoryDetail: CategoryDetail =
        CategoryDetail(
            title,
            NATIONALITY,
        )

    val title: String
        get() = categoryDetail.title

    fun changeTitle(newTitle: String) {
        this.categoryDetail = categoryDetail.copy(title = newTitle)
    }
}
