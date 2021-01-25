package de.uniluebeck.itcr.highmed.mettertron.poc.api.mdr

import com.google.gson.annotations.SerializedName
import java.util.*

data class MdrApiFolder(
    val id: String,
    val caption: MdrHateoasResponse.Localised,
    val modificationTime: Long,
    val parent: String?,
    override val links: List<MdrHateoasResponse.Link>,
) : ContentWithLink

data class MdrApiFolders(
    override val links: List<MdrHateoasResponse.Link>,
    override val content: List<MdrApiFolder>
) : ContentResponse

data class MdrApiFolderDetail(
    override val links: List<MdrHateoasResponse.Link>,
    val id: String,
    val caption: MdrHateoasResponse.Localised,
    val parent: String?,
    val modificationTime: Long,
    @SerializedName("_embedded") override val embedded: FolderEmbedded?,
) : EmbeddedResponse

data class FolderEmbedded(
    val forms: List<EmbeddedForm>?
) : EmbeddableContent

data class EmbeddedForm(
    val id: String,
    val caption: MdrHateoasResponse.Localised,
    val version: Long,
    val systemUrl: String?,
    val approvalStatus: String?, //ApprovalStatus,
    val modificationTime: Long,
    override val links: List<MdrHateoasResponse.Link>,
    val sections: List<MdrApiSection>? = null,
    val fields: List<MdrApiField>? = null,
    val validFrom: String? = null,
    val validUntil: String? = null,
    val folderId: String? = null,
) : ContentWithLink

data class MdrApiSection(
    val code: String,
    val caption: MdrHateoasResponse.Localised,
    val fields: List<MdrApiField>?,
    val sections: List<MdrApiSection>?,
    val column: Int?,
    val row: Int?,
    val columnSpan: Int?,
    val rowSpan: Int?,
    val version: Long?,
    val validFrom: String?,
    val validUntil: String?,
    val multiValue: Boolean?,
    val approvalStatus: ApprovalStatus,
    val modificationTime: Long?
)

data class MdrApiField(
    val item: MdrApiFieldItem,
    val column: Int?,
    val row: Int?,
    val columnSpan: Int?,
    val rowSpan: Int?,
    val modificationTime: Long
)

data class MdrApiFieldItem(
    val id: String,
    val itemType: String?, //ItemType?,
    val caption: MdrHateoasResponse.Localised,
    val unit: String?,
    val visible: Boolean,
    val mandatory: Boolean,
    val calculated: Boolean,
    val series: Boolean,
    val definition: String?,
    val version: Long,
    val validFrom: String?,
    val validUntil: String?,
    val systemUrl: String?,
    val referenceRange: String?,
    val source: String?,
    val validator: String?,
    val linkedForm: String?,
    val linkedSection: String?,
    val approvalStatus: ApprovalStatus,
    val modificationTime: Long?
)

data class MdrApiIndex(override val links: List<MdrHateoasResponse.Link>) : LinkResponse

data class MdrApiForms(
    override val links: List<MdrHateoasResponse.Link>,
    override val content: List<EmbeddedForm>
) : ContentResponse

/*data class MdrApiAttributeQuery(
    @SerializedName("_links") override val links: List<MdrHateoasResponse.Link>,
    @SerializedName("_embedded") override val embedded: MdrApiAttributes
) : EmbeddedResponse*/

data class MdrApiAttributeQuery(
    override val links: List<MdrHateoasResponse.Link>,
    override val content: List<MdrApiAttribute>
) : ContentResponse

data class MdrApiAttribute(
    val domain: String,
    val attribute: String,
    val value: MdrApiAttributeValue,
    override val links: List<MdrHateoasResponse.Link>
) : ContentWithLink

sealed class MdrApiAttributeValue {
    data class MdrApiAttributeStringValue(
        val value: String
    ) : MdrApiAttributeValue()

    data class MdrApiAttributeMultiValue(
        val values: List<String>
    ) : MdrApiAttributeValue()
}



@Suppress("unused")
enum class ApprovalStatus {
    DRAFT, IN_APPROVAL, APPROVED, REJECTED
}

@Suppress("unused")
enum class ItemType {
    STRING, DATE, TIME, PRECISION_DATE, NUMERIC, INTEGER, BOOLEAN, FILE, SINGLE_ELEMENT, MULTIPLE_ELEMENTS, FUNCTION, TEXT
}