package com.reservation.user.policy

import com.reservation.user.widthdrawal.WithdrawalUser

interface Withdrawable {

    fun withdraw(): WithdrawalUser
}
