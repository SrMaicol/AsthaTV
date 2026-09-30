package com.example.parser

import com.example.model.LiveChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * M3UParser: High-performance streaming parser for remote or local M3U/M3U8 playlists.
 * Designed for extreme efficiency:
 * - Line-by-line streaming (avoids multi-megabyte string array allocations).
 * - Fast deterministic ID generation.
 * - Persistent connection pooling for instant HTTP playlist downloads.
 */
object M3uParser {

    private val connectionPool = ConnectionPool(5, 5, TimeUnit.MINUTES)

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val extInfRegex = Regex("""#EXTINF:-?\s*([0-9]*)\s*(.*)""", RegexOption.IGNORE_CASE)
    private val attrRegex = Regex("""([a-zA-Z0-9_-]+)=(?:["']([^"']*)["']|([^, ]+))""")

    /**
     * Reads and parses an M3U playlist from a remote URL in background IO thread with streaming I/O.
     */
    suspend fun parseFromUrl(urlStr: String): List<LiveChannel> = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(urlStr)
        val channels = ArrayList<LiveChannel>(1000)

        try {
            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 IPTV/2.0")
                .header("Accept", "*/*")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Connection", "keep-alive")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body
                if (response.isSuccessful && body != null) {
                    body.byteStream().use { stream ->
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8), 16384).use { reader ->
                            parseReader(reader, cleanUrl, channels)
                        }
                    }
                } else {
                    // Fallback to standard URLConnection
                    val connection = URL(cleanUrl).openConnection()
                    connection.connectTimeout = 20000
                    connection.readTimeout = 35000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Safari/537.36")
                    connection.getInputStream().use { stream ->
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8), 16384).use { reader ->
                            parseReader(reader, cleanUrl, channels)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Secondary fallback: standard URLConnection
            try {
                val connection = URL(cleanUrl).openConnection()
                connection.connectTimeout = 20000
                connection.readTimeout = 35000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Safari/537.36")
                connection.getInputStream().use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8), 16384).use { reader ->
                        parseReader(reader, cleanUrl, channels)
                    }
                }
            } catch (_: Exception) {}
        }
        channels
    }

    /**
     * Parses an existing raw string content using non-allocating lineSequence.
     */
    suspend fun parse(content: String, baseUrl: String = ""): List<LiveChannel> = withContext(Dispatchers.IO) {
        val channels = ArrayList<LiveChannel>(500)
        
        var currentName = ""
        var currentLogo = ""
        var currentGroup = "General"
        var currentEpgId = ""
        var channelIndex = 0
        val baseHash = if (baseUrl.isNotEmpty()) baseUrl.hashCode().toString(36) else "local"

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim().removePrefix("\uFEFF")
            if (line.isNotEmpty()) {
                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    val parsed = parseExtInfLine(line)
                    currentName = parsed.name
                    currentLogo = parsed.logo
                    currentGroup = parsed.group
                    currentEpgId = parsed.epgId
                } else if (line.startsWith("#EXTGRP:", ignoreCase = true)) {
                    val groupName = line.substringAfter(":").trim()
                    if (groupName.isNotBlank()) {
                        currentGroup = groupName
                    }
                } else if (!line.startsWith("#")) {
                    val streamUrl = resolveUrl(baseUrl, line)
                    if (streamUrl.isNotBlank() && isValidStreamScheme(streamUrl)) {
                        channelIndex++
                        val finalName = if (currentName.isNotBlank()) currentName else "Canal $channelIndex"
                        channels.add(
                            LiveChannel(
                                id = "ch_${baseHash}_$channelIndex",
                                name = finalName,
                                streamUrl = streamUrl,
                                logoUrl = currentLogo,
                                group = if (currentGroup.isNotBlank()) currentGroup else "General",
                                epgId = currentEpgId
                            )
                        )
                    }
                    currentName = ""
                    currentLogo = ""
                    currentGroup = "General"
                    currentEpgId = ""
                }
            }
        }
        channels
    }

    private fun parseReader(reader: BufferedReader, baseUrl: String, channels: ArrayList<LiveChannel>) {
        var currentName = ""
        var currentLogo = ""
        var currentGroup = "General"
        var currentEpgId = ""
        var channelIndex = 0
        val baseHash = baseUrl.hashCode().toString(36)

        var rawLine = reader.readLine()
        while (rawLine != null) {
            val line = rawLine.trim().removePrefix("\uFEFF")
            if (line.isNotEmpty()) {
                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    val parsed = parseExtInfLine(line)
                    currentName = parsed.name
                    currentLogo = parsed.logo
                    currentGroup = parsed.group
                    currentEpgId = parsed.epgId
                } else if (line.startsWith("#EXTGRP:", ignoreCase = true)) {
                    val groupName = line.substringAfter(":").trim()
                    if (groupName.isNotBlank()) {
                        currentGroup = groupName
                    }
                } else if (!line.startsWith("#")) {
                    val streamUrl = resolveUrl(baseUrl, line)
                    if (streamUrl.isNotBlank() && isValidStreamScheme(streamUrl)) {
                        channelIndex++
                        val finalName = if (currentName.isNotBlank()) currentName else "Canal $channelIndex"
                        channels.add(
                            LiveChannel(
                                id = "ch_${baseHash}_$channelIndex",
                                name = finalName,
                                streamUrl = streamUrl,
                                logoUrl = currentLogo,
                                group = if (currentGroup.isNotBlank()) currentGroup else "General",
                                epgId = currentEpgId
                            )
                        )
                    }
                    currentName = ""
                    currentLogo = ""
                    currentGroup = "General"
                    currentEpgId = ""
                }
            }
            rawLine = reader.readLine()
        }
    }

    private data class ExtInfResult(
        val name: String,
        val logo: String,
        val group: String,
        val epgId: String
    )

    private fun parseExtInfLine(line: String): ExtInfResult {
        val match = extInfRegex.find(line)
        var name = ""
        var logo = ""
        var group = "General"
        var epgId = ""

        if (match != null) {
            val metadataPart = match.groupValues[2]
            val attributes = HashMap<String, String>(8)

            attrRegex.findAll(metadataPart).forEach { attrMatch ->
                val key = attrMatch.groupValues[1].lowercase()
                val value = attrMatch.groupValues[2].ifEmpty { attrMatch.groupValues[3] }
                attributes[key] = value
            }

            logo = attributes["tvg-logo"] ?: attributes["logo"] ?: attributes["tvg_logo"] ?: ""
            group = attributes["group-title"] ?: attributes["group"] ?: attributes["group_title"] ?: attributes["tvg-group"] ?: "General"
            epgId = attributes["tvg-id"] ?: attributes["tvg-name"] ?: attributes["id"] ?: ""

            val commaIndex = metadataPart.lastIndexOf(',')
            name = if (commaIndex != -1 && commaIndex < metadataPart.length - 1) {
                metadataPart.substring(commaIndex + 1).trim()
            } else {
                attributes["tvg-name"] ?: "Canal en Vivo"
            }
        }

        return ExtInfResult(name = name, logo = logo, group = group, epgId = epgId)
    }

    private fun isValidStreamScheme(url: String): Boolean {
        return url.startsWith("http://", ignoreCase = true) ||
               url.startsWith("https://", ignoreCase = true) ||
               url.startsWith("rtmp://", ignoreCase = true) ||
               url.startsWith("rtsp://", ignoreCase = true) ||
               url.startsWith("mms://", ignoreCase = true) ||
               url.startsWith("udp://", ignoreCase = true)
    }

    private fun sanitizeUrl(urlStr: String): String {
        var clean = urlStr.trim()
        if (clean.startsWith("//")) {
            clean = "https:$clean"
        } else if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true) && !clean.startsWith("file://", ignoreCase = true)) {
            clean = "http://$clean"
        }
        return clean
    }

    private fun resolveUrl(baseUrl: String, streamPath: String): String {
        val trimmed = streamPath.trim()
        if (isValidStreamScheme(trimmed)) {
            return trimmed
        }

        if (baseUrl.isNotBlank() && (baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))) {
            return try {
                val uri = URI.create(baseUrl)
                uri.resolve(trimmed).toString()
            } catch (_: Exception) {
                trimmed
            }
        }
        return trimmed
    }
}
