package com.reservation.menu.port.output

import com.reservation.menu.Menu
import java.math.BigDecimal

interface LoadMenuById {
    fun loadById(id: String): Menu?

    data class LoadMenu(
        val id: String,
        val restaurantId: String,
        val title: String,
        val description: String,
        val photos: List<LoadMenuPhoto>,
        val isRepresentative: Boolean,
        val isRecommended: Boolean,
        val isVisible: Boolean,
        val price: BigDecimal,
    ) {
        data class LoadMenuPhoto(
            val url: String,
        )
    }
}
