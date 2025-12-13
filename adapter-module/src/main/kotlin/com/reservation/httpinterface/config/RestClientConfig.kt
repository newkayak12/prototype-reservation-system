package com.reservation.httpinterface.config

import com.reservation.httpinterface.timetable.FindTimeTableOccupancyHttpInterface
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpResponse
import org.springframework.retry.support.RetryTemplate
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

@Configuration
class RestClientConfig(
    val httpInterfaceProperties: HttpInterfaceProperties,
) {
    @Bean
    fun retryTemplate(): RetryTemplate {
        val retry = httpInterfaceProperties.retry

        return RetryTemplate
            .builder()
            .exponentialBackoff(
                retry.initialInterval,
                retry.multiplier,
                retry.maxInterval,
            )
            .maxAttempts(retry.maxAttempts)
            .build()
    }

    @Bean
    fun restClient(retryTemplate: RetryTemplate): RestClient =
        RestClient.builder()
            .requestInterceptor { request, body, execution ->
                retryTemplate.execute<ClientHttpResponse, Exception> {
                    execution.execute(request, body)
                }
            }
            .build()

    @Bean
    fun findTimeTableOccupancyHttpInterface(
        restClient: RestClient,
    ): FindTimeTableOccupancyHttpInterface {
        val internal = httpInterfaceProperties.internal
        val url = internal.timeTableOccupancy

        val mutatedRestClient: RestClient =
            restClient
                .mutate()
                .baseUrl(url)
                .build()

        val adapter = RestClientAdapter.create(mutatedRestClient)

        return HttpServiceProxyFactory
            .builderFor(adapter)
            .build()
            .createClient(FindTimeTableOccupancyHttpInterface::class.java)
    }
}
