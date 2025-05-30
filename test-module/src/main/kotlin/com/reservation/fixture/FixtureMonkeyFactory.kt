package com.reservation.fixture

import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.FixtureMonkeyBuilder
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector
import com.navercorp.fixturemonkey.api.introspector.FailoverIntrospector
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin

object FixtureMonkeyFactory {
    private val monkey: FixtureMonkeyBuilder =
        FixtureMonkey
            .builder()
            .defaultNotNull(true)
            .enableLoggingFail(false)
            .objectIntrospector(
                FailoverIntrospector(
                    listOf(
                        FieldReflectionArbitraryIntrospector.INSTANCE,
                        ConstructorPropertiesArbitraryIntrospector.INSTANCE,
                    ),
                ),
            )
            .plugin(KotlinPlugin())

    fun giveMePureMonkey(): FixtureMonkeyBuilder = monkey

    fun giveMeJakartaMonkey(): FixtureMonkeyBuilder = monkey.plugin(JakartaValidationPlugin())
}
