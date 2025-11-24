package com.reservation.config.aspect

import com.reservation.common.exceptions.AccessNotPermittedException
import com.reservation.config.annotations.FeatureFlag
import com.reservation.featureflag.port.input.FindFeatureFlagUseCase
import com.reservation.featureflag.port.input.query.request.FindFeatureFlagQuery
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Aspect
@Component
class FeatureFlagAspect(
    private val findFeatureFlagUseCase: FindFeatureFlagUseCase,
) {
    private fun verifyFeatureFlag(annotation: FeatureFlag): Boolean {
        val featureFlagType = annotation.featureFlagType
        val featureFlagKey = annotation.featureFlagKey
        val findFeatureFlagQuery =
            FindFeatureFlagQuery(
                featureFlagType,
                featureFlagKey,
            )

        val result = findFeatureFlagUseCase.execute(findFeatureFlagQuery)
        val isEnabled = result.isEnabled

        return isEnabled
    }

    private fun failOver(
        joinPoint: ProceedingJoinPoint,
        annotation: FeatureFlag,
    ): Any? {
        if (annotation.fallback.isNullOrBlank()) throw AccessNotPermittedException()

        try {
            val targetClass = joinPoint.target::class.java
            val fallbackMethod =
                targetClass.getDeclaredMethod(
                    annotation.fallback,
                    *joinPoint.args.map { it::class.java }.toTypedArray(),
                )
            return fallbackMethod.invoke(joinPoint.target, *joinPoint.args)
        } catch (_: NoSuchMethodException) {
            throw AccessNotPermittedException()
        }
    }

    @Around("@annotation(featureFlag)")
    fun executeFeatureFlagAction(
        proceedingJoinPoint: ProceedingJoinPoint,
        featureFlag: FeatureFlag,
    ): Any? {
        val result = verifyFeatureFlag(featureFlag)
        return if (result) {
            proceedingJoinPoint.proceed()
        } else {
            failOver(proceedingJoinPoint, featureFlag)
        }
    }
}
