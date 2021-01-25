package de.uniluebeck.itcr.highmed.mettertron.poc.api.fhir

import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import de.uniluebeck.itcr.highmed.mettertron.poc.api.fhir.LookupResult
import de.uniluebeck.itcr.highmed.mettertron.poc.terminology.HapiClient

data class CanonicalUrlParams(
    @QueryParam("the canonical url of the resource to retrieve") val canonicalUrl: String
)

data class FhirLookupParameters(
    @QueryParam("the canonical URL of the CodeSystem/ValueSet to use") val canonicalUrl: String,
    @QueryParam("the code to lookup") val code: String,
    @QueryParam("the parameters to query as well") val parameterCodes: List<String>?
)

fun NormalOpenAPIRoute.fhirResourcesApi(hapiClient: HapiClient) {

    route("CodeSystem") {
        get<CanonicalUrlParams, String>(
            info("Get a code system from the terminology server")
        ) {
            respond(
                hapiClient.convertToJsonString(hapiClient.getCodeSystemByCanonical(it.canonicalUrl)),
            )
        }
        route("lookup") {
            get<FhirLookupParameters, LookupResult>(
                info("lookup a code from a code system by canonical URL")
            ) {
                respond(
                    hapiClient.callCodeSystemOperationLookup(
                        it.canonicalUrl,
                        it.code,
                        it.parameterCodes
                    )
                )
            }
        }
    }
    route("ValueSet") {
        get<CanonicalUrlParams, String>(
            info("get a value set from the terminology server")
        ) {
            respond(
                hapiClient.convertToJsonString(hapiClient.getValueSetByCanonical(it.canonicalUrl))
            )
        }
    }
    route("ConceptMap") {
        get<CanonicalUrlParams, String>(
            info("get a concept map from the terminology server")
        ) {
            respond(
                hapiClient.convertToJsonString(hapiClient.getConceptMapByCanonical(it.canonicalUrl))
            )
        }
    }
}