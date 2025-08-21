package com.reservation.rest.menu.request

import com.reservation.menu.port.input.request.CreateMenuCommand
import jakarta.validation.constraints.NotEmpty
import org.hibernate.validator.constraints.Length
import org.hibernate.validator.constraints.Range
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal

data class CreateMenuRequest(
    @field:NotEmpty
    val restaurantId: String,
    @field:NotEmpty
    @field:Length(min = 1, max = 30)
    val title: String,
    @field:NotEmpty
    @field:Length(min = 1, max = 255)
    val description: String,
    @field:Range(min = 0, max = 999_999_999)
    val price: BigDecimal,
    val isRepresentative: Boolean = false,
    val isRecommended: Boolean = false,
    val isVisible: Boolean = false,
) {
    fun toCommand(photos: List<MultipartFile>): CreateMenuCommand =
        CreateMenuCommand(
            restaurantId = restaurantId,
            title = title,
            description = description,
            price = price,
            isRepresentative = isRepresentative,
            isRecommended = isRecommended,
            isVisible = isVisible,
            photoUrl = photos,
        )
}
