package com.reservation.restaurant.service

import com.reservation.restaurant.Restaurant
import com.reservation.restaurant.exceptions.InvalidateRestaurantElementException
import com.reservation.restaurant.policy.format.CreateRestaurantForm
import com.reservation.restaurant.policy.validations.RestaurantAddressEmptyValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantAddressLengthValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantAddressPolicy
import com.reservation.restaurant.policy.validations.RestaurantCoordinateBoundaryValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantCoordinateFormatValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantCoordinatePolicy
import com.reservation.restaurant.policy.validations.RestaurantIntroduceLengthPolicy
import com.reservation.restaurant.policy.validations.RestaurantIntroducePolicy
import com.reservation.restaurant.policy.validations.RestaurantNameEmptyValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantNameLengthValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantNamePolicy
import com.reservation.restaurant.policy.validations.RestaurantPhoneEmptyValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantPhoneFormatValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantPhoneLengthValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantPhonePolicy
import com.reservation.restaurant.policy.validations.RestaurantUnifiedValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantZipCodeFormatValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantZipCodeLengthValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantZipCodePolicy
import com.reservation.restaurant.snapshot.RestaurantSnapshot
import com.reservation.restaurant.vo.RestaurantAddress
import com.reservation.restaurant.vo.RestaurantContact
import com.reservation.restaurant.vo.RestaurantCoordinate
import com.reservation.restaurant.vo.RestaurantDescription
import java.math.BigDecimal

class CreateRestaurantService {
    private val restaurantNamePolicy: List<RestaurantNamePolicy> =
        listOf(
            RestaurantNameEmptyValidationPolicy(),
            RestaurantNameLengthValidationPolicy(),
        )
    private val restaurantIntroductionPolicy: List<RestaurantIntroducePolicy> =
        listOf(
            RestaurantIntroduceLengthPolicy(),
        )
    private val restaurantPhonePolicy: List<RestaurantPhonePolicy> =
        listOf(
            RestaurantPhoneEmptyValidationPolicy(),
            RestaurantPhoneLengthValidationPolicy(),
            RestaurantPhoneFormatValidationPolicy(),
        )
    private val restaurantAddressPolicy: List<RestaurantAddressPolicy> =
        listOf(
            RestaurantAddressEmptyValidationPolicy(),
            RestaurantAddressLengthValidationPolicy(),
        )
    private val restaurantZipCodePolicy: List<RestaurantZipCodePolicy> =
        listOf(
            RestaurantZipCodeLengthValidationPolicy(),
            RestaurantZipCodeFormatValidationPolicy(),
        )
    private val restaurantCoordinatePolicy: List<RestaurantCoordinatePolicy> =
        listOf(
            RestaurantCoordinateFormatValidationPolicy(),
            RestaurantCoordinateBoundaryValidationPolicy(),
        )

    private fun <T : RestaurantUnifiedValidationPolicy<String>> List<T>.validatePolicies(
        target: String,
    ) = firstOrNull { !it.validate(target) }
        ?.let {
            throw InvalidateRestaurantElementException(it.reason)
        }

    private fun <
        T : RestaurantUnifiedValidationPolicy<Pair<BigDecimal, BigDecimal>>,
    > List<T>.validatePolicies(
        target: Pair<BigDecimal, BigDecimal>,
    ) {
        firstOrNull { !it.validate(target) }
            ?.let {
                throw InvalidateRestaurantElementException(it.reason)
            }
    }

    fun create(form: CreateRestaurantForm): RestaurantSnapshot {
        validate(form)

        val restaurant =
            Restaurant(
                companyId = form.companyId,
                introduce =
                    RestaurantDescription(
                        name = form.name,
                        introduce = form.introduce,
                    ),
                contact =
                    RestaurantContact(
                        phone = form.phone,
                    ),
                address =
                    RestaurantAddress(
                        zipCode = form.zipCode,
                        address = form.address,
                        detail = form.detail,
                        coordinate =
                            RestaurantCoordinate(
                                latitude = form.latitude,
                                longitude = form.longitude,
                            ),
                    ),
            )

        return restaurant.snapshot()
    }

    private fun validate(form: CreateRestaurantForm) {
        validateRestaurantName(form.name)
        validateRestaurantIntroduction(form.introduce)
        validateRestaurantPhone(form.phone)
        validateRestaurantAddress(form.address)
        validateRestaurantZipCode(form.zipCode)
        validateRestaurantCoordinate(form.latitude, form.longitude)
    }

    /**
     * 음식점 이름에 대한 검증을 진행합니다.
     * - 음식점 이름이 비어있는지 검증합니다.
     * - 글자 수에 대해서 검증합니다.
     */
    private fun validateRestaurantName(name: String) {
        restaurantNamePolicy.validatePolicies(name)
    }

    /**
     * 음식점 소개에 대한 검증을 진행합니다.
     * - 글자 수에 대해서 검증합니다.
     */
    private fun validateRestaurantIntroduction(introduction: String) {
        restaurantIntroductionPolicy.validatePolicies(introduction)
    }

    /**
     * 음식점 전화번호에 대한 검증을 진행합니다.
     * - 전화번호가 비어있는지 검증합니다.
     * - 전화번호의 길이에 대해서 검증합니다.
     * - 전화번호 형식에 대해서 검증합니다.
     */
    private fun validateRestaurantPhone(phone: String) {
        restaurantPhonePolicy.validatePolicies(phone)
    }

    /**
     * 음식점 주소에 대해서 검증을 진행합니다.
     * - 음식점 주소가 비어있는지 검증홥니다.
     * - 음식점 주소의 길이에 대해서 검증합니다.
     */
    private fun validateRestaurantAddress(address: String) {
        restaurantAddressPolicy.validatePolicies(address)
    }

    /**
     * 음식점 우편번호에 대해서 검증을 진행합니다.
     * - 음식점 우편번호가 비어있는지 검증합니다.
     * - 음식점 우편번호 형식에 대해서 검증합니다.
     */
    private fun validateRestaurantZipCode(zipCode: String) {
        restaurantZipCodePolicy.validatePolicies(zipCode)
    }

    /**
     * 음식점 좌표에 대해서 검증을 진행합니다.
     * - 위도, 경도의 형식에 대해서 검증합니다.
     * - 위도, 겅도가 한국에 국한되어 있는지 검증합니다.
     */
    private fun validateRestaurantCoordinate(
        latitude: BigDecimal,
        longitude: BigDecimal,
    ) {
        restaurantCoordinatePolicy.validatePolicies(latitude to longitude)
    }
}
