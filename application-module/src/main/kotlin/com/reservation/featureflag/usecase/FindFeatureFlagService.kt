package com.reservation.featureflag.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.featureflag.port.input.FindFeatureFlagUseCase
import com.reservation.featureflag.port.input.query.request.FindFeatureFlagQuery
import com.reservation.featureflag.port.input.query.response.FindFeatureFlagQueryResult
import com.reservation.featureflag.port.output.FindFeatureFlag
import com.reservation.featureflag.port.output.FindFeatureFlag.FindFeatureFlagInquiry
import com.reservation.featureflag.port.output.FindFeatureFlag.FindFeatureFlagResult
import com.reservation.featureflag.port.output.SaveFeatureFlag
import com.reservation.featureflag.port.output.SaveFeatureFlag.SaveFeatureFlagInquiry
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindFeatureFlagService(
    private val fetchFeatureFlagTemplate: FindFeatureFlag,
    private val findFeatureFlagRepository: FindFeatureFlag,
    private val saveFeatureFlagTemplate: SaveFeatureFlag,
) : FindFeatureFlagUseCase {
    companion object {
        private const val FAIL_OVER_ID = -1L
        private const val FAIL_OVER_IS_ENABLED = false
    }

    @Retryable(
        retryFor = [Exception::class],
        maxAttempts = 5,
        backoff = Backoff(delay = 100, multiplier = 2.0, maxDelay = 500),
        label = "feature-flag-redis-retry",
        listeners = ["listenRetryReason"],
        stateful = true,
    )
    override fun execute(request: FindFeatureFlagQuery): FindFeatureFlagQueryResult =
        fetchFromRedis(request)

    @Recover
    @Transactional(readOnly = true)
    fun executeWithDatabase(
        @Suppress("UNUSED_PARAMETER") exception: Exception,
        request: FindFeatureFlagQuery,
    ): FindFeatureFlagQueryResult = fetchFromDatabase(request)

    private fun fetchFromRedis(request: FindFeatureFlagQuery): FindFeatureFlagQueryResult {
        val inquiry = request.toInquiry()

        return fetchFeatureFlagTemplate.query(inquiry)
            ?.let { it.toQuery() }
            ?: fetchFromDatabaseAndSaveAtRedis(request)
    }

    private fun fetchFromDatabaseAndSaveAtRedis(
        request: FindFeatureFlagQuery,
    ): FindFeatureFlagQueryResult {
        val fetchFromDB = fetchFromDatabase(request)

        saveFeatureFlagTemplate.command(
            SaveFeatureFlagInquiry(
                id = fetchFromDB.id,
                featureFlagType = fetchFromDB.featureFlagType,
                featureFlagKey = fetchFromDB.featureFlagKey,
                isEnabled = fetchFromDB.isEnabled,
            ),
        )

        return fetchFromDB
    }

    private fun fetchFromDatabase(request: FindFeatureFlagQuery): FindFeatureFlagQueryResult {
        val inquiry = request.toInquiry()
        val failOver = request.failOver()

        return findFeatureFlagRepository.query(inquiry)
            ?.let { it.toQuery() }
            ?: failOver
    }

    private fun FindFeatureFlagQuery.toInquiry() =
        FindFeatureFlagInquiry(
            featureFlagType = this.featureFlagType,
            featureFlagKey = this.featureFlagKey,
        )

    private fun FindFeatureFlagQuery.failOver() =
        FindFeatureFlagQueryResult(
            id = FAIL_OVER_ID,
            featureFlagType = this.featureFlagType,
            featureFlagKey = this.featureFlagKey,
            isEnabled = FAIL_OVER_IS_ENABLED,
        )

    private fun FindFeatureFlagResult.toQuery() =
        FindFeatureFlagQueryResult(
            id = this.id,
            featureFlagType = this.featureFlagType,
            featureFlagKey = this.featureFlagKey,
            isEnabled = this.isEnabled,
        )
}
