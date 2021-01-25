package de.uniluebeck.itcr.highmed.mettertron.poc.api.fhir

import de.uniluebeck.itcr.highmed.mettertron.poc.terminology.FhirCommunicationException
import de.uniluebeck.itcr.highmed.mettertron.poc.terminology.HapiClient
import de.uniluebeck.itcr.highmed.mettertron.poc.terminology.logger
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.codesystems.ConceptMapEquivalence
import java.util.*

data class LookupResult(
    val codeSystemName: String,
    val codeSystemVersion: String?,
    val codeDisplay: String,
    val codeDesignation: List<LookupDesignation>?,
    val codeProperties: List<LookupProperty>?
) {
    companion object {
        fun fromFhirParameters(params: Parameters) =
            LookupResult(
                codeSystemName = params.getParameter("name").toString(),
                codeSystemVersion = params.getParameter("version")?.toString(),
                codeDisplay = params.getParameter("display").toString(),
                codeDesignation = LookupDesignation.fromFhirParameters(params),
                codeProperties = LookupProperty.fromFhirParameters(params)
            )
    }

    data class LookupDesignation(
        val language: String?,
        val use: String?,
        val value: String
    ) {
        companion object {
            fun fromFhirParameters(params: Parameters): List<LookupDesignation> =
                params.parameter
                    .filter { it.name == "designation" }
                    .map { des ->
                        val language =
                            des.getMatchFirstPart("language")!!.value.toString()
                        val use = des.getMatchFirstPart("use", false).toString()
                        val value = des.getMatchFirstPart("value")!!.value.toString()
                        LookupDesignation(
                            language = language,
                            use = use,
                            value = value
                        )
                    }
        }
    }

    data class LookupProperty(
        val code: String,
        val value: String?,
        val valueType: String?,
        val description: String?
    ) {
        companion object {
            fun fromFhirParameters(params: Parameters): List<LookupProperty> =
                params.parameter
                    .filter { it.name == "property" }
                    .map { prop ->
                        val code = prop.getMatchFirstPart("code")!!.value.toString()
                        val rawValue = prop.getPartValueAsType()
                        val valueString = rawValue?.primitiveValue()
                        val valueType = prop.part.firstOrNull { it.name.startsWith("value") }?.name
                        val description = prop.getMatchFirstPart("description", false)?.value?.toString()
                        LookupProperty(
                            code = code,
                            value = valueString,
                            valueType = valueType,
                            description = description
                        )
                    }
        }
    }
}

data class MappingParameters(
    val result: Boolean,
    val message: String?,
    val match: List<MappingMatch>
) {
    data class MappingMatch(
        val equivalence: ConceptMapEquivalence,
        val conceptSystem: String,
        val conceptCode: String,
        val conceptDisplay: String,
        val matchSource: String,
        var mappingDetail: LookupResult? = null
    ) {

        suspend fun getDetail(hapiClient: HapiClient, includeFHIRDefinedProperties: Boolean = false) {
            val codeSystemProperties = hapiClient.getCodeSystemByCanonical(conceptSystem).property.map { it.code }
            val fhirDefinedCodeSystemCodes = listOf("inactive", "deprecated", "notSelectable", "parent", "child")
            mappingDetail = hapiClient.callCodeSystemOperationLookup(
                codeSystemUrl = conceptSystem,
                code = conceptCode,
                properties = when (includeFHIRDefinedProperties) {
                    false -> codeSystemProperties
                    true -> codeSystemProperties.plus(fhirDefinedCodeSystemCodes)
                }

            )
        }

        companion object {
            fun fromFhirParameters(params: Parameters) =
                params.parameter.filter { it.name == "match" }.also {
                    if (it.count() < 2)
                        throw FhirCommunicationException("there are less than two elements in the parameters")
                }.map { match ->
                    val partEquivalence: Parameters.ParametersParameterComponent =
                        match.getMatchFirstPart("equivalence")!!
                    val partConcept = match.getMatchFirstPart("concept")!!.value as Coding
                    val partSource = match.getMatchFirstPart("source", false)
                    MappingMatch(
                        equivalence = ConceptMapEquivalence.valueOf(partEquivalence.value.toString()
                            .uppercase(Locale.getDefault())),
                        conceptSystem = partConcept.system,
                        conceptCode = partConcept.code,
                        conceptDisplay = partConcept.display,
                        matchSource = partSource?.value.toString()
                    )
                }
        }
    }

    companion object {
        fun fromFhirParameters(params: Parameters) = MappingParameters(
            result = params.getParameterBool("result"),
            message = params.getParameter("message")?.toString(),
            match = MappingMatch.fromFhirParameters(params)
        ).also {
            logger.info("mapping converted result: $it")
        }
    }
}

fun Parameters.ParametersParameterComponent.getMatchFirstPart(param: String, throwIfNull: Boolean = true) =
    this.part.firstOrNull { it.name == param }.let {
        if (it != null || !throwIfNull) it else
            throw FhirCommunicationException("parameter $param is not present")
    }

fun Parameters.ParametersParameterComponent.getPartValueAsType(): Type? {
    return this.part.firstOrNull { it.name.startsWith("value") }
        ?.let(Parameters.ParametersParameterComponent::paramAsType)
}

fun Parameters.ParametersParameterComponent.paramAsType() =
    when (name) {
        "valueCode" -> value as CodeType
        "valueCoding" -> value as Coding
        "valueString" -> value as StringType
        "valueInteger" -> value as IntegerType
        "valueBoolean" -> value as BooleanType
        "valueDateTime" -> value as DateTimeType
        "valueDecimal" -> value as DecimalType
        "value" -> value as StringType
        else -> throw TypeCastException("the value at ${this.name} is not in a recognised format")
    }

