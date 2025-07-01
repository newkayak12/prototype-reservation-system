package com.reservation.user.self.usecase

import com.reservation.common.exceptions.AlreadyPersistedException
import com.reservation.common.exceptions.NoSuchDatabaseElementException
import com.reservation.config.annotations.UseCase
import com.reservation.exceptions.InvalidSituationException
import com.reservation.user.history.change.port.input.CreateGeneralUserChangeHistoryCommand
import com.reservation.user.history.change.port.input.CreateGeneralUserChangeHistoryCommand.CreateGeneralUserChangeHistoryCommandDto
import com.reservation.user.self.User
import com.reservation.user.self.port.input.ChangeGeneralUserNicknameCommand
import com.reservation.user.self.port.input.ChangeGeneralUserNicknameCommand.ChangeGeneralUserNicknameCommandDto
import com.reservation.user.self.port.output.ChangeGeneralUserNickname
import com.reservation.user.self.port.output.ChangeGeneralUserNickname.ChangeGeneralUserNicknameDto
import com.reservation.user.self.port.output.CheckGeneralUserNicknameDuplicated
import com.reservation.user.self.port.output.CheckGeneralUserNicknameDuplicated.CheckGeneralUserNicknameDuplicatedInquiry
import com.reservation.user.self.port.output.LoadGeneralUser
import com.reservation.user.self.port.output.LoadGeneralUser.LoadGeneralUserResult
import com.reservation.user.service.ChangeUserNicknameService
import org.springframework.transaction.annotation.Transactional

@UseCase
class ChangeGeneralUserNicknameUseCase(
    private val changeGeneralUserNickname: ChangeGeneralUserNickname,
    private val checkGeneralUserNicknameDuplicated: CheckGeneralUserNicknameDuplicated,
    private val changeUserNicknameService: ChangeUserNicknameService,
    private val loadGeneralUser: LoadGeneralUser,
    private val createGeneralUserChangeHistoryCommand: CreateGeneralUserChangeHistoryCommand,
) : ChangeGeneralUserNicknameCommand {
    private fun changePersonalAttribute(
        loadUserResult: LoadGeneralUserResult,
        nickname: String,
    ): User {
        return changeUserNicknameService.changePersonalAttributes(
            loadUserResult.toDomain(),
            nickname,
        )
    }

    private fun writeChangeHistory(loadUserResult: LoadGeneralUserResult) {
        createGeneralUserChangeHistoryCommand.execute(
            CreateGeneralUserChangeHistoryCommandDto(
                loadUserResult.id,
                loadUserResult.loginId,
                loadUserResult.email,
                loadUserResult.nickname,
                loadUserResult.mobile,
            ),
        )
    }

    /***
     * 닉네임에 대한 중복 체크를 진행한 후 사용자를 찾아 현재 상태를 저장하고 닉네임 변경을 진행합니다.
     */
    @Transactional
    override fun execute(command: ChangeGeneralUserNicknameCommandDto): Boolean {
        checkGeneralUserNicknameDuplicated.query(
            CheckGeneralUserNicknameDuplicatedInquiry(command.nickname, command.role),
        ).run {
            if (this) {
                throw AlreadyPersistedException()
            }
        }

        val user =
            loadGeneralUser.load(command.id)
                ?.let {
                    writeChangeHistory(it)
                    return@let changePersonalAttribute(it, command.nickname)
                }
                ?: run { throw NoSuchDatabaseElementException() }

        return changeGeneralUserNickname.command(
            ChangeGeneralUserNicknameDto(
                user.identifier ?: run { throw InvalidSituationException() },
                user.userNickname,
            ),
        )
    }
}
