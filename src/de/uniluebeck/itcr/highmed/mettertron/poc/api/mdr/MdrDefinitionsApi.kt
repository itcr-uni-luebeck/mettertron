package de.uniluebeck.itcr.highmed.mettertron.poc.api.mdr

import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import de.uniluebeck.itcr.highmed.mettertron.poc.mdr.MdrClient
import de.uniluebeck.itcr.highmed.mettertron.poc.mdr.MdrCommunicationException
import de.uniluebeck.itcr.highmed.mettertron.poc.mdr.MdrInvalidStateException
import de.uniluebeck.itcr.highmed.mettertron.poc.mdr.MdrNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

var log: Logger = LoggerFactory.getLogger("MdrDefinitions")

suspend fun MdrClient.getFolders(): List<MdrFolder> {
    val endpoint = this.mdrLinks?.folders
        ?: throw MdrCommunicationException("MDR links is not present - are you logged in?")
    try {
        return this
            .executeHateoas<MdrApiFolders>(endpoint, false)
            .content
            .map { MdrFolder.fromApi(it) }
    } catch (e: Exception) {
        throw MdrCommunicationException("error when reading folders", e)
    }
}

suspend fun MdrClient.getFolderById(id: String): MdrFolderDetail {
    val folders = this.getFolders()
    val find = folders.find { it.id == id }
        ?: throw MdrNotFoundException("the folder $id could not be found")
    val findLink = find.link ?: throw MdrInvalidStateException("no link in the MDR folder")
    val fromApi = this.executeHateoas<MdrApiFolderDetail>(findLink, false)
    return MdrFolderDetail.fromApi(fromApi)
}

suspend fun MdrClient.getForms(): MdrForms {
    return try {
        val apiForms = this
            .executeHateoas<MdrApiForms>("forms", true)
        val mdrForms = apiForms.let(MdrForms.Companion::fromApi)
        mdrForms
    } catch (e: Exception) {
        throw MdrCommunicationException("error when reading forms", e)
    }
}

suspend fun MdrClient.getFormById(id: String): MdrForm {
    this.getForms().forms.find { it.id == id } ?: throw MdrNotFoundException("the form $id could not be found")
    return MdrForm.fromAPI(this.executeHateoasContent("forms/form?code=$id"))
}

suspend fun MdrClient.getAttributesOfFormById(formId: String): MdrFormAttributes {
    val form = this.getFormById(formId)
    val formAttributes = this.getAttributesOnForm(formId)
    val fieldAttributes = this.getAttributesForFieldList(formId, form.fields)
    val sectionAttributes = form.sections.associateBy { it.code }
        .mapValues {
            MdrFormSectionAttributes(
                attributes = this.getSectionAttributeOnForm(formId, it.value.code),
                fieldAttributes = this.getAttributesForFieldList(formId, it.value.fields)
            )
        }
    return MdrFormAttributes(
        formAttributes,
        fieldAttributes,
        sectionAttributes
    )
}

suspend fun MdrClient.getAttributesOfDefinitionById(definitionIdentifier: String, definitionValue: String): MdrDefinitionAttributes {
    val definitionAttributes = this.getAttributesOnDefinition(definitionIdentifier, definitionValue)

    return MdrDefinitionAttributes(
        definitionAttributes
    )
}

private suspend fun MdrClient.getAttributesForFieldList(formId: String, fields: List<MdrFormField>) =
    fields.associateBy { it.id }
        .mapValues { field ->
            this.getFieldAttributeOnForm(formId, field.value.id)
        }

private suspend fun MdrClient.getAttributesOnForm(formId: String): List<MdrAttribute> =
    this.getAttributesForRoute("forms/attributes/form?code=$formId")

private suspend fun MdrClient.getAttributesOnDefinition(definitionIdentifier: String, definitionVersion: String): List<MdrAttribute> =
    this.getAttributesForRoute("definitions/attributes/definition/version?code=$definitionIdentifier&version=$definitionVersion")

private suspend fun MdrClient.getFieldAttributeOnForm(formId: String, fieldCode: String) =
    this.getAttributesForRoute("forms/attributes/field?code=$formId&fieldCode=$fieldCode")

private suspend fun MdrClient.getSectionAttributeOnForm(
    formId: String,
    sectionCode: String
): List<MdrAttribute> =
    this.getAttributesForRoute("forms/attributes/section?code=$formId&sectionCode=$sectionCode")


private suspend fun MdrClient.getAttributesForRoute(route: String): List<MdrAttribute> {
    logger.info("requesting attributes from $route")
    return this
        .executeHateoas<MdrApiAttributeQuery>(route)
        /*.also {
            println(it)
        }*/
        .content
        .map(MdrAttribute.Companion::fromApi)
}

fun NormalOpenAPIRoute.mdrDefitionsApi(
    mdrClient: MdrClient
) {
    data class IdParameters(
        @PathParam("the element ID") val id: String
    )

    route("folders") {
        get<Unit, List<MdrFolder>>(
            info("list the folders in the MDR"),
            example = listOf(
                MdrFolder(
                    "folder-id",
                    text = LocalisedString(mapOf("de" to "deutscher text", "en" to "english text")),
                    link = "http://example.org/centraxx-mdr/api/folders"
                )
            )
        ) {
            respond(mdrClient.getFolders())
        }

        route("{id}") {
            get<IdParameters, MdrFolderDetail>(
                info("inspect a folder in detail")
            ) {
                respond(mdrClient.getFolderById(it.id))
            }
        }
    }

    route("forms") {
        get<Unit, MdrForms>(
            info("list the forms in the MDR")
        ) {
            respond(mdrClient.getForms())
        }
        route("{id}") {
            get<IdParameters, MdrForm>(
                info("get a form in the MDR by its ID")
            ) {
                respond(mdrClient.getFormById(it.id))
            }
            route("attributes") {
                get<IdParameters, MdrFormAttributes>(
                    info("get the attributes of a form")
                ) {
                    respond(mdrClient.getAttributesOfFormById(it.id))
                }
            }
        }
    }
}