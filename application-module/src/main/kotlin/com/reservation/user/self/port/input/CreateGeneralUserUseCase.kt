package com.reservation.user.self.port.input

import com.reservation.user.self.port.input.command.request.CreateGeneralUserCommand

/**
 * 일반 사용자에 대한 생성을 요구합니다.
 * 일반 사용자 가입에 성공하면 `true`, 실패하면 `false`를 반환합니다.
 */
interface CreateGeneralUserUseCase {
    fun execute(command: CreateGeneralUserCommand): Boolean
}
