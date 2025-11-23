package com.reservation.enumeration

enum class TableStatus {
    EMPTY,
    OCCUPIED,
    ;

    fun isOccupied(): Boolean = this == EMPTY
}
