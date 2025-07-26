package com.reservation.restaurant.usecase

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.annotations.UseCase
import com.reservation.restaurant.Restaurant
import com.reservation.restaurant.policy.format.ChangeRestaurantForm
import com.reservation.restaurant.policy.format.RestaurantWorkingDayForm
import com.reservation.restaurant.port.input.UpdateRestaurantCommand
import com.reservation.restaurant.port.input.UpdateRestaurantCommand.ChangeRestaurantCommandDto
import com.reservation.restaurant.port.output.ChangeRestaurant
import com.reservation.restaurant.port.output.ChangeRestaurant.ChangeCuisinesInquiry
import com.reservation.restaurant.port.output.ChangeRestaurant.ChangeNationalitiesInquiry
import com.reservation.restaurant.port.output.ChangeRestaurant.ChangePhotosInquiry
import com.reservation.restaurant.port.output.ChangeRestaurant.ChangeRestaurantInquiry
import com.reservation.restaurant.port.output.ChangeRestaurant.ChangeTagInquiry
import com.reservation.restaurant.port.output.ChangeRestaurant.ChangeWorkingDayInquiry
import com.reservation.restaurant.port.output.LoadRestaurant
import com.reservation.restaurant.port.output.UploadRestaurantImageFile
import com.reservation.restaurant.service.ChangeRestaurantService
import com.reservation.restaurant.snapshot.RestaurantSnapshot
import jakarta.transaction.Transactional
import org.springframework.web.multipart.MultipartFile

@UseCase
class ChangeRestaurantUseCase(
    private val changeRestaurantService: ChangeRestaurantService,
    private val loadRestaurant: LoadRestaurant,
    private val changeRestaurant: ChangeRestaurant,
    private val uploadRestaurantImageFile: UploadRestaurantImageFile,
) : UpdateRestaurantCommand {
    @Transactional
    override fun execute(request: ChangeRestaurantCommandDto): Boolean {
        val restaurant: Restaurant = load(request.id)
        val photoUrls = uploadPhotos(request.photos)

        val form = createForm(request, photoUrls)
        val changedResult = changeRestaurantService.change(restaurant, form)

        return updateRestaurant(changedResult)
    }

    private fun load(id: String) =
        loadRestaurant.query(id)
            ?.toDomain()
            ?: throw NoSuchPersistedElementException()

    private fun uploadPhotos(photos: List<MultipartFile>) =
        if (photos.isEmpty()) listOf() else uploadRestaurantImageFile.execute(photos)

    private fun createForm(
        request: ChangeRestaurantCommandDto,
        photoUrls: List<String>,
    ) = ChangeRestaurantForm(
        id = request.id,
        name = request.name,
        introduce = request.introduce,
        phone = request.phone,
        zipCode = request.zipCode,
        address = request.address,
        detail = request.detail,
        latitude = request.latitude,
        longitude = request.longitude,
        workingDays =
            request.workingDays.map {
                RestaurantWorkingDayForm(it.day, it.startTime, it.endTime)
            },
        photos = photoUrls,
        tags = request.tags,
        nationalities = request.nationalities,
        cuisines = request.cuisines,
    )

    private fun updateRestaurant(result: RestaurantSnapshot): Boolean {
        val inquiry =
            ChangeRestaurantInquiry(
                id = result.id!!,
                name = result.name,
                introduce = result.introduce,
                phone = result.phone,
                zipCode = result.zipCode,
                address = result.address,
                detail = result.detail,
                latitude = result.latitude,
                longitude = result.longitude,
                workingDay =
                    result.workingDays.map {
                        ChangeWorkingDayInquiry(it.day, it.startTime, it.endTime)
                    },
                photos = result.photos.map { ChangePhotosInquiry(it.url) },
                tag = result.tags.map { ChangeTagInquiry(it) },
                nationalities = result.tags.map { ChangeNationalitiesInquiry(it) },
                cuisines = result.cuisines.map { ChangeCuisinesInquiry(it) },
            )

        return changeRestaurant.command(inquiry)
    }
}
