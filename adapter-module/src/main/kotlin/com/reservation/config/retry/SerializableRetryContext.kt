package com.reservation.config.retry

import org.springframework.retry.RetryContext

data class SerializableRetryContext(
    private var retryCount: Int = 0,
    private var lastExceptionType: String? = null,
    private var lastExceptionMessage: String? = null,
    private var startTime: Long = System.currentTimeMillis(),
    private var exhausted: Boolean = false,
    private var exhaustedOnly: Boolean = false,
    private var attribute: MutableMap<String, Any> = mutableMapOf(),
) : RetryContext {
    override fun getRetryCount() = retryCount

    override fun getLastThrowable(): Throwable? = null

    @Suppress("EmptyFunctionBlock")
    override fun setAttribute(
        name: String,
        value: Any?,
    ) {
        if (value == null) return
        attribute[name] = value
    }

    override fun getAttribute(name: String): Any? = attribute[name]

    override fun removeAttribute(name: String): Any? = attribute.remove(name)

    override fun hasAttribute(name: String) = attribute.containsKey(name)

    override fun attributeNames(): Array<String> = attribute.keys.toTypedArray()

    override fun isExhaustedOnly(): Boolean = exhaustedOnly

    override fun setExhaustedOnly() {
        exhausted = true
    }

    override fun getParent(): RetryContext? = null
}
