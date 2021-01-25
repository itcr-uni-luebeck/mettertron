package de.uniluebeck.itcr.highmed.mettertron.poc.helper

import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

val logger: Logger = LoggerFactory.getLogger("CacheLayer")

class CacheLayer(
    val acceptableCacheAgeSeconds: Long = 3 * 60,
    val maximumElements: Int = 50
) {

    val cache: MutableMap<String, CacheElement> = mutableMapOf()
    val mutex = Mutex(false)

    suspend inline fun <reified T> getFromCacheOrQuery(
        url: String,
        httpClient: () -> HttpClient,
        getBlock: HttpRequestBuilder.() -> Unit,
        deserializerBlock: (String) -> T,
    ): T {
        mutex.withLock {
            val fullUrl = HttpRequestBuilder().apply(getBlock).apply {
                this.url(url)
            }.url.buildString()
            if (checkInCacheAndNotExpired(fullUrl)) {
                val cachedElement = cache[fullUrl]!!
                logger.info(
                    "retrieved from cache for $fullUrl, valid until ${
                        cachedElement.validUntil(
                            acceptableCacheAgeSeconds
                        )
                    }"
                )
                return deserializerBlock(cachedElement.cachedElementJson)
            }
            logger.info("cache miss for $fullUrl")
            val jsonString = httpClient().get<String>(url, getBlock)
            putInCache(fullUrl, jsonString)
            return deserializerBlock(jsonString)
        }
    }

    fun checkInCacheAndNotExpired(url: String): Boolean {
        return cache.containsKey(url) && localDateTimeIsWithinTolerance(cache[url]!!.cachedAt)
    }

    private fun localDateTimeIsWithinTolerance(cachedAt: LocalDateTime) = ChronoUnit.SECONDS.between(
        cachedAt,
        LocalDateTime.now()
    ) <= acceptableCacheAgeSeconds


    fun putInCache(url: String, jsonString: String) {
        val newCacheElement = CacheElement(LocalDateTime.now(), jsonString)
        cache[url] = newCacheElement
        logger.debug("cached $newCacheElement")
        when (maximumElements) {
            -1 -> return
            else -> {
                if (cache.count() > maximumElements)
                    ejectOldest()
            }
        }
    }

    private fun ejectOldest() {
        logCache()
        cache.toList().minByOrNull { it.second.cachedAt }?.let { oldest ->
            logger.debug("ejected the oldest element, ${oldest.first}, which was cached at ${oldest.second.cachedAt}")
            cache.remove(oldest.first)
        }
    }

    enum class LogLevel {
        DEBUG, INFO
    }

    private fun logCache(level: LogLevel = LogLevel.DEBUG) {
        val logFun: (String) -> Unit = when (level) {
            LogLevel.DEBUG -> logger::debug
            LogLevel.INFO -> logger::info
        }
        when (cache.count()) {
            0 -> logFun("cache is empty")
            else -> {
                val printed = cache.toList()
                    .sortedBy { it.second.cachedAt }
                    .joinToString(separator = "\n   - ") {
                        "${it.second.cachedAt} -> ${it.first} (${
                            it.second.cachedElementJson.subSequence(
                                0,
                                50
                            )
                        }...)"
                    }
                logFun(
                    "current cache elements (${cache.count()}):\n" +
                            "   - $printed"
                )
            }
        }
    }

    suspend fun clearCache() = mutex.withLock {
        logger.info("clearing cache")
        logCache(LogLevel.INFO)
        cache.clear()
    }

    suspend fun cleanCache(fashion: String = "scheduled") {
        mutex.withLock {
            logger.debug("running $fashion cleanup task")
            logCache()
            this.cache.filter { !localDateTimeIsWithinTolerance(it.value.cachedAt) }.keys
                .let { ejectUrls ->
                    if (ejectUrls.any())
                        logger.info(
                            "ejected the following stale cache elements during the cleanup task: ${
                                ejectUrls.joinToString(
                                    ", "
                                )
                            }"
                        )
                }
            this.cache.entries.retainAll { localDateTimeIsWithinTolerance(it.value.cachedAt) }
            //this block commented out, because the put routine ejects the oldest element when space is needed
            /*if (maximumElements > 0 && this.cache.count() > maximumElements) {
                val youngestKeys = this.cache.entries
                    .sortedBy { it.value.cachedAt }
                    .reversed()
                    .take(maximumElements)
                    .also { elem ->
                        logger.info("keeping only these $maximumElements youngest entries: ${elem.joinToString(" | ") { "${it.key} - ${it.value.cachedAt}" }}")
                    }

                    .map { it.key }
                this.cache.keys.retainAll(youngestKeys)
            }*/
        }
    }

    suspend fun getUrls() = mutex.withLock { cache.keys.toList() }

    suspend fun getCount() = mutex.withLock { cache.count() }
}

data class CacheElement(
    val cachedAt: LocalDateTime,
    val cachedElementJson: String
) {
    fun validUntil(acceptableCacheAgeSeconds: Long): LocalDateTime = cachedAt.plusSeconds(acceptableCacheAgeSeconds)
}