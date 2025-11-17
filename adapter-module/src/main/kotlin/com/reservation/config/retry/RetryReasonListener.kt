package com.reservation.config.retry

import com.reservation.utilities.logger.loggerFactory
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener

class RetryReasonListener : RetryListener {
    private val log = loggerFactory<RetryReasonListener>()

    override fun <T : Any?, E : Throwable?> onError(
        context: RetryContext?,
        callback: RetryCallback<T, E>?,
        throwable: Throwable?,
    ) {
        super.onError(context, callback, throwable)

        log.error(
            """
            Retry===
            context : {}
            ========
            """.trimIndent(),
            context,
        )
    }
}
