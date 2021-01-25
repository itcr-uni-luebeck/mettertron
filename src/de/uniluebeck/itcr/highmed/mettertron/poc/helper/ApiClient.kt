package de.uniluebeck.itcr.highmed.mettertron.poc.helper

abstract class ApiClient(private val baseUrl: String, private val prefix: String, val cacheLayer: CacheLayer) {

    fun buildRoute(endpoint: String) =
        when (prefix.isBlank()) {
            true -> joinUrl(baseUrl, endpoint)
            false -> joinUrl(baseUrl, prefix, endpoint)
        }

    fun joinUrl(vararg components: String) =
        components.joinToString("/") { it.trimStart('/').trimEnd('/') }
}
