package com.reservation.config.retry

import org.springframework.retry.RetryContext

data class SerializableRetryContext(
    private var retryCount: Int = 0,
    private var lastExceptionType: String? = null,
    private var lastExceptionMessage: String? = null,
    private var startTime: Long = System.currentTimeMillis(),
    private var exhausted: Boolean = false,
    private var exhaustedOnly: Boolean = false,
) : RetryContext {
    override fun getRetryCount() = retryCount

    override fun getLastThrowable(): Throwable? = null

    @Suppress("EmptyFunctionBlock")
    override fun setAttribute(
        name: String,
        value: Any?,
    ) {}

    override fun getAttribute(name: String): Any? = null

    override fun removeAttribute(name: String): Any? = null

    override fun hasAttribute(name: String) = false

    override fun attributeNames(): Array<String> = emptyArray()

    override fun isExhaustedOnly(): Boolean = exhaustedOnly

    override fun setExhaustedOnly() {
        exhausted = true
    }

    override fun getParent(): RetryContext? = null
}
