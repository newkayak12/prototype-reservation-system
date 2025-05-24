package com.reservation.user.policy

import com.reservation.user.widthdrawal.WithdrawalUser

interface UserWithdrawable {
    fun withdraw(): WithdrawalUser
}
