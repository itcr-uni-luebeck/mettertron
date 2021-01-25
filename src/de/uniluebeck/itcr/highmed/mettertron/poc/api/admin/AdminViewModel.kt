package de.uniluebeck.itcr.highmed.mettertron.poc.api.admin

import com.papsign.ktor.openapigen.annotations.Response
import com.papsign.ktor.openapigen.annotations.properties.description.Description
import de.uniluebeck.itcr.highmed.mettertron.poc.helper.CacheLayer
import de.uniluebeck.itcr.highmed.mettertron.poc.mdr.MdrClient
import io.ktor.http.*
import java.time.LocalDateTime
import kotlin.math.round

@Response("represent the status of the system", 200)
data class Status(
    @Description("Is the system authenticated towards the MDR?") val mdrIsAuthenticated: Boolean,
    @Description("local datetime when authentication towards the MDR expires") val mdrAuthenticationValidUntil: String?
) {
    companion object {
        fun fromMdrClientStatus(mdrClient: MdrClient): Status = when (mdrClient.loginState) {
            null -> Status(false, null)
            else -> Status(!mdrClient.loginState!!.isExpired, mdrClient.loginState!!.expiresAt.toHttpDateString())
        }

        fun example(): Status = Status(true, LocalDateTime.now().plusSeconds(1000).toHttpDateString())
    }
}

@Response("represent a token that can be used to authenticate with the MDR")
data class MdrLoginToken(
    @Description("the login token") val token: String
) {
    companion object {
        fun fromMdrClientStatus(mdrClient: MdrClient) =
            mdrClient.loginState?.let { MdrLoginToken(it.accessToken) }
                ?: throw IllegalStateException("login not possible")

        fun example() = MdrLoginToken("8e9093d3-0070-4ba7-bbaa-f7e9cb396d65")
    }
}

data class CacheStatus(
    val maxAgeSeconds: Long,
    val maxElements: Int,
    val numberElements: Int,
    val fullness: String,
    val cachedUrls: List<String>
) {
    companion object {
        suspend fun fromCacheLayer(cacheLayer: CacheLayer) = CacheStatus(
            maxAgeSeconds = cacheLayer.acceptableCacheAgeSeconds,
            maxElements = cacheLayer.maximumElements,
            numberElements = cacheLayer.getCount(),
            fullness = "${round(cacheLayer.getCount() / cacheLayer.maximumElements.toDouble() * 100)}%",
            cachedUrls = cacheLayer.getUrls()
        )
    }
}