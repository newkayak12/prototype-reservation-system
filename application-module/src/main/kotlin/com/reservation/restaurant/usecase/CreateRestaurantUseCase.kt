package com.reservation.restaurant.usecase

import com.reservation.common.exceptions.AlreadyPersistedException
import com.reservation.config.annotations.UseCase
import com.reservation.restaurant.policy.format.CreateRestaurantForm
import com.reservation.restaurant.port.input.CreateRestaurantCommand
import com.reservation.restaurant.port.input.CreateRestaurantCommand.CreateProductCommandDto
import com.reservation.restaurant.port.output.CheckRestaurantDuplicated
import com.reservation.restaurant.port.output.CheckRestaurantDuplicated.CheckRestaurantDuplicatedInquiry
import com.reservation.restaurant.port.output.CreateRestaurant
import com.reservation.restaurant.port.output.CreateRestaurant.CreateProductInquiry
import com.reservation.restaurant.port.output.CreateRestaurant.Photo
import com.reservation.restaurant.port.output.CreateRestaurant.WorkingDay
import com.reservation.restaurant.service.CreateRestaurantService
import com.reservation.restaurant.snapshot.RestaurantSnapshot
import org.springframework.transaction.annotation.Transactional

@UseCase
class CreateRestaurantUseCase(
    private val createRestaurant: CreateRestaurant,
    private val checkRestaurantDuplicated: CheckRestaurantDuplicated,
    private val createRestaurantService: CreateRestaurantService,
) : CreateRestaurantCommand {
    private fun checkDuplicated(
        companyId: String,
        restaurantName: String,
    ) {
        val isDuplicated =
            checkRestaurantDuplicated.query(
                CheckRestaurantDuplicatedInquiry(companyId, restaurantName),
            )

        if (isDuplicated) {
            throw AlreadyPersistedException()
        }
    }

    private fun createRestaurant(request: CreateProductCommandDto): RestaurantSnapshot {
        val form =
            CreateRestaurantForm(
                companyId = request.companyId,
                name = request.name,
                introduce = request.introduce,
                phone = request.phone,
                zipCode = request.zipCode,
                address = request.address,
                detail = request.detail,
                latitude = request.latitude,
                longitude = request.longitude,
                workingDays = request.workingDays,
                photos = request.photos,
                tags = request.tags,
                nationalities = request.nationalities,
                cuisines = request.cuisines,
            )

        return createRestaurantService.create(form)
    }

    private fun insertRestaurant(snapshot: RestaurantSnapshot): Boolean {
        val inquiry =
            CreateProductInquiry(
                companyId = snapshot.companyId,
                name = snapshot.name,
                introduce = snapshot.introduce,
                phone = snapshot.phone,
                zipCode = snapshot.zipCode,
                address = snapshot.address,
                detail = snapshot.detail,
                latitude = snapshot.latitude,
                longitude = snapshot.longitude,
                workingDays =
                    snapshot.workingDays.map {
                        WorkingDay(it.day, it.startTime, it.endTime)
                    },
                photos = snapshot.photos.map { Photo(it.url) },
                tags = snapshot.tags,
                nationalities = snapshot.nationalities,
                cuisines = snapshot.cuisines,
            )

        return createRestaurant.command(inquiry)
    }

    @Transactional
    override fun execute(request: CreateProductCommandDto): Boolean {
        checkDuplicated(request.companyId, request.name)

        val snapshot = createRestaurant(request)

        return insertRestaurant(snapshot)
    }
}
