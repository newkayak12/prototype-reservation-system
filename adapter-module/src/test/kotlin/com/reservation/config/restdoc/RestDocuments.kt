package com.reservation.config.restdoc

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails
import com.epages.restdocs.apispec.ResourceSnippetDetails
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.headers.HeaderDescriptor
import org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.requestPartBody
import org.springframework.restdocs.payload.PayloadDocumentation.requestPartFields
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
    private var requestHeader: Array<Header>? = null,
    private val responseHeader: Array<Header>? = null,
    private val query: Array<Query>? = null,
    private val pathParameter: Array<PathParameter>? = null,
    private val requestPart: Array<Part>? = null,
    private val requestPartBody: Array<RequestPartFields>? = null,
) {
    private var tags: ResourceSnippetDetails = resourceDetails()

    private inline fun <T, R : Snippet> MutableSet<Snippet>.addIfNotNullOrEmpty(
        data: Array<T>?,
        parser: (T) -> Any,
        builder: (List<Any>) -> R,
    ) {
        data?.takeIf { it.isNotEmpty() }?.let { list ->
            val descriptor = list.map { parser(it) }
            add(builder(descriptor))
        }
    }

    fun authorizedRequestHeader(): RestDocuments {
        val appendableHeader = requestHeader?.toMutableList() ?: mutableListOf<Header>()

        appendableHeader.add(
            Header(HttpHeaders.AUTHORIZATION, false, "AccessToken"),
        )
        requestHeader = appendableHeader.toTypedArray()
        return this
    }

    fun create(): RestDocumentationResultHandler {
        val snippets = mutableSetOf<Snippet>()

        tags.summary(summary)
        tags.description(description)
        tags.tags(*(documentTags ?: listOf("api")).toTypedArray())

        if (requestHeader?.isNotEmpty() == true) {
            requestHeader = requestHeader?.distinctBy { it.name }?.toTypedArray()
        }

        bindRequestHeader(snippets)

        bindResponseHeader(snippets)

        bindRequestBody(snippets)

        bindResponseBody(snippets)

        bindQueryParams(snippets)

        bindPathParams(snippets)

        bindRequestPart(snippets)

        bindRequestPartBody(snippets)

        return document(
            identifier = identifier,
            description = description,
            summary = summary,
            requestPreprocessor = preprocessRequest(prettyPrint()),
            responsePreprocessor = preprocessResponse(prettyPrint()),
            snippets = snippets.toTypedArray(),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun bindRequestPartBody(snippets: MutableSet<Snippet>) {
        for ((name, bodies) in requestPartBody ?: arrayOf()) {
            snippets.add(requestPartBody(name))
            snippets.addIfNotNullOrEmpty(
                bodies,
                { it.parse() },
                { requestPartFields(name, it as List<FieldDescriptor>) },
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun bindRequestPart(snippets: MutableSet<Snippet>) {
        snippets.addIfNotNullOrEmpty(
            requestPart,
            { it.parse() },
            { requestParts(it as List<RequestPartDescriptor>) },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun bindPathParams(snippets: MutableSet<Snippet>) {
        snippets.addIfNotNullOrEmpty(
            pathParameter,
            { it.parse() },
            { pathParameters(it as List<ParameterDescriptor>) },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun bindQueryParams(snippets: MutableSet<Snippet>) {
        snippets.addIfNotNullOrEmpty(
            query,
            { it.parse() },
            { queryParameters(it as List<ParameterDescriptor>) },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun bindResponseBody(snippets: MutableSet<Snippet>) {
        snippets.addIfNotNullOrEmpty(
            responseBody,
            { it.parse() },
            { responseFields(it as List<FieldDescriptor>) },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun bindRequestBody(snippets: MutableSet<Snippet>) {
        snippets.addIfNotNullOrEmpty(
            requestBody,
            { it.parse() },
            { requestFields(it as List<FieldDescriptor>) },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun bindResponseHeader(snippets: MutableSet<Snippet>) {
        snippets.addIfNotNullOrEmpty(
            responseHeader,
            { it.parse() },
            { requestHeaders(it as List<HeaderDescriptor>) },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun bindRequestHeader(snippets: MutableSet<Snippet>) {
        snippets.addIfNotNullOrEmpty(
            requestHeader,
            { it.parse() },
            { requestHeaders(it as List<HeaderDescriptor>) },
        )
    }
}
