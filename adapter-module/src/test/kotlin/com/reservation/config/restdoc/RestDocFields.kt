package com.reservation.config.restdoc

import org.springframework.restdocs.headers.HeaderDescriptor
import org.springframework.restdocs.headers.HeaderDocumentation
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.ParameterDescriptor
import org.springframework.restdocs.request.RequestDocumentation
import org.springframework.restdocs.request.RequestPartDescriptor

data class Body(
    val name: String,
    val jsonType: JsonFieldType?,
    val optional: Boolean = false,
    val description: String = "",
) {
    fun parse(): FieldDescriptor =
        fieldWithPath(name)
            .also {
                if (optional) it.optional()
                if (jsonType != null) it.type(jsonType)
                if (description.isNotEmpty()) it.description(description)
            }
}

data class Header(
    val name: String,
    val optional: Boolean = false,
    val description: String = "",
) {
    fun parse(): HeaderDescriptor =
        HeaderDocumentation.headerWithName(name)
            .also {
                if (optional) it.optional()
                if (description.isNotEmpty()) it.description(description)
            }
}

data class Query(
    val name: String,
    val optional: Boolean = false,
    val description: String = "",
) {
    fun parse(): ParameterDescriptor =
        RequestDocumentation.parameterWithName(name)
            .also {
                if (optional) it.optional()
                if (description.isNotEmpty()) it.description(description)
            }
}

data class PathParameter(
    val name: String,
    val optional: Boolean = false,
    val description: String = "",
) {
    fun parse(): ParameterDescriptor =
        RequestDocumentation.parameterWithName(name)
            .also {
                if (optional) it.optional()
                if (description.isNotEmpty()) it.description(description)
            }
}

data class Part(
    val name: String,
    val optional: Boolean = false,
    val description: String = "",
) {
    fun parse(): RequestPartDescriptor =
        RequestDocumentation.partWithName(name)
            .also {
                if (optional) it.optional()
                if (description.isNotEmpty()) it.description(description)
            }
}
