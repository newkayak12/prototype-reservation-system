package com.reservation.restaurant

import com.reservation.restaurant.vo.RestaurantAddress
import com.reservation.restaurant.vo.RestaurantContact
import com.reservation.restaurant.vo.RestaurantCuisines
import com.reservation.restaurant.vo.RestaurantDescription
import com.reservation.restaurant.vo.RestaurantNationalities
import com.reservation.restaurant.vo.RestaurantPhotoBook
import com.reservation.restaurant.vo.RestaurantRoutine
import com.reservation.restaurant.vo.RestaurantTags

class Restaurant(
    val id: String,
    val companyId: String,
    val introduce: RestaurantDescription,
    val contact: RestaurantContact,
    val address: RestaurantAddress,
) {
    val routine = RestaurantRoutine()
    val photos = RestaurantPhotoBook()
    val tags = RestaurantTags()
    val nationalities = RestaurantNationalities()
    val cuisines = RestaurantCuisines()
}
