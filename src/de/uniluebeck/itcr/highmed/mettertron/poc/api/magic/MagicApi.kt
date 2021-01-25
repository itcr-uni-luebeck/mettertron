package de.uniluebeck.itcr.highmed.mettertron.poc.api.magic

import com.papsign.ktor.openapigen.annotations.Response
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.interop.OpenAPIGenStatusPagesInterop
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import de.uniluebeck.itcr.highmed.mettertron.poc.ApplicationError
import de.uniluebeck.itcr.highmed.mettertron.poc.api.fhir.MappingParameters
import de.uniluebeck.itcr.highmed.mettertron.poc.api.mdr.*
import de.uniluebeck.itcr.highmed.mettertron.poc.mdr.MdrClient
import de.uniluebeck.itcr.highmed.mettertron.poc.mdr.MdrNotFoundException
import de.uniluebeck.itcr.highmed.mettertron.poc.terminology.HapiClient
import de.uniluebeck.itcr.highmed.mettertron.poc.terminology.ValidationParameters
import io.ktor.client.features.*
import io.ktor.http.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("MagicAPI")

open class MagicException(
    errorMessage: String,
    backtrace: Throwable? = null
) : Exception(errorMessage, backtrace)

@Response("the attributes of a form field do not contain the required attributes")
class MagicMissingAttributesException(
    errorMessage: String,
    backtrace: Throwable? = null
) : MagicException(errorMessage, backtrace)

@Response("error in validation")
class MagicValidationException(
    errorMessage: String,
    backtrace: Throwable? = null
) : MagicException(errorMessage, backtrace)

@Response("error in FHIR mapping")
class MagicFhirMappingException(
    errorMessage: String,
    backtrace: Throwable? = null
) : MagicException(errorMessage, backtrace)

data class FhirValidationParams(
    @QueryParam("the id of the form") override val formId: String,
    @QueryParam("the code of the field") override val fieldCode: String,
    @QueryParam("the value of the field to validate") override val fieldValue: String
) : MagicParams

data class FhirMapParams(
    @QueryParam("the id of the form") override val formId: String,
    @QueryParam("the code of the field") override val fieldCode: String,
    @QueryParam("the value of the field to lookup") override val fieldValue: String,
    @QueryParam("true if full definitions of the target codes should be queried from the TS") val lookupCodes: Boolean? = false,
    @QueryParam("true if the property lookup should include the default parameters at https://www.hl7.org/fhir/codesystem-concept-properties.html") val includeFHIRDefinedProperties: Boolean? = false
) : MagicParams

interface MagicParams {
    val formId: String
    val fieldCode: String
    val fieldValue: String
}

fun OpenAPIGenStatusPagesInterop.magicExceptionHandling() {
    fun magicAppError(message: String, magicException: MagicException): ApplicationError = ApplicationError(
        message,
        magicException.message,
        magicException::class.simpleName,
        magicException.cause?.message
    ).also { log.error(it.toString()) }

    exception<MagicValidationException, ApplicationError>(HttpStatusCode.BadRequest, ApplicationError.example) {
        magicAppError("error in validation", it)
    }
    exception<MagicMissingAttributesException, ApplicationError>(HttpStatusCode.BadRequest, ApplicationError.example) {
        magicAppError("missing attributes for validation", it)
    }
    exception<MagicFhirMappingException, ApplicationError>(HttpStatusCode.BadRequest, ApplicationError.example) {
        magicAppError("error in FHIR mapping", it)
    }
}

