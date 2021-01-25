package de.uniluebeck.itcr.highmed.mettertron.poc.api.mdr

import com.papsign.ktor.openapigen.annotations.Response
import com.papsign.ktor.openapigen.annotations.properties.description.Description

interface MdrHateoasResponse {

    @Description("the links that relate to this response")
    val links: List<Link>

    class Localised : HashMap<String, Localised.Content>() {
        data class Content(
            val name: String,
            val description: String?
        )
    }

    fun getSelfLink() = this.links.find { it.rel == "self" }?.href

    @Response("A link within the MDR")
    data class Link(
        @Description("the relation of the link to the requested resource") val rel: String,
        @Description("the link itself") val href: String,
        @Description("the language the linked resource is in") val hreflang: String?,
        val media: String?,
        @Description("the title of the linked resource") val title: String?,
        val type: String?,
        val deprecation: String?
    )
}

interface Content

interface EmbeddableContent : Content

interface ContentWithLink : Content {
    val links: List<MdrHateoasResponse.Link>?
    fun getSelfLink(): String? = this.links?.find { it.rel == "self" }?.href
}

@Response("a response from the MDR 1..n elements as list and links")
interface ContentResponse : MdrHateoasResponse {
    @Description("the content of the response as a list")
    val content: List<Content>
}

interface EmbeddedResponse : MdrHateoasResponse {
    val embedded: EmbeddableContent?
}

@Response("a response from the MDR with a single element and links")
interface SingleContentResponse : MdrHateoasResponse {
    @Description("the content of the response, a single element")
    val content: Content
}

@Response("a response from the MDR with only links")
interface LinkResponse : MdrHateoasResponse

