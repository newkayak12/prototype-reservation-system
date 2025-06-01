package com.reservation.user.policy.formats

data class CreateGeneralUserForm(
    private val loginId: String,
    private val password: String,
    private val email: String,
    private val mobile: String,
    private val nickname: String,
) : CreateUserFormats {
    override fun loginId(): String = loginId

    override fun password(): String = password

    override fun email(): String = email

    override fun mobile(): String = mobile

    override fun nickname(): String = nickname
}
