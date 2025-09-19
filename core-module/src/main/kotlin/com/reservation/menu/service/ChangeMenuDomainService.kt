package com.reservation.menu.service

import com.reservation.menu.Menu
import com.reservation.menu.exceptions.InvalidateMenuElementException
import com.reservation.menu.policy.format.UpdateMenuForm
import com.reservation.menu.policy.validations.MenIdIsNotEmptyValidationPolicy
import com.reservation.menu.policy.validations.MenuDescriptionEmptyValidationPolicy
import com.reservation.menu.policy.validations.MenuDescriptionLengthValidationPolicy
import com.reservation.menu.policy.validations.MenuIdFormatValidationPolicy
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

@Suppress("TooManyFunctions")
class ChangeMenuDomainService {
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
    private val menuIdPolicy =
        listOf(MenuIdFormatValidationPolicy(), MenIdIsNotEmptyValidationPolicy())

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

    private fun validateMenuId(id: String) = menuIdPolicy.validatePolicies(id)

    private fun validateTitle(title: String) = titlePolicy.validatePolicies(title)

    private fun validateDescription(description: String) =
        descriptionPolicy.validatePolicies(description)

    private fun validatePrice(price: BigDecimal) = pricePolicy.validatePolicies(price)

    private fun validateRestaurantId(restaurantId: String) =
        restaurantPolicy.validatePolicies(restaurantId)

    private fun validate(form: UpdateMenuForm) {
        validateMenuId(form.id)
        validateTitle(form.title)
        validateDescription(form.description)
        validatePrice(form.price)
        validateRestaurantId(form.restaurantId)
    }

    private fun changeInformation(
        menu: Menu,
        title: String,
        description: String,
    ) = menu.changeInformation(MenuDescription(title, description))

    private fun changeAttributes(
        menu: Menu,
        isRepresentative: Boolean = false,
        isRecommended: Boolean = false,
        isVisible: Boolean = false,
    ) = menu.changeAttributes(
        MenuAttributes(
            isRepresentative,
            isRecommended,
            isVisible,
        ),
    )

    private fun changePrice(
        menu: Menu,
        price: BigDecimal,
    ) = menu.changePrice(MenuPrice(price))

    private fun changePhoto(
        menu: Menu,
        photoUrl: List<String>,
    ) {
        menu.manipulatePhoto {
            val all = it.allPhotos()

            all.filter { photoUrl.contains(it.url) }
                .forEach { element -> it.delete(element) }

            photoUrl
                .map { photoUrl -> MenuPhoto(photoUrl) }
                .filter { !all.contains(it) }
                .forEach { element -> it.add(element) }
        }
    }

    fun updateMenu(
        menu: Menu,
        form: UpdateMenuForm,
    ): MenuSnapshot {
        validate(form)
        changeInformation(menu, form.title, form.description)
        changeAttributes(menu, form.isRepresentative, form.isRecommended, form.isVisible)
        changePrice(menu, form.price)
        changePhoto(menu, form.photoUrl)

        return menu.snapshot()
    }
}
