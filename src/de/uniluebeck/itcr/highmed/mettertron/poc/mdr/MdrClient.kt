package de.uniluebeck.itcr.highmed.mettertron.poc.mdr

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.papsign.ktor.openapigen.annotations.Response
import com.papsign.ktor.openapigen.interop.OpenAPIGenStatusPagesInterop
import de.uniluebeck.itcr.highmed.mettertron.poc.ApplicationError
import de.uniluebeck.itcr.highmed.mettertron.poc.api.admin.MdrLoginState
import de.uniluebeck.itcr.highmed.mettertron.poc.api.admin.ReceivedMdrLoginState
import de.uniluebeck.itcr.highmed.mettertron.poc.api.mdr.*
import de.uniluebeck.itcr.highmed.mettertron.poc.helper.CacheLayer
import de.uniluebeck.itcr.highmed.mettertron.poc.helper.GsonConverter
import de.uniluebeck.itcr.highmed.mettertron.poc.helper.ServerSettings
import de.uniluebeck.itcr.highmed.mettertron.poc.helper.ApiClient
import de.uniluebeck.itcr.highmed.mettertron.poc.httpClient
import de.uniluebeck.itcr.highmed.mettertron.poc.httpClientAuth
import io.ktor.application.*
import io.ktor.client.features.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun OpenAPIGenStatusPagesInterop.mdrExceptionHandling() {
    fun mdrAppError(message: String, mdrException: MdrException): ApplicationError = ApplicationError(
        message,
        mdrException.message,
        mdrException::class.simpleName,
        mdrException.cause?.message
    ).also { log.error(it.toString()) }

    exception<MdrCommunicationException, ApplicationError>(
        HttpStatusCode.BadGateway,
        ApplicationError.example
    ) {
        mdrAppError(
            "Error in communicating with the MDR web service", it
        )
    }
    exception<MdrNotFoundException, ApplicationError>(
        HttpStatusCode.NotFound,
        ApplicationError.example
    ) {
        mdrAppError("resource could not be found in the MDR", it)
    }
}

@Response("error in communication with MDR", 502) //bad gateway
class MdrCommunicationException(
    errorMessage: String,
    backtrace: Throwable? = null
) : MdrException(errorMessage, backtrace)

@Response("an MDR resource was not found", 404) //not found
class MdrNotFoundException(
    errorMessage: String,
    backtrace: Throwable? = null
) : MdrException(errorMessage, backtrace)

@Response("the MDR subsystem is in an invalid state", 500) //internal server error
class MdrInvalidStateException(
    errorMessage: String,
    backtrace: Throwable? = null
) : MdrException(errorMessage, backtrace)

@Response("an invalid cast was attempted when communicating with the MDR", 500)
class MdrInvalidCastException(
    errorMessage: String,
    backtrace: Throwable? = null
) : MdrException(errorMessage, backtrace)

abstract class MdrException(
    errorMessage: String,
    backtrace: Throwable? = null
) : Exception(errorMessage, backtrace)

