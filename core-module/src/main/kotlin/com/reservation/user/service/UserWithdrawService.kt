package com.reservation.user.service

import com.reservation.user.policy.UserWithdrawable
import com.reservation.user.widthdrawal.WithdrawalUser

class UserWithdrawService {

    fun <T: UserWithdrawable> withdraw(target: T ): WithdrawalUser = target.withdraw()
}
