package com.reservation.config.restdoc

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails
import com.epages.restdocs.apispec.ResourceSnippetDetails
import org.springframework.restdocs.headers.HeaderDescriptor
import org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.ParameterDescriptor
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.restdocs.request.RequestDocumentation.requestParts
import org.springframework.restdocs.request.RequestPartDescriptor
import org.springframework.restdocs.snippet.Snippet

@SuppressWarnings("LongParameterList")
class RestDocuments(
    private val identifier: String,
    private val documentTags: List<String>?,
    private val summary: String,
    private val description: String? = null,
    private val requestBody: Array<Body>? = null,
    private val responseBody: Array<Body>? = null,
    private val requestHeader: Array<Header>? = null,
    private val responseHeader: Array<Header>? = null,
    private val query: Array<Query>? = null,
    private val pathParameter: Array<PathParameter>? = null,
    private val part: Array<Part>? = null,
) {
    private var tags: ResourceSnippetDetails = resourceDetails()

    private inline fun <T, R : Snippet> MutableSet<Snippet>.addIfNotNullOrEmpty(
        data: Array<T>?,
        parser: (T) -> Any,
        builder: (List<Any>) -> R,
    ) {
        data?.takeIf { it.isNotEmpty() }
            ?.let { list ->
                val descriptor = list.map { parser(it) }
                add(builder(descriptor))
            }
    }

    fun create(): RestDocumentationResultHandler {
        val snippets = mutableSetOf<Snippet>()

        tags.summary(summary)
        tags.description(description)
        tags.tags(*(documentTags ?: listOf("api")).toTypedArray())

        snippets.addIfNotNullOrEmpty(
            requestHeader,
            { it.parse() as HeaderDescriptor },
            { requestHeaders(it as List<HeaderDescriptor>) },
        )

        snippets.addIfNotNullOrEmpty(
            responseHeader,
            { it.parse() as HeaderDescriptor },
            { responseHeaders(it as List<HeaderDescriptor>) },
        )

        snippets.addIfNotNullOrEmpty(
            requestBody,
            { it.parse() },
            { requestFields(it as List<FieldDescriptor>) },
        )

        snippets.addIfNotNullOrEmpty(
            responseBody,
            { it.parse() },
            { responseFields(it as List<FieldDescriptor>) },
        )

        snippets.addIfNotNullOrEmpty(
            query,
            { it.parse() },
            { queryParameters(it as List<ParameterDescriptor>) },
        )

        snippets.addIfNotNullOrEmpty(
            pathParameter,
            { it.parse() },
            { pathParameters(it as List<ParameterDescriptor>) },
        )

        snippets.addIfNotNullOrEmpty(
            part,
            { it.parse() },
            { requestParts(it as List<RequestPartDescriptor>) },
        )

        return document(
            identifier = identifier,
            description = description,
            summary = summary,
            requestPreprocessor = preprocessRequest(prettyPrint()),
            responsePreprocessor = preprocessResponse(prettyPrint()),
            snippets = snippets.toTypedArray(),
        )
    }
}
