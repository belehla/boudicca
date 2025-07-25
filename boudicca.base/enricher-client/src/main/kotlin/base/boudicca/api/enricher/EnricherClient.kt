package base.boudicca.api.enricher

import base.boudicca.enricher.openapi.api.EnricherApi
import base.boudicca.enricher.openapi.model.EnrichRequestDTO
import base.boudicca.model.Event
import base.boudicca.openapi.ApiClient
import base.boudicca.openapi.ApiException
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.javahttpclient.JavaHttpClientTelemetry
import java.net.http.HttpClient
import base.boudicca.enricher.openapi.model.Event as EnricherOpenApiEvent

class EnricherClient(
    private val enricherUrl: String,
    private val otel: OpenTelemetry = GlobalOpenTelemetry.get()
) {

    private val enricherApi: EnricherApi

    init {
        check(enricherUrl.isNotBlank()) { "you need to pass an enricherUrl!" }
        val apiClient = object : ApiClient() {
            override fun getHttpClient(): HttpClient? {
                return JavaHttpClientTelemetry
                    .builder(otel)
                    .build()
                    .newHttpClient(super.getHttpClient())
            }
        }
        apiClient.updateBaseUri(enricherUrl)

        enricherApi = EnricherApi(apiClient)
    }

    fun enrichEvents(events: List<Event>): List<Event> {
        try {
            return enricherApi.enrich(EnrichRequestDTO().events(events.map { mapToEnricherEvent(it) }))
                .map { toEvent(it) }
        } catch (e: ApiException) {
            throw EnricherException("could not reach enricher: $enricherUrl", e)
        }
    }

    private fun toEvent(enricherEvent: EnricherOpenApiEvent): Event {
        return Event(enricherEvent.name!!, enricherEvent.startDate!!, enricherEvent.data ?: mapOf())
    }

    private fun mapToEnricherEvent(event: Event): EnricherOpenApiEvent {
        return EnricherOpenApiEvent()
            .name(event.name)
            .startDate(event.startDate)
            .data(event.data)
    }
}

class EnricherException(msg: String, e: ApiException) : RuntimeException(msg, e)
