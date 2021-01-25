package de.uniluebeck.itcr.highmed.mettertron.poc.api.mdr

import com.papsign.ktor.openapigen.annotations.Response
import com.papsign.ktor.openapigen.annotations.properties.description.Description
import com.papsign.ktor.openapigen.annotations.type.`object`.example.ExampleProvider
import com.papsign.ktor.openapigen.annotations.type.`object`.example.WithExample

@Response("A folder in the MDR")
data class MdrFolder(
    @Description("the id of the folder") val id: String,
    @Description("the name of the folder") val text: LocalisedString,
    @Description("the link to the folder definition within the MDR") val link: String?,
    @Transient private val api: MdrApiFolder? = null
) {
    companion object {
        fun fromApi(api: MdrApiFolder) =
            MdrFolder(
                id = api.id,
                text = LocalisedString.namesFromApi(api.caption),
                link = api.getSelfLink(),
                api = api
            )
    }
}

@Response("a detailed look at a folder in the MDR")
data class MdrFolderDetail(
    val id: String,
    val caption: LocalisedString?,
    //val link: String?,
    val embeddedForms: List<MdrFolderEmbeddedForm>?,
    @Transient private val api: MdrApiFolderDetail? = null
) {
    companion object {
        fun fromApi(api: MdrApiFolderDetail): MdrFolderDetail {
            return MdrFolderDetail(
                id = api.id,
                caption = LocalisedString.namesFromApi(api.caption),
                //link = mettertron.poc.api.getSelfLink(),
                embeddedForms = api.embedded?.forms?.map { MdrFolderEmbeddedForm.fromApi(it) },
                api = api
            )
        }
    }
}

data class MdrFolderEmbeddedForm(
    val id: String,
    val caption: LocalisedString?,
    val version: Long?,
    val systemUrl: String?,
    val approvalStatus: String?, //ApprovalStatus,
    //val link: String?
    @Transient private val api: EmbeddedForm? = null
) {
    companion object {
        fun fromApi(api: EmbeddedForm) =
            MdrFolderEmbeddedForm(
                id = api.id,
                caption = LocalisedString.namesFromApi(api.caption),
                version = api.version,
                systemUrl = api.systemUrl,
                approvalStatus = api.approvalStatus,
                api = api
                //link = mettertron.poc.api.getSelfLink()
            )
    }
}

@Suppress("unused")
@Response("a localised string within the MDR (in multiple languages)")
@WithExample(LocalisedString.LocalisedStringExampleProvider::class)
open class LocalisedString(
    @Description("a map of language codes to a string in that language") val translations: Map<String, String?>
) {

    /*@WithExample
    class LocalisedStringExample : LocalisedString(mapOf("de" to "deutscher Wert", "en" to "english value")) {
        companion object : ExampleProvider<LocalisedStringExample> {
            override val example = LocalisedStringExample()
        }
    }*/

    object LocalisedStringExampleProvider : ExampleProvider<LocalisedString> {
        override val example: LocalisedString
            get() = LocalisedString(mapOf("de" to "deutscher Name", "en" to "english name"))
    }

    companion object {
        fun namesFromApi(loc: MdrHateoasResponse.Localised) =
            LocalisedString(loc.mapValues { it.value.name })

        fun descriptionsFromApi(loc: MdrHateoasResponse.Localised) =
            LocalisedString(loc.mapValues { it.value.description })
    }
}

@Response("the forms in the MDR")
data class MdrForms(val forms: List<MdrForm>) {
    companion object {
        fun fromApi(api: MdrApiForms): MdrForms = MdrForms(api.content.map(MdrForm.Companion::fromAPI))
    }
}

@Response("a form in the MDR")
data class MdrForm(
    val id: String,
    val caption: LocalisedString?,
    val version: Long?,
    val systemUrl: String?,
    val approvalStatus: String?, //ApprovalStatus,
    val sections: List<MdrFormSection> = emptyList(),
    val fields: List<MdrFormField> = emptyList(),
    @Transient private val api: EmbeddedForm? = null
) {
    companion object {
        fun fromAPI(api: EmbeddedForm) = MdrForm(
            id = api.id,
            caption = LocalisedString.namesFromApi(api.caption),
            version = api.version,
            systemUrl = api.systemUrl,
            approvalStatus = api.approvalStatus,
            sections = api.sections?.map(MdrFormSection.Companion::fromApi) ?: emptyList(),
            fields = api.fields?.map(MdrFormField.Companion::fromApi) ?: emptyList(),
            api = api
        )
    }
}

@Response("a section of a form in the MDR")
data class MdrFormSection(
    val code: String,
    val caption: LocalisedString?,
    val fields: List<MdrFormField>,
    @Transient private val api: MdrApiSection? = null
) {
    companion object {
        fun fromApi(api: MdrApiSection) = MdrFormSection(
            code = api.code,
            caption = LocalisedString.namesFromApi(api.caption),
            fields = api.fields?.map(MdrFormField.Companion::fromApi) ?: emptyList(),
            api = api
        )
    }
}

@Response("a field in a form in the MDR")
data class MdrFormField(
    val id: String,
    val column: Int?,
    val row: Int?,
    val itemType: String?, // ItemType?,
    val caption: LocalisedString?,
    val referenceRange: String?,
    val unit: String?,
    val visible: Boolean,
    val mandatory: Boolean,
    val definition: String?,
    val version: Long?,
    val systemUrl: String?,
    val linkedForm: String?,
    val linkedSection: String?,
    val approvalStatus: ApprovalStatus,
    @Transient private val api: MdrApiField? = null
) {
    companion object {
        fun fromApi(api: MdrApiField) =
            MdrFormField(
                id = api.item.id,
                column = api.column,
                row = api.row,
                itemType = api.item.itemType,
                caption = LocalisedString.namesFromApi(api.item.caption),
                referenceRange = api.item.referenceRange,
                unit = api.item.unit,
                visible = api.item.visible,
                mandatory = api.item.mandatory,
                definition = api.item.definition,
                version = api.item.version,
                systemUrl = api.item.systemUrl,
                linkedForm = api.item.linkedForm,
                linkedSection = api.item.linkedSection,
                approvalStatus = api.item.approvalStatus,
                api = api
            )
    }
}

sealed class MdrAttribute(
    val domain: String,
    val attribute: String
) {

    class MdrStringAttribute(domain: String, attribute: String, val value: String) : MdrAttribute(
        domain,
        attribute
    )

    class MdrMultipleAttribute(domain: String, attribute: String, val value: List<String>) : MdrAttribute(
        domain, attribute
    )

    companion object {
        fun fromApi(api: MdrApiAttribute) = when (api.value) {
            is MdrApiAttributeValue.MdrApiAttributeStringValue -> MdrStringAttribute(
                api.domain, api.attribute, api.value.value
            )
            is MdrApiAttributeValue.MdrApiAttributeMultiValue -> MdrMultipleAttribute(
                api.domain, api.attribute, api.value.values
            )
        }
        /* fun fromApi(api: MdrApiAttribute) = MdrAttribute(
                domain = api.domain,
                attribute = api.attribute,
                value = api.value
            )*/
    }
}

data class MdrFormAttributes(
    val form: List<MdrAttribute>,
    val fields: Map<String, List<MdrAttribute>>,
    val sections: Map<String, MdrFormSectionAttributes>
)

data class MdrDefinitionAttributes(
    val definition: List<MdrAttribute>
)

data class MdrFormSectionAttributes(
    val attributes: List<MdrAttribute>,
    val fieldAttributes: Map<String, List<MdrAttribute>>
)
