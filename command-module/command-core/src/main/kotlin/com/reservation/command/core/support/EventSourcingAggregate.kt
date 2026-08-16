package com.reservation.command.core.support

abstract class EventSourcingAggregate<S : EventSourcingAggregate<S>> {
    abstract fun handle(command: Command): List<DomainEvent>

    abstract fun apply(event: DomainEvent): S
}
