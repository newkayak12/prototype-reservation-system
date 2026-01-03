package com.reservation.config.aspect

import com.reservation.common.exceptions.AccessNotPermittedException
import com.reservation.config.annotations.FeatureFlag
import com.reservation.featureflag.port.input.FindFeatureFlagUseCase
import com.reservation.featureflag.port.input.query.request.FindFeatureFlagQuery
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import java.lang.reflect.Method

@Aspect
@Component
class FeatureFlagAspect(
    private val findFeatureFlagUseCase: FindFeatureFlagUseCase,
) : ApplicationContextAware {
    private lateinit var applicationContext: ApplicationContext

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

    private fun getClass(
        joinPoint: ProceedingJoinPoint,
        annotation: FeatureFlag,
    ): Class<out Any> {
        val className = annotation.targetClass

        if (StringUtils.hasText(className)) {
            val bean = applicationContext.getBean(annotation.targetClass)
            return bean.javaClass
        }

        return joinPoint.target::class.java
    }

    private fun getFallbackMethod(
        targetClass: Class<out Any>,
        joinPoint: ProceedingJoinPoint,
        annotation: FeatureFlag,
    ): Method =
        targetClass.getDeclaredMethod(
            annotation.fallback,
            *joinPoint.args.map { it::class.java }.toTypedArray(),
        )

    private fun failOver(
        joinPoint: ProceedingJoinPoint,
        annotation: FeatureFlag,
    ): Any? {
        if (annotation.fallback.isNullOrBlank()) throw AccessNotPermittedException()

        try {
            val targetClass = getClass(joinPoint, annotation)
            val fallbackMethod = getFallbackMethod(targetClass, joinPoint, annotation)

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

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.applicationContext = applicationContext
    }
}
