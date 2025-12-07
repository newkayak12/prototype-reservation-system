package com.reservation.persistence.converter

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer

@Converter
class GenericJson2Converter : AttributeConverter<Any, String> {
    companion object {
        private val serializer = GenericJackson2JsonRedisSerializer()
    }

    override fun convertToDatabaseColumn(attribute: Any?): String? {
        return attribute?.let {
            String(serializer.serialize(it)!!)
        }
    }

    override fun convertToEntityAttribute(dbData: String?): Any? {
        return dbData?.let {
            serializer.deserialize(it.toByteArray())
        }
    }
}
