package com.wangzan101.mika.core.search

import com.fasterxml.jackson.core.JsonProcessingException
import com.firecrawl.client.FirecrawlClient
import com.firecrawl.errors.FirecrawlException
import com.firecrawl.models.SearchOptions
import com.wangzan101.mika.core.net.NetProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Firecrawl 搜索:用官方 Java SDK(com.firecrawl:firecrawl-java)调 `POST https://api.firecrawl.dev/v2/search`,`Authorization: Bearer`,
 * 请求体 `{"query","limit","integration":"_mika"}`。不带 scrapeOptions,只要搜索结果本身(不抓正文、不另收费);
 * 返回 `data.web[]`,每条有 title、url、description。SDK 是阻塞调用,放到 IO 线程;它自带的重试关掉,失败照常交给模型。
 */
class FirecrawlSearch(
    apiKey: String,
    client: OkHttpClient = NetProxy.shared.client(),
    url: HttpUrl = URL.toHttpUrl(),
) : WebSearch {
    private val firecrawl = FirecrawlClient.builder()
        .apiKey(apiKey.trim())
        .apiUrl(url.toString())
        .httpClient(client)
        .maxRetries(0)
        .build()

    override suspend fun search(query: String, count: Int): WebSearchResult {
        val n = count.coerceIn(1, WebSearch.MAX_COUNT)
        val options = SearchOptions.builder().limit(n).integration(INTEGRATION).build()
        return try {
            val web = runInterruptible(Dispatchers.IO) { firecrawl.search(query, options) }.web
                ?: return WebSearchResult.Failure("invalid_response", "The Firecrawl response has no web results array.")
            WebSearchResult.Success(web.mapNotNull { o ->
                SearchHttp.hit(text(o, "title"), text(o, "url"), text(o, "description"))
            }.take(n))
        } catch (e: FirecrawlException) {
            failure(e)
        } catch (e: IllegalArgumentException) {
            WebSearchResult.Failure("invalid_response", "The Firecrawl response could not be read.")
        }
    }

    private fun failure(e: FirecrawlException): WebSearchResult.Failure = when (e.statusCode) {
        401, 403 -> WebSearchResult.Failure("authentication_failed", "The search service rejected the configured API key.")
        402 -> WebSearchResult.Failure("quota_exhausted", "The search service reports that its quota or credits are exhausted.")
        429 -> WebSearchResult.Failure("rate_limited", "The search service is rate limiting requests; retry later or use the browser.")
        0 -> if (e.cause is JsonProcessingException) {
            WebSearchResult.Failure("invalid_response", "The search service did not return a JSON object.")
        } else {
            WebSearchResult.Failure("offline", "The search service could not be reached; check the network and proxy.")
        }
        else -> WebSearchResult.Failure("service_unavailable", "The search service returned HTTP ${e.statusCode}.")
    }

    private fun text(o: Map<String, Any?>, name: String): String = (o[name] as? String)?.trim().orEmpty()

    companion object {
        const val URL = "https://api.firecrawl.dev"
        /** Firecrawl 用它区分请求来自哪个集成;每次请求都带。 */
        const val INTEGRATION = "_mika"
    }
}
