package com.reservation.featureflag.configurations

import com.reservation.utilities.logger.loggerFactory
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener

class TestListenRetryReason : RetryListener {
    private val log = loggerFactory<TestListenRetryReason>()

    override fun <T : Any?, E : Throwable?> onError(
        context: RetryContext?,
        callback: RetryCallback<T, E>?,
        throwable: Throwable?,
    ) {
        super.onError(context, callback, throwable)
        log.error(
            """
            RETRY====
            context: {}
            =========

            """.trimIndent(),
            context,
            callback,
            throwable,
        )
    }
}
