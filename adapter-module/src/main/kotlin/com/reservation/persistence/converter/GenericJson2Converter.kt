package com.reservation.persistence.converter

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.reservation.event.abstractEvent.AbstractEvent
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class GenericJson2Converter<T : AbstractEvent> : AttributeConverter<T, String> {
    companion object {
        val mapper: ObjectMapper =
            ObjectMapper()
                .registerKotlinModule()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(
                    LaissezFaireSubTypeValidator.instance,
                    ObjectMapper.DefaultTyping.NON_FINAL,
                    JsonTypeInfo.As.PROPERTY,
                )
    }

    override fun convertToDatabaseColumn(attribute: T?): String? {
        return attribute?.let { mapper.writeValueAsString(attribute) }
    }

    override fun convertToEntityAttribute(dbData: String?): T? {
        return dbData?.let { mapper.readValue(it, AbstractEvent::class.java) as T }
    }
}
