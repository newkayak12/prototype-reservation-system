package com.reservation.config.retry

import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener
import org.springframework.stereotype.Component

@Component
class ListenRetryReason : RetryListener {
    override fun <T : Any?, E : Throwable?> onError(
        context: RetryContext?,
        callback: RetryCallback<T, E>?,
        throwable: Throwable?,
    ) {
        super.onError(context, callback, throwable)
    }
}