class MdrClient(
    val settings: ServerSettings,
    cacheLayer: CacheLayer,
) : ApiClient(
    baseUrl = settings.mdr.url,
    prefix = settings.mdr.urlPrefix,
    cacheLayer = cacheLayer
) {
    var mdrLinks: MdrLinks? = null
    var loginState: MdrLoginState? = null
    val logger: Logger = LoggerFactory.getLogger("MdrClient")

    data class MdrLinks(
        val users: String,
        val itemsets: String,
        val domains: String,
        val definitions: String,
        val folders: String,
        val units: String
    )

    class MdrIndexResponse(override val links: List<MdrHateoasResponse.Link>) : LinkResponse

    private suspend fun getLinks() {
        fun LinkResponse.getLink(rel: String) = this.links.find { it.rel == rel }?.href
        try {
            this.mdrLinks = executeHateoasLinks<MdrIndexResponse>("/").let { links ->
                //client().get<LinkResponse>(buildRoute("/")).let { links ->
                return@let MdrLinks(
                    links.getLink("users") ?: throw MdrCommunicationException("missing user link in index"),
                    links.getLink("itemsets") ?: throw MdrCommunicationException("missing itemsets link in index"),
                    links.getLink("domains") ?: throw MdrCommunicationException("missing domains link in index"),
                    links.getLink("definitions")
                        ?: throw MdrCommunicationException("missing definitions link in index"),
                    links.getLink("folders") ?: throw MdrCommunicationException("missing folders link in index"),
                    links.getLink("units") ?: throw MdrCommunicationException("missing units link in index")
                )
            }
            logger.info("Got links: $mdrLinks")
        } catch (e: Exception) {
            logger.warn(e.message)
            throw e
        }
    }

    val gson: Gson =
        GsonBuilder()
            .registerTypeAdapter(MdrApiAttributeValue::class.java, GsonConverter.MdrApiAttributeDeserializer())
            .create()

    fun client() = httpClient {
        /*install(HttpTimeout) {
            requestTimeoutMillis = settings.http.requestTimeoutMillis
            connectTimeoutMillis = settings.http.connectTimeoutMillis
        }*/
        if (loginState != null && !loginState!!.isExpired)
            defaultRequest {
                header("Authorization", "Bearer ${loginState!!.accessToken}")
            }
        else
            throw MdrCommunicationException("there is no login token for the MDR client, please authenticate")
    }

    suspend inline fun <reified T> requestAndCastFromMdr(
        endpoint: String,
        buildRoute: Boolean = true
    ): T {
        login()
        val route = if (buildRoute) this.buildRoute(endpoint) else endpoint
        try {
            return cacheLayer.getFromCacheOrQuery(
                route,
                httpClient = this::client,
                getBlock = {},
                deserializerBlock = {
                    logger.debug("raw response from $route (casting to ${T::class.java.simpleName}): '$it'")
                    gson.fromJson(it, T::class.java)
                }
            )
            //return Gson().fromJson(rx, T::class.java)
        } catch (e: Exception) {
            throw MdrInvalidCastException("invalid cast to ${T::class.simpleName}", e)
        }
    }

    suspend inline fun <reified T : MdrHateoasResponse> executeHateoas(
        endpoint: String,
        buildRoute: Boolean = true,
    ): T = requestAndCastFromMdr(endpoint, buildRoute)

    suspend inline fun <reified T : Content> executeHateoasContent(
        endpoint: String,
        buildRoute: Boolean = true
    ): T = requestAndCastFromMdr(endpoint, buildRoute)

    suspend inline fun <reified T : LinkResponse> executeHateoasLinks(
        endpoint: String,
        buildRoute: Boolean = true
    ): T = requestAndCastFromMdr(endpoint, buildRoute)

    @Throws(MdrCommunicationException::class)
    suspend fun login(force: Boolean? = false): MdrLoginState? {
        if (loginState != null && !loginState!!.isExpired) {
            when (force) {
                null -> {
                    logger.debug("not forcing re-login due to missing force parameter")
                    return loginState
                }
                false -> {
                    logger.debug("login to MDR not required, logging in at ${loginState!!.expiresAt}")
                    return loginState
                }

                else -> {}
            }
        }
        val client = httpClientAuth {
            basic {
                credentials {
                    BasicAuthCredentials(settings.mdr.clientId, settings.mdr.clientSecret)
                }
            }
        }
        val call = joinUrl(settings.mdr.url, "/oauth/token")
        logger.info("Logging in to the MDR at $call")
        try {
            this.loginState = client.post<ReceivedMdrLoginState>(call) {
                body = FormDataContent(Parameters.build {
                    append("grant_type", settings.mdr.grantType)
                    append("scope", settings.mdr.scope)
                    append("username", settings.mdr.user)
                    append("password", settings.mdr.password)
                })
            }.let { MdrLoginState.fromReceivedMdrLoginState(it) }
            logger.info("Logged in successfully: $loginState")
            if (mdrLinks == null)
                getLinks()
            return loginState
        } catch (e: Exception) {
            logger.warn(e.message)
            throw MdrCommunicationException("error when logging in to the MDR", e)
        }
    }
}