package com.reservation.restaurant.port.output

import org.springframework.web.multipart.MultipartFile

interface UploadRestaurantImageFile {
    fun execute(files: List<MultipartFile>): List<String>
}
