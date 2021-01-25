package de.uniluebeck.itcr.highmed.mettertron.poc.api.admin

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime

data class ReceivedMdrLoginState(
    val accessToken: String,
    @SerializedName("expires_in") val expiresInSeconds: Long,
    val tokenType: String,
    val scope: String,
)

/**
 * we need to wrap the received login state, because setting the "expiresAt" parameter in the body of the class
 * weirdly did not work (if we use val... get() = now.plusSeconds(...), the value changes over time...
 * removing the get() failed to initialize the value, so NPE
 *
 */
data class MdrLoginState(
    val accessToken: String,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val tokenType: String,
    val scope: String
) {
    val isExpired: Boolean get() = LocalDateTime.now() >= expiresAt
    override fun toString(): String =
        "MdrLoginState(accessToken=${
            accessToken.subSequence(
                0,
                6
            )
        }-..., expiresAt=$expiresAt, tokenType=$tokenType, scope=$scope)"

    companion object {
        fun fromReceivedMdrLoginState(received: ReceivedMdrLoginState) =
            LocalDateTime.now().let { now ->
                MdrLoginState(
                    accessToken = received.accessToken,
                    createdAt = now,
                    expiresAt = now.plusSeconds(received.expiresInSeconds),
                    tokenType = received.tokenType,
                    scope = received.scope
                )
            }
    }
}