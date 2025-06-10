package com.reservation.utilities.mosaic

object MaskingUtility {
    private const val START_INDEX = 0
    private const val SIXTY_PERCENT = 0.6
    private const val HALF_DIVISOR = 2
    private const val MASKING_CHARACTER = "*"
    private const val LOGIN_ID_MINIMUM_LENGTH = 8

    fun manipulate(target: String): String {
        assert(target.length >= LOGIN_ID_MINIMUM_LENGTH) {
            "invalid login id"
        }
        val length = target.length
        val sixtyPercentLength: Int = length.times(SIXTY_PERCENT).toInt()
        val start: Int = length.div(HALF_DIVISOR) - sixtyPercentLength.div(HALF_DIVISOR)
        val end: Int = start + sixtyPercentLength

        val firstChunk = target.slice(START_INDEX..<start)
        val middleChunk = MASKING_CHARACTER.repeat(sixtyPercentLength)
        val lastChunk = target.slice(end..target.lastIndex)

        return "$firstChunk$middleChunk$lastChunk"
    }
}
