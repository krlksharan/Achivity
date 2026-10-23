package com.shield.adultblocker

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors

object HostsRepository {
    private const val GITHUB_HOSTS_URL =
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts"
    private const val CACHE_FILE_NAME = "blocked_hosts_cache.txt"

    private val blockedDomains = Collections.synchronizedSet(HashSet<String>(90000))

    private val blockedKeywords = listOf(
        "pornhub", "xvideos", "xnxx", "xhamster", "redtube", "youporn",
        "spankbang", "eporner", "brazzers", "onlyfans", "chaturbate",
        "stripchat", "rule34", "hentai", "nhentai", "hqporner", "beeg",
        "porn", "xxx", "nsfw", "sexvideo", "nudity", "camgirl"
    )

    fun initialize(context: Context, onComplete: ((Int) -> Unit)? = null) {
        Executors.newSingleThreadExecutor().execute {
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)

            if (cacheFile.exists()) {
                loadFromFile(cacheFile)
            }

            try {
                val url = URL(GITHUB_HOSTS_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 15000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val tempSet = HashSet<String>(90000)
                    BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        cacheFile.bufferedWriter().use { writer ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                val trimmed = line!!.trim()
                                if (trimmed.startsWith("0.0.0.0 ")) {
                                    val domain = trimmed.substringAfter("0.0.0.0 ").trim().lowercase()
                                    if (domain.isNotEmpty() && domain != "0.0.0.0") {
                                        tempSet.add(domain)
                                        writer.write("$domain\n")
                                    }
                                }
                            }
                        }
                    }
                    synchronized(blockedDomains) {
                        blockedDomains.clear()
                        blockedDomains.addAll(tempSet)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onComplete?.invoke(blockedDomains.size)
        }
    }

    private fun loadFromFile(file: File) {
        try {
            file.forEachLine { line ->
                val domain = line.trim()
                if (domain.isNotEmpty()) blockedDomains.add(domain)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isUrlOrQueryBlocked(input: String): Boolean {
        val clean = input.trim().lowercase()
        if (clean.isEmpty()) return false

        val host = extractDomain(clean)
        if (host.isNotEmpty()) {
            if (blockedDomains.contains(host) || blockedDomains.contains("www.$host")) {
                return true
            }
            val parts = host.split(".")
            if (parts.size >= 2) {
                val rootDomain = "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
                if (blockedDomains.contains(rootDomain)) return true
            }
        }

        for (keyword in blockedKeywords) {
            if (Regex("\\b$keyword\\b").containsMatchIn(clean) || clean.contains(keyword)) {
                return true
            }
        }
        return false
    }

    private fun extractDomain(input: String): String {
        return try {
            val formatted = if (!input.startsWith("http")) "https://$input" else input
            val uri = URI(formatted)
            (uri.host ?: input).removePrefix("www.").lowercase()
        } catch (e: Exception) {
            input.substringBefore("/").substringBefore("?").removePrefix("www.").lowercase()
        }
    }
}
