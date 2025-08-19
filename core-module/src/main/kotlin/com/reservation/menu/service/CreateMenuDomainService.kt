package com.reservation.menu.service

import com.reservation.menu.Menu
import com.reservation.menu.exceptions.InvalidateMenuElementException
import com.reservation.menu.policy.format.CreateMenuForm
import com.reservation.menu.policy.validations.MenuDescriptionEmptyValidationPolicy
import com.reservation.menu.policy.validations.MenuDescriptionLengthValidationPolicy
import com.reservation.menu.policy.validations.MenuPriceRangeValidationPolicy
import com.reservation.menu.policy.validations.MenuRestaurantIdEmptyValidationPolicy
import com.reservation.menu.policy.validations.MenuTitleEmptyValidationPolicy
import com.reservation.menu.policy.validations.MenuTitleLengthValidationPolicy
import com.reservation.menu.policy.validations.MenuUnifiedValidationPolicy
import com.reservation.menu.snapshot.MenuSnapshot
import com.reservation.menu.vo.MenuAttributes
import com.reservation.menu.vo.MenuDescription
import com.reservation.menu.vo.MenuPhoto
import com.reservation.menu.vo.MenuPrice
import java.math.BigDecimal

class CreateMenuDomainService {
    private val titlePolicy =
        listOf(
            MenuTitleEmptyValidationPolicy(),
            MenuTitleLengthValidationPolicy(),
        )
    private val descriptionPolicy =
        listOf(
            MenuDescriptionEmptyValidationPolicy(),
            MenuDescriptionLengthValidationPolicy(),
        )
    private val pricePolicy = listOf(MenuPriceRangeValidationPolicy())
    private val restaurantPolicy = listOf(MenuRestaurantIdEmptyValidationPolicy())

    private fun <T : MenuUnifiedValidationPolicy<String>> List<T>.validatePolicies(target: String) =
        firstOrNull { !it.validate(target) }
            ?.let {
                throw InvalidateMenuElementException(it.reason)
            }

    private fun <
        T : MenuUnifiedValidationPolicy<BigDecimal>,
    > List<T>.validatePolicies(
        target: BigDecimal,
    ) = firstOrNull { !it.validate(target) }
        ?.let {
            throw InvalidateMenuElementException(it.reason)
        }

    private fun validateTitle(title: String) = titlePolicy.validatePolicies(title)

    private fun validateDescription(description: String) =
        descriptionPolicy.validatePolicies(description)

    private fun validatePrice(price: BigDecimal) = pricePolicy.validatePolicies(price)

    private fun validateRestaurantId(restaurantId: String) =
        restaurantPolicy.validatePolicies(restaurantId)

    private fun validate(form: CreateMenuForm) {
        validateTitle(form.title)
        validateDescription(form.description)
        validatePrice(form.price)
        validateRestaurantId(form.restaurantId)
    }

    fun createMenu(form: CreateMenuForm): MenuSnapshot {
        validate(form)

        val menu =
            Menu(
                restaurantId = form.restaurantId,
                information =
                    MenuDescription(
                        title = form.title,
                        description = form.description,
                    ),
                photos = form.photoUrl.map { MenuPhoto(it) },
                attributes =
                    MenuAttributes(
                        isRepresentative = form.isRepresentative,
                        isRecommended = form.isRecommended,
                        isVisible = form.isVisible,
                    ),
                price = MenuPrice(form.price),
            )

        return menu.snapshot()
    }
}
