package de.uniluebeck.itcr.highmed.mettertron.poc

import com.google.gson.FieldNamingPolicy
import com.papsign.ktor.openapigen.APITag
import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.annotations.Response
import com.papsign.ktor.openapigen.annotations.properties.description.Description
import com.papsign.ktor.openapigen.interop.withAPI
import com.papsign.ktor.openapigen.openAPIGen
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.route.tag
import com.papsign.ktor.openapigen.schema.namer.DefaultSchemaNamer
import com.papsign.ktor.openapigen.schema.namer.SchemaNamer
import de.uniluebeck.itcr.highmed.mettertron.poc.api.admin.adminApi
import de.uniluebeck.itcr.highmed.mettertron.poc.api.fhir.fhirResourcesApi
import de.uniluebeck.itcr.highmed.mettertron.poc.api.mdr.mdrDefitionsApi
import de.uniluebeck.itcr.highmed.mettertron.poc.helper.ServerSettings
import de.uniluebeck.itcr.highmed.mettertron.poc.api.magic.magicApi
import de.uniluebeck.itcr.highmed.mettertron.poc.api.magic.magicExceptionHandling
import de.uniluebeck.itcr.highmed.mettertron.poc.helper.CacheLayer
import de.uniluebeck.itcr.highmed.mettertron.poc.mdr.MdrClient
import de.uniluebeck.itcr.highmed.mettertron.poc.mdr.mdrExceptionHandling
import de.uniluebeck.itcr.highmed.mettertron.poc.terminology.HapiClient
import de.uniluebeck.itcr.highmed.mettertron.poc.terminology.fhirExceptionHandling
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.json.*
import kotlinx.coroutines.*
import io.ktor.client.features.logging.*
import java.io.FileInputStream
import java.lang.Exception
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.timer
import kotlin.reflect.KType

fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(ContentNegotiation) {
        gson {
            setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        }
    }

    install(OpenAPIGen) {
        info {
            version = "0.0.1"
            title = "METTERTRON Proof Of Concept API"
            description = "Demonstrator for integration of an Meta-Data Repository with a FHIR-based terminology server"
            contact {
                name = "Joshua Wiedekopf"
                email = "j.wiedekopf@uni-luebeck.de"
                url = "https://itcr.uni-luebeck.de"
            }
        }
        server("http://localhost:${environment.config.property("ktor.deployment.port").getString()}") {
            description = "the testserver on localhost"
        }
        replaceModule(DefaultSchemaNamer, object : SchemaNamer {
            val regex = Regex("[A-Za-z0-9_.]+")
            override fun get(type: KType): String {
                return type.toString().replace(regex) { it.value.split(".").last() }.replace(Regex(">|<|, "), "_")
            }
        })
    }

    val cacheLayer = CacheLayer(
        acceptableCacheAgeSeconds = settings.cache.acceptableAgeSeconds,
        maximumElements = settings.cache.maximumElements
    )
    val mdrClient = MdrClient(settings, cacheLayer)
    val hapiClient = HapiClient(settings, cacheLayer)

    fun loginToMdr() {
        GlobalScope.launch {
            try {
                mdrClient.login(false)
            } catch (e: Exception) {
                log.error("background MDR login failed: ${e::class.simpleName} - ${e.message}")
            }
        }
    }

    timer(
        name = "LoginTimer",
        daemon = false,
        initialDelay = 1000L,
        period = settings.background.autoLoginSeconds * 1000
    ) {
        loginToMdr()
    }

    timer(
        name = "CacheTimer",
        daemon = false,
        initialDelay = settings.background.cleanupCacheSeconds * 1000,
        period = settings.background.cleanupCacheSeconds * 1000
    ) {
        GlobalScope.launch {
            cacheLayer.cleanCache()
        }
    }

    routing {

        get("/openapi.json") {
            call.respond(application.openAPIGen.api.serialize())
        }
        get("/") {
            call.respondRedirect("/swagger-ui/index.html?url=/openapi.json", true)
        }
    }

    apiRouting {
        route("/admin").tag(OpenApiTags.Admin) {
            adminApi(mdrClient)
        }
        route("/mdr").tag(OpenApiTags.MdrDefinitions) {
            mdrDefitionsApi(mdrClient)
        }
        route("/fhir").tag(OpenApiTags.FhirResources) {
            fhirResourcesApi(hapiClient)
        }
        route("/magic").tag(OpenApiTags.Magic) {
            magicApi(mdrClient, hapiClient)
        }
    }

    install(StatusPages) {
        withAPI(openAPIGen) {
            mdrExceptionHandling()
            fhirExceptionHandling()
            magicExceptionHandling()
        }
    }
}

@Response("The application has detected an error")
data class ApplicationError(
    @Description("the error message") val message: String,
    @Description("the detailed error message") val innerMessage: String?,
    @Description("the type of the exception") val exceptionType: String?,
    @Description("the message of the causing exception") val backtraceMessage: String?
) {
    companion object {
        val example = ApplicationError(
            message = "Error communicating with MDR",
            innerMessage = "Login failed",
            exceptionType = "MdrCommunicationException",
            backtraceMessage = "Connect timeout has been expired"
        )
    }
}

/**
 * get an instance of the HttpClient with a provided auth config, e.g. `basic { username = "foo"; password = "bar" }` in the block
 */
fun httpClientAuth(authenticationInit: (Auth.() -> Unit)? = null): HttpClient =
    httpClient(LogLevel.INFO) {
        if (authenticationInit != null)
            install(Auth, configure = authenticationInit)
    }

fun httpClient(logLevel: LogLevel = LogLevel.INFO, init: (HttpClientConfig<CIOEngineConfig>.() -> Unit)?) =
    HttpClient(CIO) {
        install(JsonFeature) {
            serializer = GsonSerializer {
                serializeNulls()
                setPrettyPrinting()
                setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            }
        }
        install(Logging) {
            level = logLevel
        }
        /*engine {
            https {
                trustManager = object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate?> = arrayOf()
                    override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
                    override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
                }
            }
        }*/
        init?.let { it() } //call the provided init block if not-null
    }

val settings = ServerSettings.fromHocon()

enum class OpenApiTags(override val description: String) : APITag {
    Admin("Administration Routes, such as status, MDR Login, etc."),
    MdrDefinitions("Get definitions in the MDR"),
    FhirResources("Get resources in the FHIR Terminology Server"),
    Magic("the real magic of the app, performing mapping instance data, mediated by the MDR and the terminology server")
}