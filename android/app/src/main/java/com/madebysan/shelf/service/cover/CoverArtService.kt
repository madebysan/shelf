package com.madebysan.shelf.service.cover

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverArtService @Inject constructor() {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Tries iTunes → Google Books → Open Library to find cover art.
     * Returns the image URL or null.
     */
    suspend fun findCoverArt(title: String, author: String?): String? {
        return tryItunes(title, author)
            ?: tryGoogleBooks(title, author)
            ?: tryOpenLibrary(title, author)
    }

    private suspend fun tryItunes(title: String, author: String?): String? = withContext(Dispatchers.IO) {
        try {
            val query = buildString {
                append(title)
                if (author != null) append(" $author")
            }
            val url = "https://itunes.apple.com/search?term=${encode(query)}&media=audiobook&limit=1"
            val body = fetch(url) ?: return@withContext null
            val root = json.parseToJsonElement(body).jsonObject
            val results = root["results"]?.jsonArray ?: return@withContext null
            if (results.isEmpty()) return@withContext null
            val artworkUrl = results[0].jsonObject["artworkUrl100"]?.jsonPrimitive?.content
            // Get higher resolution
            artworkUrl?.replace("100x100", "600x600")
        } catch (_: Exception) { null }
    }

    private suspend fun tryGoogleBooks(title: String, author: String?): String? = withContext(Dispatchers.IO) {
        try {
            val query = buildString {
                append("intitle:${encode(title)}")
                if (author != null) append("+inauthor:${encode(author)}")
            }
            val url = "https://www.googleapis.com/books/v1/volumes?q=$query&maxResults=1"
            val body = fetch(url) ?: return@withContext null
            val root = json.parseToJsonElement(body).jsonObject
            val items = root["items"]?.jsonArray ?: return@withContext null
            if (items.isEmpty()) return@withContext null
            val imageLinks = items[0].jsonObject["volumeInfo"]?.jsonObject?.get("imageLinks")?.jsonObject
            val thumbnail = imageLinks?.get("thumbnail")?.jsonPrimitive?.content
            // Use https and remove edge curl
            thumbnail?.replace("http://", "https://")?.replace("&edge=curl", "")
        } catch (_: Exception) { null }
    }

    private suspend fun tryOpenLibrary(title: String, author: String?): String? = withContext(Dispatchers.IO) {
        try {
            val query = buildString {
                append("title=${encode(title)}")
                if (author != null) append("&author=${encode(author)}")
            }
            val url = "https://openlibrary.org/search.json?$query&limit=1&fields=cover_i"
            val body = fetch(url) ?: return@withContext null
            val root = json.parseToJsonElement(body).jsonObject
            val docs = root["docs"]?.jsonArray ?: return@withContext null
            if (docs.isEmpty()) return@withContext null
            val coverId = docs[0].jsonObject["cover_i"]?.jsonPrimitive?.content ?: return@withContext null
            "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
        } catch (_: Exception) { null }
    }

    private fun fetch(url: String): String? {
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }

    private fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
