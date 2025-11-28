package com.reservation.aspects.featureflag.configurations

import org.springframework.retry.RetryContext

class TestSerializableRetryContext(
    private var retryCount: Int = 0,
    private var lastExceptionType: String? = null,
    private var lastExceptionMessage: String? = null,
    private var startTime: Long = System.currentTimeMillis(),
    private var exhausted: Boolean = false,
    private var attribute: MutableMap<String, Any> = mutableMapOf(),
) : RetryContext {
    override fun getRetryCount() = retryCount

    override fun getLastThrowable(): Throwable? {
        print("getLastThrowable")
        return try {
            val clazz = Class.forName(lastExceptionType!!).asSubclass(Throwable::class.java)
            val constructor = clazz.getConstructor(String::class.java)
            constructor.newInstance(lastExceptionMessage)
        } catch (_: Exception) {
            RuntimeException("Failed to restore $lastExceptionType: $lastExceptionMessage")
        }
    }

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

    override fun isExhaustedOnly(): Boolean = exhausted

    override fun setExhaustedOnly() {
        exhausted = true
    }

    override fun getParent(): RetryContext? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TestSerializableRetryContext

        if (lastExceptionType != other.lastExceptionType) return false
        if (lastExceptionMessage != other.lastExceptionMessage) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lastExceptionType?.hashCode() ?: 0
        result = 31 * result + (lastExceptionMessage?.hashCode() ?: 0)
        return result
    }
}
