package com.reservation.infrastructure.upload.file

import com.reservation.menu.port.output.UploadMenuImageFile
import com.reservation.restaurant.port.output.UploadRestaurantImageFile
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

@Suppress("TODO_USAGE")
@Component
class UploadImageFile : UploadRestaurantImageFile, UploadMenuImageFile {
    override fun execute(files: List<MultipartFile>): List<String> {
        TODO("Implementation is not planned")
    }
}
