package com.shield.adultblocker

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object HostsRepository {

    private const val HOSTS_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts"
    private const val CACHE_FILE_NAME = "blocked_hosts_cache.txt"

    private val blockedDomains: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile
    private var isInitialized = false

    // Core high-priority domains loaded immediately in memory
    private val coreBlockedDomains = listOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
        "youporn.com", "spankbang.com", "eporner.com", "brazzers.com", "onlyfans.com",
        "chaturbate.com", "stripchat.com", "cam4.com", "livejasmin.com", "bongacams.com",
        "rule34.xxx", "nhentai.net", "hanime.tv", "motherless.com", "txxx.com",
        "hqporner.com", "beeg.com", "daftsex.com", "fuq.com", "porn.com"
    )

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        blockedDomains.addAll(coreBlockedDomains)

        val appContext = context.applicationContext
        executor.execute {
            val cacheFile = File(appContext.filesDir, CACHE_FILE_NAME)
            if (cacheFile.exists() && cacheFile.length() > 1024) {
                loadFromCache(cacheFile)
            } else {
                downloadAndCacheHosts(cacheFile)
            }
        }
    }

    /**
     * Checks if a URL, domain, or search text matches any blocked adult domain in <0.01ms.
     */
    fun isBlocked(input: String): Boolean {
        val clean = input.lowercase().trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removePrefix("m.")

        val host = clean.substringBefore("/").substringBefore("?").substringBefore(" ")
        if (host.isEmpty()) return false

        // Direct O(1) match
        if (blockedDomains.contains(host)) return true

        // Check parent domain if subdomain is used (e.g., es.pornhub.com -> pornhub.com)
        val parts = host.split(".")
        if (parts.size >= 2) {
            val rootDomain = "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
            if (blockedDomains.contains(rootDomain)) return true
        }

        return false
    }

    // Alias in case any other class calls isDomainBlocked
    fun isDomainBlocked(domain: String): Boolean = isBlocked(domain)

    private fun loadFromCache(file: File) {
        try {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val d = line.trim()
                    if (d.isNotEmpty()) blockedDomains.add(d)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun downloadAndCacheHosts(cacheFile: File) {
        try {
            val url = URL(HOSTS_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                requestMethod = "GET"
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val tempSet = HashSet<String>(80000)
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line!!.trim()
                        if (trimmed.startsWith("0.0.0.0 ")) {
                            val domain = trimmed.substringAfter("0.0.0.0 ").substringBefore("#").trim().lowercase()
                            if (domain.isNotEmpty() && domain != "0.0.0.0" && !domain.startsWith("www.")) {
                                tempSet.add(domain)
                            }
                        }
                    }
                }
                if (tempSet.isNotEmpty()) {
                    blockedDomains.addAll(tempSet)
                    cacheFile.bufferedWriter().use { writer ->
                        tempSet.forEach { d ->
                            writer.write(d)
                            writer.newLine()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
