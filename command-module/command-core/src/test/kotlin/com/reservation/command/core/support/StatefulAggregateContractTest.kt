package com.reservation.command.core.support

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

private class Touch(val by: String) : Command

private class ProbeAggregate(override val id: String) : StatefulAggregate<String>() {
    var touchedBy: String? = null
        private set

    override fun handle(command: Command) {
        when (command) {
            is Touch -> touchedBy = command.by
            else -> error("알 수 없는 커맨드: $command")
        }
    }
}

class StatefulAggregateContractTest : BehaviorSpec({

    given("StatefulAggregate를 구현한 최소 애그리거트가 주어졌을 때") {
        `when`("커맨드를 handle하면") {
            val aggregate = ProbeAggregate(id = "probe-1")
            aggregate.handle(Touch(by = "tester"))

            then("id 프로퍼티와 handle의 부수효과가 모두 관찰 가능하다") {
                aggregate.id shouldBe "probe-1"
                aggregate.touchedBy shouldBe "tester"
            }
        }
    }
})
