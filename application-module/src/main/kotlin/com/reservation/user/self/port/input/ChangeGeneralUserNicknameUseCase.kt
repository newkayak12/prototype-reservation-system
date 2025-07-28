package com.reservation.user.self.port.input

import com.reservation.user.self.port.input.command.request.ChangeGeneralUserNicknameCommand

/**
 * 닉네임 변경을 요청합니다.
 * 닉네임 변경에 성공하면 `true` 실패하면 `false`를 반환합니다.
 */
interface ChangeGeneralUserNicknameUseCase {
    fun execute(command: ChangeGeneralUserNicknameCommand): Boolean
}
