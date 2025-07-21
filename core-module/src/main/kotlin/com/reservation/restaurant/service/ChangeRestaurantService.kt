package com.reservation.restaurant.service

import com.reservation.restaurant.Restaurant
import com.reservation.restaurant.policy.format.ChangeRestaurantForm
import com.reservation.restaurant.service.update.UpdateCuisines
import com.reservation.restaurant.service.update.UpdateNationalities
import com.reservation.restaurant.service.update.UpdatePhoto
import com.reservation.restaurant.service.update.UpdateRoutine
import com.reservation.restaurant.service.update.UpdateTag
import com.reservation.restaurant.service.validate.ValidateAddress
import com.reservation.restaurant.service.validate.ValidateCoordinate
import com.reservation.restaurant.service.validate.ValidateIntroduce
import com.reservation.restaurant.service.validate.ValidateName
import com.reservation.restaurant.service.validate.ValidatePhone
import com.reservation.restaurant.service.validate.ValidateZipCode
import com.reservation.restaurant.snapshot.RestaurantSnapshot
import com.reservation.restaurant.vo.RestaurantAddress
import com.reservation.restaurant.vo.RestaurantContact
import com.reservation.restaurant.vo.RestaurantCoordinate
import com.reservation.restaurant.vo.RestaurantDescription

@Suppress("TooManyFunctions")
class ChangeRestaurantService(
    private val updateRoutine: UpdateRoutine,
    private val updatePhoto: UpdatePhoto,
    private val updateTag: UpdateTag,
    private val updateNationalities: UpdateNationalities,
    private val updateCuisines: UpdateCuisines,
) {
    private val validateName = ValidateName()
    private val validateIntroduce = ValidateIntroduce()
    private val validatePhone = ValidatePhone()
    private val validateRestaurantAddress = ValidateAddress()
    private val validateZipCode = ValidateZipCode()
    private val validateCoordinate = ValidateCoordinate()

    private fun validate(form: ChangeRestaurantForm) {
        validateName.validate(form.name)
        validateIntroduce.validate(form.introduce)
        validatePhone.validate(form.phone)
        validateRestaurantAddress.validate(form.address)
        validateZipCode.validate(form.zipCode)
        validateCoordinate.validate(form.latitude, form.longitude)
    }

    private fun updateDescription(
        restaurant: Restaurant,
        name: String,
        introduce: String,
    ) = restaurant.updateDescription(RestaurantDescription(name, introduce))

    private fun updateContact(
        restaurant: Restaurant,
        phone: String,
    ) = restaurant.updateContact(RestaurantContact(phone))

    private fun updateAddress(
        restaurant: Restaurant,
        zipCode: String,
        address: String,
        detail: String,
        coordinate: RestaurantCoordinate,
    ) = restaurant.updateLocation(
        RestaurantAddress(
            zipCode,
            address,
            detail,
            coordinate,
        ),
    )

    fun change(
        restaurant: Restaurant,
        changeRestaurantForm: ChangeRestaurantForm,
    ): RestaurantSnapshot {
        validate(changeRestaurantForm)

        updateDescription(restaurant, changeRestaurantForm.name, changeRestaurantForm.introduce)
        updateContact(restaurant, changeRestaurantForm.phone)
        updateAddress(
            restaurant,
            changeRestaurantForm.zipCode,
            changeRestaurantForm.address,
            changeRestaurantForm.detail,
            RestaurantCoordinate(changeRestaurantForm.latitude, changeRestaurantForm.longitude),
        )

        updateRoutine.adjust(restaurant, changeRestaurantForm.workingDays)
        updatePhoto.adjust(restaurant, changeRestaurantForm.photos)
        updateTag.adjust(restaurant, changeRestaurantForm.tags)
        updateNationalities.adjust(restaurant, changeRestaurantForm.nationalities)
        updateCuisines.adjust(restaurant, changeRestaurantForm.cuisines)

        return restaurant.snapshot()
    }
}