private class MagicApiWrapper(
    val mdrClient: MdrClient,
    val hapiClient: HapiClient
) {

    private suspend fun getFieldAttributes(formId: String, fieldCode: String): List<MdrAttribute>? {
        val attributes = mdrClient.getAttributesOfFormById(formId)
        return when (attributes.fields.keys.contains(fieldCode)) {
            true -> attributes.fields[fieldCode]
            else -> {
                attributes.sections.mapNotNull { section ->
                    @Suppress("ReplaceGetOrSet")
                    section.value.fieldAttributes.get(fieldCode)
                }.firstOrNull()
            }
        }
    }

    private suspend fun getDefinitionAttributes(formId: String, fieldCode: String): List<MdrAttribute> {
        val form = mdrClient.getFormById(formId)
        var definition = form.fields.firstOrNull { it.id == fieldCode }?.definition

        if (definition == null) {
            formLoop@ for (section in form.sections) {
                for (field in section.fields) {
                    if (field.id == fieldCode) {
                        definition = field.definition
                        break@formLoop
                    }
                }
            }
        }

        if (definition.isNullOrBlank()) {
            throw MdrNotFoundException("the field $fieldCode has no definition")
        }

        val definitionSplit = definition.split(":")
        val definitionVersion = definitionSplit.last()
        val definitionCode = definitionSplit[definitionSplit.size - 2]

        val attributes = mdrClient.getAttributesOfDefinitionById(definitionCode, definitionVersion)

        if (attributes.definition.isEmpty()) {
            throw MdrNotFoundException("the definition $definition has no attributes")
        }
        return attributes.definition
    }

    suspend fun fhirValidateFormField(
        params: MagicParams
    ): FormFieldFhirValidation {
        val theFieldAttributes = getFieldAttributes(params.formId, params.fieldCode)
        logger.info(theFieldAttributes?.joinToString(", ") ?: "no field attributes")
        if (theFieldAttributes == null) {
            throw MdrNotFoundException("the field ${params.fieldCode} could not be found in form ${params.formId}")
        }

        val theDefinitionAttributes = getDefinitionAttributes(params.formId, params.fieldCode)

        try {
            val validationAttributes =
                verifyAttributesAllowCodeVerification(theDefinitionAttributes).filterIsInstance<MdrAttribute.MdrStringAttribute>()
            val validationStatus = validateCodeWithAttributes(params.fieldValue, validationAttributes)

            return FormFieldFhirValidation(
                formId = params.formId,
                fieldCode = params.fieldCode,
                fieldContentCode = params.fieldValue,
                validationStatus = validationStatus.first,
                validationMessages = validationStatus.second
            )
        } catch (e: MagicMissingAttributesException) {
            throw MagicValidationException(
                errorMessage = "the attributes on the field ${params.fieldCode} @ form ${params.formId} do not allow verification",
                backtrace = e
            )
        }
    }

    private fun verifyAttributesAllowCodeVerification(
        attributes: List<MdrAttribute>
    ): List<MdrAttribute> {
        val attributesSettings = mdrClient.settings.mdrAttributes
        val fhirDomainAttributes = attributes.filter { it.domain == attributesSettings.domainCode }
        if (fhirDomainAttributes.none()) {
            throw MagicMissingAttributesException("there are no attributes in the ${attributesSettings.domainCode} domain")
        }
        logger.info("Fhir Domain Attributes: ${fhirDomainAttributes.joinToString(", ")}")
        val csVsAttributes =
            fhirDomainAttributes.filter { it.attribute == attributesSettings.fhirCsCanonical || it.attribute == attributesSettings.fhirVsCanonical }
        if (csVsAttributes.none()) {
            throw MagicMissingAttributesException("there are no attributes in the ${attributesSettings.domainCode} domain")
        }
        val csVsAttributesCounts: Map<String, Int> = csVsAttributes.groupingBy { it.attribute }.eachCount()
        if (attributesSettings.fhirCsCanonical !in csVsAttributesCounts && attributesSettings.fhirVsCanonical in csVsAttributesCounts) {
            throw MagicMissingAttributesException("there is no CS attribute, but a value set attribute on the element - that makes no sense!")
        }
        if (attributesSettings.fhirVsCanonical in csVsAttributesCounts && csVsAttributesCounts[attributesSettings.fhirVsCanonical]!! > 1) {
            throw MagicValidationException("there are multiple VS attributes - that makes no sense!")
        }
        logger.debug("CS / VS Attributes: $csVsAttributesCounts")
        return csVsAttributes
    }

    private suspend fun validateCodeWithAttributes(
        code: String,
        csVsAttributes: List<MdrAttribute.MdrStringAttribute>
    ): Pair<ValidationStatus, List<String>> {
        val attributesSettings = mdrClient.settings.mdrAttributes
        val csAttributes = csVsAttributes.filter { it.attribute == attributesSettings.fhirCsCanonical }
        val csValidation = csAttributes.map {
            hapiClient.callCodeSystemOperationValidateCode(it.value, code)
        }
        logger.debug("CS-based validation: ${csValidation.joinToString("; ")}")
        if (csValidation.any { !it.result }) {
            return Pair(ValidationStatus.INVALID, csValidation.mapNotNull { it.message })
        }
        val vsAttribute = csVsAttributes.firstOrNull { it.attribute == attributesSettings.fhirVsCanonical }
        if (vsAttribute != null) {
            val vsWithCsValidation: Map<String, ValidationParameters> = csAttributes
                .associate { csa ->
                    csa.value to hapiClient.callValueSetOperationValidateCode(vsAttribute.value, csa.value, code)
                }
            val valueValidInCodeSystem = vsWithCsValidation.any {
                it.value.result
            }
            logger.debug("VS-based validation: $vsWithCsValidation -- valid in VS: $valueValidInCodeSystem")
            if (!valueValidInCodeSystem) {
                return Pair(ValidationStatus.NOT_IN_VALUESET, vsWithCsValidation.mapNotNull { it.value.message })
            }
        }
        return Pair(ValidationStatus.VALID, emptyList())
    }

    suspend fun mapFormField(params: FhirMapParams): FormFieldCodeMapping {
        val attributesSettings = mdrClient.settings.mdrAttributes
        val validation = fhirValidateFormField(params)
        if (validation.validationStatus != ValidationStatus.VALID) {
            throw MagicValidationException("the form field (or its value) is not valid")
        }
        val theDefinitionAttributes = getDefinitionAttributes(params.formId, params.fieldCode).filterIsInstance<MdrAttribute.MdrStringAttribute>()

        val cmAttribute =
            theDefinitionAttributes.firstOrNull { it.attribute == attributesSettings.fhirCmCanonical }
                ?: throw MagicMissingAttributesException("there is no concept map attribute on the specified field!")
        val vsAttribute =
            theDefinitionAttributes.firstOrNull { it.attribute == attributesSettings.fhirVsCanonical }
        try {
            val mapResults =
                theDefinitionAttributes
                    .filter { it.attribute == attributesSettings.fhirCsCanonical }.map { csAttribute ->
                        hapiClient.callConceptMapOperationTranslate(
                            cmCanonical = cmAttribute.value,
                            params.fieldValue,
                            csAttribute.value,
                            vsAttribute?.value
                        )
                    }
            val mappingSuccess = mapResults.any { it.result }
            val mappingMessages = mapResults.mapNotNull { it.message }
            val mappings: List<MappingParameters.MappingMatch> = mapResults.flatMap { it.match }
            //todo JPW
            when (params.lookupCodes) {
                null, false -> logger.debug("not looking up code definitions")
                true -> {
                    mappings.forEach {
                        it.getDetail(
                            hapiClient = hapiClient,
                            includeFHIRDefinedProperties = params.includeFHIRDefinedProperties ?: false
                        )
                    }
                }
            }

            return FormFieldCodeMapping(
                formId = params.formId,
                fieldCode = params.fieldCode,
                fieldContentCode = params.fieldValue,
                mappingSuccess = mappingSuccess,
                mappingMessages = mappingMessages,
                mappings = mappings
            )
        } catch (e: ClientRequestException) {
            throw MagicFhirMappingException(
                "there was an error when requesting the mapping from the terminology server",
                e.cause
            )
        }

    }
}

fun NormalOpenAPIRoute.magicApi(
    mdrClient: MdrClient,
    hapiClient: HapiClient
) {

    suspend fun validateFormField(magicApiWrapper: MagicApiWrapper, params: MagicParams) =
        magicApiWrapper.fhirValidateFormField(params).also {
            logger.info(it.toString())
        }

    suspend fun mapFormField(magicApiWrapper: MagicApiWrapper, params: FhirMapParams) =
        magicApiWrapper.mapFormField(params).also {
            logger.info(it.toString())
        }

    val magicApiWrapper = MagicApiWrapper(mdrClient, hapiClient)

    route("\$validate-code") {
        get<FhirValidationParams, FormFieldFhirValidation>(
            info("validate a string content of a specified field using its FHIR annotation in the MDR")
        ) { params ->
            respond(validateFormField(magicApiWrapper, params))
        }
    }

    route("\$translate") {
        get<FhirMapParams, FormFieldCodeMapping>(
            info("get a mapping on a field value that has TS annotations in the MDR (and validate the field in the process)")
        ) { params ->
            respond(mapFormField(magicApiWrapper, params))
        }
    }
}

