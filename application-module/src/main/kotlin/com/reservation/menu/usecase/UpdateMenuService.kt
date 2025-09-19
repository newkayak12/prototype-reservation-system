package com.reservation.menu.usecase

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.annotations.UseCase
import com.reservation.menu.Menu
import com.reservation.menu.policy.format.UpdateMenuForm
import com.reservation.menu.port.input.UpdateMenuUseCase
import com.reservation.menu.port.input.request.UpdateMenuCommand
import com.reservation.menu.port.output.LoadMenuById
import com.reservation.menu.port.output.UpdateMenu
import com.reservation.menu.port.output.UpdateMenu.UpdateMenuInquiry
import com.reservation.menu.port.output.UploadMenuImageFile
import com.reservation.menu.service.ChangeMenuDomainService
import com.reservation.menu.snapshot.MenuSnapshot
import com.reservation.menu.vo.MenuAttributes
import com.reservation.menu.vo.MenuDescription
import com.reservation.menu.vo.MenuPhoto
import com.reservation.menu.vo.MenuPhotoBook
import com.reservation.menu.vo.MenuPrice
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@UseCase
class UpdateMenuService(
    private val loadMenuById: LoadMenuById,
    private val changeMenuDomainService: ChangeMenuDomainService,
    private val uploadMenuImageFile: UploadMenuImageFile,
    private val updateMenu: UpdateMenu,
) : UpdateMenuUseCase {
    private fun load(id: String): Menu {
        val loadMenu = loadMenuById.loadById(id) ?: throw NoSuchPersistedElementException()
        return Menu(
            loadMenu.id,
            restaurantId = loadMenu.restaurantId,
            information = MenuDescription(loadMenu.title, loadMenu.description),
            menuPhotoBook =
                MenuPhotoBook(
                    loadMenu.photos.map { MenuPhoto(it.url) }.toMutableList(),
                ),
            attributes =
                MenuAttributes(
                    loadMenu.isRepresentative,
                    loadMenu.isRecommended,
                    loadMenu.isVisible,
                ),
            price = MenuPrice(loadMenu.price),
        )
    }

    private fun uploadImage(files: List<MultipartFile>): List<String> =
        uploadMenuImageFile.execute(files)

    private fun createForm(
        command: UpdateMenuCommand,
        newPhotos: List<String>,
    ): UpdateMenuForm =
        UpdateMenuForm(
            id = command.id,
            restaurantId = command.restaurantId,
            title = command.title,
            description = command.description,
            photoUrl = command.photoUrl.map { it.url } + newPhotos,
            isRepresentative = command.isRepresentative,
            isRecommended = command.isRecommended,
            isVisible = command.isVisible,
            price = command.price,
        )

    private fun updateMenu(snapshot: MenuSnapshot): Boolean =
        updateMenu.command(
            UpdateMenuInquiry(
                id = snapshot.id!!,
                restaurantId = snapshot.restaurantId,
                title = snapshot.title,
                description = snapshot.description,
                price = snapshot.price,
                isRepresentative = snapshot.isRepresentative,
                isRecommended = snapshot.isRecommended,
                isVisible = snapshot.isVisible,
                photoUrl = snapshot.photoUrl,
            ),
        )

    @Transactional
    override fun execute(command: UpdateMenuCommand): Boolean {
        val menu = load(command.id)
        val newImages = uploadImage(command.photos)
        val form = createForm(command, newImages)

        val snapshot = changeMenuDomainService.updateMenu(menu, form)
        updateMenu(snapshot)

        return true
    }
}
