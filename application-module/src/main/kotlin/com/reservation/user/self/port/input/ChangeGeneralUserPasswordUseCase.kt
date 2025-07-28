package com.reservation.user.self.port.input

import com.reservation.user.self.port.input.command.request.ChangeGeneralUserPasswordCommand

/**
 * 일반 사용자의 비밀번호 변경을 요청합니다.
 * 비밀번호 변경에 성공하면 `true` 실패하면 `false`를 반환합니다.
 */
interface ChangeGeneralUserPasswordUseCase {
    fun execute(command: ChangeGeneralUserPasswordCommand): Boolean
}
