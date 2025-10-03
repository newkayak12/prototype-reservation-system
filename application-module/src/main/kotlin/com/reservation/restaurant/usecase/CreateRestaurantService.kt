package com.reservation.restaurant.usecase

import com.reservation.common.exceptions.AlreadyPersistedException
import com.reservation.config.annotations.UseCase
import com.reservation.restaurant.event.CreateScheduleEvent
import com.reservation.restaurant.policy.format.CreateRestaurantForm
import com.reservation.restaurant.port.input.CreateRestaurantUseCase
import com.reservation.restaurant.port.input.command.request.CreateProductCommand
import com.reservation.restaurant.port.output.CheckRestaurantDuplicated
import com.reservation.restaurant.port.output.CheckRestaurantDuplicated.CheckRestaurantDuplicatedInquiry
import com.reservation.restaurant.port.output.CreateRestaurant
import com.reservation.restaurant.port.output.CreateRestaurant.CreateProductInquiry
import com.reservation.restaurant.port.output.CreateRestaurant.Photo
import com.reservation.restaurant.port.output.CreateRestaurant.WorkingDay
import com.reservation.restaurant.port.output.UploadRestaurantImageFile
import com.reservation.restaurant.service.CreateRestaurantDomainService
import com.reservation.restaurant.snapshot.RestaurantSnapshot
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@UseCase
class CreateRestaurantService(
    private val createRestaurant: CreateRestaurant,
    private val checkRestaurantDuplicated: CheckRestaurantDuplicated,
    private val createRestaurantDomainService: CreateRestaurantDomainService,
    private val uploadRestaurantImageFile: UploadRestaurantImageFile,
    private val applicationEventPublisher: ApplicationEventPublisher
) : CreateRestaurantUseCase {
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

    private fun uploadFiles(files: List<MultipartFile>): List<String> =
        if (files.isEmpty()) listOf() else uploadRestaurantImageFile.execute(files)

    private fun createRestaurant(request: CreateProductCommand): RestaurantSnapshot {
        val images: List<String> = uploadFiles(request.photos)

        val form =
            CreateRestaurantForm(
                companyId = request.companyId,
                userId = request.userId,
                name = request.name,
                introduce = request.introduce,
                phone = request.phone,
                zipCode = request.zipCode,
                address = request.address,
                detail = request.detail,
                latitude = request.latitude,
                longitude = request.longitude,
                workingDays = request.workingDays,
                photos = images,
                tags = request.tags,
                nationalities = request.nationalities,
                cuisines = request.cuisines,
            )

        return createRestaurantDomainService.create(form)
    }

    private fun insertRestaurant(snapshot: RestaurantSnapshot): String {
        val inquiry =
            CreateProductInquiry(
                companyId = snapshot.companyId,
                userId = snapshot.userId,
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
    override fun execute(request: CreateProductCommand): Boolean {
        checkDuplicated(request.companyId, request.name)

        val snapshot = createRestaurant(request)
        val restaurantId = insertRestaurant(snapshot)

        applicationEventPublisher.publishEvent(CreateScheduleEvent(restaurantId))
        return restaurantId != null
    }
}
