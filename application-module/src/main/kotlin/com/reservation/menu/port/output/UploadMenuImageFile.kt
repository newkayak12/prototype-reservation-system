package com.reservation.menu.port.output

import org.springframework.web.multipart.MultipartFile

interface UploadMenuImageFile {
    fun execute(files: List<MultipartFile>): List<String>
}
