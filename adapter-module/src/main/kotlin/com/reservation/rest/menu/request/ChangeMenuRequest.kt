package com.reservation.rest.menu.request

import com.reservation.menu.port.input.request.UpdateMenuCommand
import com.reservation.menu.port.input.request.UpdateMenuCommand.UpdateMenuPreviousImage
import jakarta.validation.constraints.NotEmpty
import org.hibernate.validator.constraints.Length
import org.hibernate.validator.constraints.Range
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal

data class ChangeMenuRequest(
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
    val photoUrl: List<String>,
) {
    fun toCommand(
        id: String,
        photos: List<MultipartFile>,
    ): UpdateMenuCommand =
        UpdateMenuCommand(
            id = id,
            restaurantId = restaurantId,
            title = title,
            description = description,
            price = price,
            isRepresentative = isRepresentative,
            isRecommended = isRecommended,
            isVisible = isVisible,
            photoUrl = photoUrl.map { UpdateMenuPreviousImage(it) },
            photos = photos,
        )
}
