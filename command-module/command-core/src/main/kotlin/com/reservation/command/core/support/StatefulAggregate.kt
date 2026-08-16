package com.reservation.command.core.support

abstract class StatefulAggregate<ID : Any> {
    abstract val id: ID

    abstract fun handle(command: Command)
}
