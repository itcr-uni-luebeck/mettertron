package de.uniluebeck.itcr.highmed.mettertron.poc.api.magic

import com.papsign.ktor.openapigen.annotations.Response
import com.papsign.ktor.openapigen.annotations.properties.description.Description
import de.uniluebeck.itcr.highmed.mettertron.poc.api.fhir.MappingParameters

@Response("result of the validation call for the specified form field and coded content")
data class FormFieldFhirValidation(
    @Description("the id of the form") override val formId: String,
    @Description("the code of the form field (not the coded content)") override val fieldCode: String,
    @Description("the coded content of the form field (not the code in the form)")override val fieldContentCode: String,
    @Description("the validity status of the coded value, for computers") val validationStatus: ValidationStatus,
    @Description("any validation messages passed along by the FHIR server, for humans") val validationMessages: List<String>
) : FormFieldMagic

@Response("result of a mapping via the MDR to another code system")
data class FormFieldCodeMapping(
    @Description("the id of the form") override val formId: String,
    @Description("the code of the form field (not the coded content)") override val fieldCode: String,
    @Description("the coded content of the form field (not the code in the form)")override val fieldContentCode: String,
    @Description("mapping success") val mappingSuccess: Boolean,
    @Description("mapping messages") val mappingMessages: List<String>,
    @Description("the mappings of the provided code") val mappings: List<MappingParameters.MappingMatch>
) : FormFieldMagic

interface FormFieldMagic {
    val formId: String
    val fieldCode: String
    val fieldContentCode: String
}

@Suppress("unused")
enum class ValidationStatus(val description: String) {
    VALID("the code is valid for this field"),
    NOT_IN_VALUESET("the code is not in the valueset, but is contained in (one of) the specified code system(s)"),
    INVALID("the code is neither contained in the valueset nor in (one of) the specified code system(s)")
}