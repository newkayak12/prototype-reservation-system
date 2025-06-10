package com.reservation.user.self.usecase

import com.reservation.common.exceptions.AlreadyPersistedException
import com.reservation.config.annotations.UseCase
import com.reservation.exceptions.InvalidSituationException
import com.reservation.user.exceptions.NoSuchDatabaseElementException
import com.reservation.user.self.port.input.ChangeGeneralUserNicknameCommand
import com.reservation.user.self.port.input.ChangeGeneralUserNicknameCommand.ChangeGeneralUserNicknameCommandDto
import com.reservation.user.self.port.output.ChangeGeneralUserNickname
import com.reservation.user.self.port.output.ChangeGeneralUserNickname.ChangeGeneralUserNicknameDto
import com.reservation.user.self.port.output.CheckGeneralUserNicknameDuplicated
import com.reservation.user.self.port.output.CheckGeneralUserNicknameDuplicated.CheckGeneralUserNicknameDuplicatedInquiry
import com.reservation.user.self.port.output.LoadGeneralUser
import com.reservation.user.service.ChangeUserNicknameService
import org.springframework.transaction.annotation.Transactional

@UseCase
class ChangeGeneralUserNicknameUseCase(
    private val changeGeneralUserNickname: ChangeGeneralUserNickname,
    private val checkGeneralUserNicknameDuplicated: CheckGeneralUserNicknameDuplicated,
    private val changeUserNicknameService: ChangeUserNicknameService,
    private val loadGeneralUser: LoadGeneralUser,
) : ChangeGeneralUserNicknameCommand {
    @Transactional
    override fun execute(command: ChangeGeneralUserNicknameCommandDto): Boolean {
        checkGeneralUserNicknameDuplicated.isDuplicated(
            CheckGeneralUserNicknameDuplicatedInquiry(command.nickname, command.role),
        ).run {
            if (this) {
                throw AlreadyPersistedException()
            }
        }

        val user =
            loadGeneralUser.load(command.id)
                ?.let {
                    changeUserNicknameService.changePersonalAttributes(
                        it.toDomain(),
                        command.nickname,
                    )
                }
                ?: run { throw NoSuchDatabaseElementException() }

        return changeGeneralUserNickname.changeGeneralUserNickname(
            ChangeGeneralUserNicknameDto(
                user.identifier ?: run { throw InvalidSituationException() },
                user.userNickname,
            ),
        )
    }
}
