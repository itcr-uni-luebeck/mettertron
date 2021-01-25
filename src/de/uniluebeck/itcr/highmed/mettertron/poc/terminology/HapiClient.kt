package de.uniluebeck.itcr.highmed.mettertron.poc.terminology

import ca.uhn.fhir.context.FhirContext
import com.papsign.ktor.openapigen.interop.OpenAPIGenStatusPagesInterop
import de.uniluebeck.itcr.highmed.mettertron.poc.ApplicationError
import de.uniluebeck.itcr.highmed.mettertron.poc.api.mdr.log
import de.uniluebeck.itcr.highmed.mettertron.poc.api.fhir.LookupResult
import de.uniluebeck.itcr.highmed.mettertron.poc.api.fhir.MappingParameters
import de.uniluebeck.itcr.highmed.mettertron.poc.helper.CacheLayer
import de.uniluebeck.itcr.highmed.mettertron.poc.helper.ApiClient
import de.uniluebeck.itcr.highmed.mettertron.poc.helper.ServerSettings
import de.uniluebeck.itcr.highmed.mettertron.poc.httpClient
import io.ktor.client.request.*
import io.ktor.features.*
import io.ktor.http.*
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Parameters
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("HapiLayer")

fun OpenAPIGenStatusPagesInterop.fhirExceptionHandling() {
    fun fhirAppError(message: String, fhirException: FhirException): ApplicationError = ApplicationError(
        message,
        fhirException.message,
        fhirException::class.simpleName,
        fhirException.cause?.message
    ).also { log.error(it.toString()) }

    exception<FhirMultipleResultsException, ApplicationError>(HttpStatusCode.Conflict, ApplicationError.example) {
        fhirAppError("multiple results", it)
    }
    exception<FhirCommunicationException, ApplicationError>(HttpStatusCode.BadGateway, ApplicationError.example) {
        fhirAppError("error in communicating with the FHIR TS Web Service", it)
    }
    exception<FhirNotFoundException, ApplicationError>(HttpStatusCode.NotFound, ApplicationError.example) {
        fhirAppError("a resource was not found in the FHIR TS", it)
    }
}

data class ValidationParameters(
    val result: Boolean,
    val message: String?,
    val display: String?
) {
    companion object {
        fun fromFhirParameters(fhirParameters: Parameters): ValidationParameters {
            return ValidationParameters(
                fhirParameters.getParameterBool("result"),
                fhirParameters.getParameter("message")?.toString(),
                fhirParameters.getParameter("display")?.toString()
            )
        }
    }
}

