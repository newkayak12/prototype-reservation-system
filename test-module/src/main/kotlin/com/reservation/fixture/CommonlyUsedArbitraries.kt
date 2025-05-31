package com.reservation.fixture

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import java.text.DecimalFormat

@SuppressWarnings("MagicNumber")
object CommonlyUsedArbitraries {
    val loginIdArbitrary: Arbitrary<String> =
        Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .ofMinLength(4)
            .ofMaxLength(20)

    val emailArbitrary: Arbitrary<String> =
        Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(5)
            .ofMaxLength(10)
            .map { "$it@example.com" }

    val passwordArbitrary: Arbitrary<String> =
        Arbitraries.integers().between(4, 14) // 나머지 길이 조절
            .flatMap { restLength ->
                Combinators.combine(
                    Arbitraries.strings().withCharRange('A', 'Z').ofMinLength(1).ofMaxLength(1),
                    Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(1),
                    Arbitraries.strings().withCharRange('0', '9').ofMinLength(1).ofMaxLength(1),
                    Arbitraries.of(listOf('!', '@', '#', '$', '%', '^', '&', '*', '(', ')')),
                    Arbitraries.strings()
                        .withCharRange('A', 'Z')
                        .withCharRange('a', 'z')
                        .withCharRange('0', '9')
                        .ofMinLength(restLength)
                        .ofMaxLength(restLength),
                ).`as` { upper, lower, digit, special, rest ->
                    (upper + lower + digit + special + rest).toList().shuffled().joinToString("")
                }
            }

    private val decimalFormat = DecimalFormat("00000000")
    val phoneNumberArbitrary: Arbitrary<String> =
        Arbitraries.longs().between(10000000L, 99999999L)
            .map { decimalFormat.format(it) }
            .map { num -> "010-${num.toString().substring(0, 4)}-${num.toString().substring(4)}" }

    val nicknameArbitrary: Arbitrary<String> =
        Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .ofMinLength(5)
            .ofMaxLength(12)
}
