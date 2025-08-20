package com.reservation.menu.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.menu.policy.format.CreateMenuForm
import com.reservation.menu.port.input.CreateMenuUseCase
import com.reservation.menu.port.input.request.CreateMenuCommand
import com.reservation.menu.port.output.CreateMenu
import com.reservation.menu.port.output.CreateMenu.CreateMenuInquiry
import com.reservation.menu.port.output.UploadMenuImageFile
import com.reservation.menu.service.CreateMenuDomainService
import com.reservation.menu.snapshot.MenuSnapshot
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@UseCase
class CreateMenuService(
    private val createMenuDomainService: CreateMenuDomainService,
    private val uploadMenuImageFile: UploadMenuImageFile,
    private val createMenu: CreateMenu,
) : CreateMenuUseCase {
    @Transactional
    override fun execute(command: CreateMenuCommand): Boolean {
        val urls = uploadFiles(command.photoUrl)
        val form = transform(command, urls)
        val snapshot = createMenu(form)

        return insertMenu(snapshot)
    }

    private fun transform(
        command: CreateMenuCommand,
        url: List<String>,
    ): CreateMenuForm =
        CreateMenuForm(
            restaurantId = command.restaurantId,
            title = command.title,
            description = command.description,
            price = command.price,
            isRepresentative = command.isRepresentative,
            isRecommended = command.isRecommended,
            isVisible = command.isVisible,
            photoUrl = url,
        )

    private fun uploadFiles(files: List<MultipartFile>): List<String> =
        if (files.isEmpty()) listOf() else uploadMenuImageFile.execute(files)

    private fun createMenu(form: CreateMenuForm): MenuSnapshot =
        createMenuDomainService.createMenu(form)

    private fun insertMenu(snapshot: MenuSnapshot): Boolean {
        val inquiry =
            CreateMenuInquiry(
                id = snapshot.id,
                restaurantId = snapshot.restaurantId,
                title = snapshot.title,
                description = snapshot.description,
                price = snapshot.price,
                isRepresentative = snapshot.isRepresentative,
                isRecommended = snapshot.isRecommended,
                isVisible = snapshot.isVisible,
                photoUrl = snapshot.photoUrl,
            )

        return createMenu.command(inquiry)
    }
}
