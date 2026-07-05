package com.cortex.app.data.remote

import com.cortex.app.data.model.SearchResult
import com.cortex.app.data.model.WebSearchConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

class WebSearchProvider {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Execute a web search using the configured provider.
     * Returns list of SearchResult (title, url, snippet).
     */
    suspend fun search(query: String, config: WebSearchConfig): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        when (config.provider) {
            com.cortex.app.data.model.SearchProvider.DISABLED -> emptyList()
            com.cortex.app.data.model.SearchProvider.DUCK_DUCK_GO -> searchDDG(query, config.maxResults)
            com.cortex.app.data.model.SearchProvider.EXA -> searchExa(query, config)
            com.cortex.app.data.model.SearchProvider.FIRECRAWL -> searchFirecrawl(query, config)
            com.cortex.app.data.model.SearchProvider.SEARXNG -> searchSearXNG(query, config)
        }
    }

    /** DuckDuckGo HTML scraping — free, no API key, works out of the box. */
    private suspend fun searchDDG(query: String, max: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val url = "https://html.duckduckgo.com/html/"
            val bodyStr = "q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&b=" + java.net.URLEncoder.encode("", "UTF-8")
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://duckduckgo.com/")
                .post(bodyStr.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val html = resp.body?.string() ?: return@withContext emptyList()
                val doc: Document = Jsoup.parse(html)
                val results = mutableListOf<SearchResult>()
                doc.select(".result").forEach { el ->
                    if (results.size >= max) return@forEach
                    val a = el.selectFirst(".result__a") ?: return@forEach
                    val title = a.text().trim()
                    val href = a.attr("href")
                    val snippetEl = el.selectFirst(".result__snippet")
                    val snippet = snippetEl?.text()?.trim() ?: ""
                    // DDG wraps URLs in /l/?uddg=...
                    val cleanUrl = extractDDGUrl(href)
                    if (title.isNotEmpty() && cleanUrl.isNotEmpty()) {
                        results.add(SearchResult(title = title, url = cleanUrl, snippet = snippet))
                    }
                }
                results
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractDDGUrl(href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        // DDG often wraps: //duckduckgo.com/l/?uddg=https%3A%2F%2F...
        val uddgMatch = Regex("uddg=([^&]+)").find(href)
        if (uddgMatch != null) {
            return runCatching { java.net.URLDecoder.decode(uddgMatch.groupValues[1], "UTF-8") }.getOrDefault(href)
        }
        if (href.startsWith("//")) return "https:$href"
        return href
    }

    /** Exa search — neural search, requires API key. */
    private suspend fun searchExa(query: String, config: WebSearchConfig): List<SearchResult> = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) return@withContext emptyList()
        try {
            val payload = """
                {"query":${kotlinx.serialization.json.JsonPrimitive(query).toString()},"numResults":${config.maxResults},"contents":{"text":{"maxCharacters":300}}}
            """.trimIndent()
            val req = Request.Builder()
                .url("https://api.exa.ai/search")
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val obj = json.parseToJsonElement(body).jsonObject
                val results = obj["results"]?.jsonArray ?: return@withContext emptyList()
                results.mapNotNull { r ->
                    val o = r.jsonObject
                    val title = o["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val url = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val text = o["text"]?.jsonPrimitive?.contentOrNull ?: ""
                    SearchResult(title = title, url = url, snippet = text.take(300))
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Firecrawl search — deep crawl + extract, requires API key. */
    private suspend fun searchFirecrawl(query: String, config: WebSearchConfig): List<SearchResult> = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) return@withContext emptyList()
        try {
            val payload = """
                {"query":${kotlinx.serialization.json.JsonPrimitive(query).toString()},"limit":${config.maxResults}}
            """.trimIndent()
            val req = Request.Builder()
                .url("https://api.firecrawl.dev/v0/search")
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val obj = json.parseToJsonElement(body).jsonObject
                val data = obj["data"]?.jsonArray ?: return@withContext emptyList()
                data.mapNotNull { r ->
                    val o = r.jsonObject
                    val title = o["title"]?.jsonPrimitive?.contentOrNull ?: o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val url = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val desc = o["description"]?.jsonPrimitive?.contentOrNull ?: ""
                    SearchResult(title = title, url = url, snippet = desc.take(300))
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** SearXNG self-hosted meta-search. User provides instance URL. */
    private suspend fun searchSearXNG(query: String, config: WebSearchConfig): List<SearchResult> = withContext(Dispatchers.IO) {
        val base = config.instanceUrl.trimEnd('/')
        if (base.isBlank()) return@withContext emptyList()
        try {
            val url = "$base/search?q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&format=json"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Cortex/1.0 Android")
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val obj = json.parseToJsonElement(body).jsonObject
                val results = obj["results"]?.jsonArray ?: return@withContext emptyList()
                results.take(config.maxResults).mapNotNull { r ->
                    val o = r.jsonObject
                    val title = o["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val urlStr = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val snippet = o["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    SearchResult(title = title, url = urlStr, snippet = snippet.take(300))
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Build a system-prompt snippet that injects search results so the model
     * can ground its answer. Optimized for clarity — the AI sees structured
     * results with numbered citations and clear instructions.
     */
    fun formatResultsForPrompt(query: String, results: List<SearchResult>): String {
        if (results.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("═══ WEB SEARCH RESULTS ═══\n")
        sb.append("Query: \"$query\"\n\n")
        results.forEachIndexed { i, r ->
            sb.append("[${i + 1}] ${r.title}\n")
            sb.append("    Source: ${r.url}\n")
            if (r.snippet.isNotBlank()) {
                sb.append("    Content: ${r.snippet}\n")
            }
            sb.append("\n")
        }
        sb.append("═══ END SEARCH RESULTS ═══\n\n")
        sb.append("Instructions: Use the above web search results to answer the user's question. ")
        sb.append("Cite sources using [1], [2], etc. format. ")
        sb.append("If the results don't contain relevant information, say so and answer from your own knowledge. ")
        sb.append("Synthesize information across multiple sources when possible.")
        return sb.toString()
    }
}
