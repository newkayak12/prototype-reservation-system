package com.reservation.category.cuisine

import com.reservation.category.shared.CategoryDetail
import com.reservation.enumeration.CategoryType.CUISINE

class Cuisine(
    val id: Long,
    title: String,
) {
    private var categoryDetail: CategoryDetail =
        CategoryDetail(
            title,
            CUISINE,
        )

    val title: String
        get() = categoryDetail.title

    fun changeTitle(newTitle: String) {
        this.categoryDetail = categoryDetail.copy(title = newTitle)
    }
}