class HapiClient(
    serverSettings: ServerSettings,
    cacheLayer: CacheLayer
) : ApiClient(
    baseUrl = serverSettings.terminology.url,
    prefix = "",
    cacheLayer = cacheLayer
) {

    private val fhirContext = FhirContext.forR4()

    private val jsonParser = fhirContext.newJsonParser().apply {
        setPrettyPrint(true)
    }

    fun convertToJsonString(resource: Resource): String = jsonParser.encodeResourceToString(resource)

    private fun client() = httpClient {}

    suspend fun getCodeSystemByCanonical(csCanonical: String): CodeSystem =
        getResourceWithParams {
            parameter("url", csCanonical)
        }

    suspend fun getValueSetByCanonical(vsCanonical: String): ValueSet =
        getResourceWithParams {
            parameter("url", vsCanonical)
        }

    suspend fun getConceptMapByCanonical(cmCanonical: String): ConceptMap =
        getResourceWithParams {
            parameter("url", cmCanonical)
        }

    suspend fun callCodeSystemOperationLookup(
        codeSystemUrl: String,
        code: String,
        properties: List<String>? = null
    ): LookupResult = callParametersOperation(buildRoute("CodeSystem/\$lookup")) {
        parameter("code", code)
        parameter("system", codeSystemUrl)
        parameter("property", "designation")
        properties?.forEach {
            parameter("property", it)
        }
    }.let {
        LookupResult.fromFhirParameters(it)
    }

    suspend fun callCodeSystemOperationValidateCode(
        codeSystemUrl: String,
        code: String,
    ): ValidationParameters = callValidationOperation(buildRoute("CodeSystem/\$validate-code")) {
        parameter("url", codeSystemUrl)
        parameter("code", code)
    }

    suspend fun callConceptMapOperationTranslate(
        cmCanonical: String,
        code: String,
        codeSystemCanonical: String,
        valueSetCanonical: String?
    ): MappingParameters = callParametersOperation(buildRoute("ConceptMap/\$translate")) {
        parameter("url", cmCanonical)
        parameter("system", codeSystemCanonical)
        parameter("code", code)
        valueSetCanonical?.let {
            parameter("source", it)
        }
    }.let {
        MappingParameters.fromFhirParameters(it)
    }

    private suspend fun callParametersOperation(
        url: String,
        paramsBlock: HttpRequestBuilder.() -> Unit
    ): Parameters = cacheLayer.getFromCacheOrQuery(
        url,
        httpClient = this::client,
        getBlock = paramsBlock,
        deserializerBlock = { rx ->
            logger.info(rx)
            jsonParser.parseResource(rx) as Parameters
        }
    )

    private suspend fun callValidationOperation(
        url: String,
        paramsBlock: HttpRequestBuilder.() -> Unit
    ): ValidationParameters = callParametersOperation(url, paramsBlock).let {
        ValidationParameters.fromFhirParameters(it)
    }
    /*return cacheLayer.getFromCacheOrQuery(
        url,
        httpClient = this::client,
        getBlock = paramsBlock,
        deserializerBlock = { rx ->
            logger.info(rx)
            val parameters: Parameters = jsonParser.parseResource(rx) as Parameters
            parameters.let { ValidationParameters.fromFhirParameters(parameters) }
        }
    ).also {
        cacheLayer.logCache()
    }*/

    suspend fun callValueSetOperationValidateCode(
        valueSetUrl: String,
        codeSystemUrl: String,
        code: String,
    ): ValidationParameters = callValidationOperation(buildRoute("ValueSet/\$validate-code")) {
        parameter("url", valueSetUrl)
        parameter("system", codeSystemUrl)
        parameter("code", code)
    }

    private suspend inline fun <reified T : Resource> getResourceWithParams(block: HttpRequestBuilder.() -> Unit): T {
        val resourceName = T::class.java.simpleName
        val url = buildRoute(resourceName)
        try {
            val bundle = cacheLayer.getFromCacheOrQuery(
                url,
                httpClient = this::client,
                getBlock = block,
                deserializerBlock = { rx ->
                    logger.debug("got bundle from $url, received '$rx'")
                    (jsonParser.parseResource(rx) as Bundle).also { bundle ->
                        logger.info("parsed using HAPI to bundle: '$bundle'")
                    }
                }
            )
            /*val rx = client().get<String>(url, block).also { rx ->
                logger.info("requested $resourceName from $url, received: '$rx'")
            }
            val bundle = (jsonParser.parseResource(rx) as Bundle).also {
                logger.info("parsed using HAPI to bundle: '$it'")
            }*/
            if (!bundle.hasEntry()) {
                throw NotFoundException("the resource could not be found")
            }
            //todo: JPW
            if (bundle.entry.size > 1) {
                throw FhirMultipleResultsException("there are ${bundle.entry.size} entries in the resulting bundle")
            }
            val fullUrl =
                bundle.entryFirstRep.fullUrl ?: throw FhirCommunicationException("no full URL in resulting bundle")
            return cacheLayer.getFromCacheOrQuery(
                fullUrl,
                httpClient = this::client,
                getBlock = {},
                deserializerBlock = { rx ->
                    logger.info("requested full resource, casting to $resourceName, got '$rx'")
                    jsonParser.parseResource(rx) as T
                }
            )
            /*return client().get<String>(fullUrl).let {
                logger.info("requested full resource, casting to $resourceName, got '$it'")
                jsonParser.parseResource(it) as T
            }*/
        } catch (e: NotFoundException) {
            throw FhirNotFoundException("resource of type $resourceName could not be found", e)
        } catch (e: Exception) {
            throw FhirCommunicationException("error in terminology server communication", e)
        }
    }

}

class FhirMultipleResultsException(errorMessage: String, backtrace: Throwable? = null) :
    FhirException(errorMessage, backtrace)

class FhirCommunicationException(errorMessage: String, backtrace: Throwable? = null) :
    FhirException(errorMessage, backtrace)

class FhirNotFoundException(errorMessage: String, backtrace: Throwable? = null) : FhirException(errorMessage, backtrace)

abstract class FhirException(errorMessage: String, backtrace: Throwable?) : Exception(errorMessage, backtrace)