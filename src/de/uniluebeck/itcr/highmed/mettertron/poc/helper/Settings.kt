package de.uniluebeck.itcr.highmed.mettertron.poc.helper

import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

private val settingsLogger: Logger = LoggerFactory.getLogger("Settings")

private data class ServerSettingsConf(
    val cache: CacheSettings,
    val background: BackgroundSettings,
    val mdrAttributes: MdrAttributesSettings,
    val terminology: TerminologySettings,
)

data class ServerSettings(
    val cache: CacheSettings,
    val background: BackgroundSettings,
    val mdr: MdrSettings,
    val mdrAttributes: MdrAttributesSettings,
    val terminology: TerminologySettings
) {

    companion object {
        fun fromHocon(): ServerSettings {
            return ConfigFactory.defaultApplication().getConfig("ktor.app")
                .extract<ServerSettingsConf>()
                .let { serverSettings ->
                    // Config4k doesn't support includes, hence this construction with a private utility class
                    val mdr = ConfigFactory
                        .parseString(File("resources/mdr.conf").readText())
                        .extract<MdrSettings>("mdr")
                    settingsLogger.info(serverSettings.toString())
                    ServerSettings(
                        cache = serverSettings.cache,
                        background = serverSettings.background,
                        mdr = mdr,
                        mdrAttributes = serverSettings.mdrAttributes,
                        terminology = serverSettings.terminology
                    )
                }
        }
    }


}

data class CacheSettings(
    val acceptableAgeSeconds: Long,
    val maximumElements: Int
)

data class BackgroundSettings(
    val cleanupCacheSeconds: Long,
    val autoLoginSeconds: Long
)

data class MdrSettings(
    val url: String,
    val urlPrefix: String,
    val user: String,
    val password: String,
    val clientId: String,
    val clientSecret: String,
    val grantType: String,
    val scope: String,

    ) {
    override fun toString(): String {
        return "MdrSettings(url='$url', urlPrefix='$urlPrefix', user='<secret>', password='<secret>', client_id='<secret>', client_secret='<secret>', grant_type='$grantType', scope='$scope')"
    }
}

data class TerminologySettings(val url: String)

data class MdrAttributesSettings(
    val domainCode: String,
    val fhirCsCanonical: String,
    val fhirVsCanonical: String,
    val fhirMappableToCsCanonical: String,
    val fhirCmCanonical: String
)