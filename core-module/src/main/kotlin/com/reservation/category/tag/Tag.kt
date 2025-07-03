package com.reservation.category.tag

import com.reservation.category.shared.vo.CategoryDetail
import com.reservation.enumeration.CategoryType.TAG

class Tag(
    val id: Long,
    title: String,
) {
    private var categoryDetail: CategoryDetail

    init {
        categoryDetail =
            CategoryDetail(
                title,
                TAG,
            )
    }

    val title: String
        get() = categoryDetail.title

    fun changeTitle(newTitle: String) {
        this.categoryDetail = categoryDetail.copy(title = newTitle)
    }
}
