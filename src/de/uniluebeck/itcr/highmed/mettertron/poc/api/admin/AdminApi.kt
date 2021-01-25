package de.uniluebeck.itcr.highmed.mettertron.poc.api.admin

import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import de.uniluebeck.itcr.highmed.mettertron.poc.api.admin.Status.Companion.fromMdrClientStatus
import de.uniluebeck.itcr.highmed.mettertron.poc.api.mdr.LinkResponse
import de.uniluebeck.itcr.highmed.mettertron.poc.api.mdr.MdrApiIndex
import de.uniluebeck.itcr.highmed.mettertron.poc.mdr.MdrClient
import de.uniluebeck.itcr.highmed.mettertron.poc.mdr.MdrCommunicationException

data class LoginMdrParameters(
    @QueryParam("force the login to the MDR, even if the token is not exipred") val force: Boolean? = false
)

fun NormalOpenAPIRoute.adminApi(mdrClient: MdrClient) {


    route("/status") {
        get<Unit, Status>(
            info("Get the status of the system"),
            example = Status.example()
        ) {
            respond(fromMdrClientStatus(mdrClient))
        }
    }
    route("login-mdr") {
        get<LoginMdrParameters, Status>(
            info("login to the MDR using the configured credentials"),
            example = Status.example(),
        ) {
            try {
                mdrClient.login(it.force)
                respond(fromMdrClientStatus(mdrClient))
            } catch (e: Exception) {
                throw MdrCommunicationException("error when logging in", e)
            }
        }
        route("get-token") {
            get<Unit, MdrLoginToken>(
                info("get the login token (encapsulate the MDR login)"),
                example = MdrLoginToken.example()
            ) {
                mdrClient.login(false)
                respond(MdrLoginToken.fromMdrClientStatus(mdrClient))
            }
        }
    }
    route("/mdr-index") {
        get<Unit, LinkResponse>(
            info("get the link index of the MDR")
        ) {
            respond(mdrClient.executeHateoasLinks<MdrApiIndex>("/"))
        }
    }

    route("/caches") {
        val cacheLayer = mdrClient.cacheLayer

        route("status") {
            get<Unit, CacheStatus>(
                info("check the status of the cache")
            ) {
                respond(CacheStatus.fromCacheLayer(cacheLayer))
            }
        }

        route("clean") {
            post<Unit, CacheStatus, Unit>(
                info("clean the cache, removing stale elements")
            ) { _, _ ->
                cacheLayer.cleanCache("manual")
                respond(CacheStatus.fromCacheLayer(cacheLayer))
            }
        }

        route("clear") {
            post<Unit, CacheStatus, Unit>(
                info("clear all elements from the cache")
            ) { _, _ ->
                cacheLayer.clearCache()
                respond(CacheStatus.fromCacheLayer(cacheLayer))
            }
        }
    }
}