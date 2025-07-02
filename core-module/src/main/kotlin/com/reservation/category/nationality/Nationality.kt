package com.reservation.category.nationality

import com.reservation.category.shared.CategoryDetail

class Nationality(
    val id: Long,
    private var categoryDetail: CategoryDetail,
) {
    val title: String
        get() = categoryDetail.title

    fun changeTitle(newTitle: String) {
        this.categoryDetail = categoryDetail.copy(title = newTitle)
    }
}
