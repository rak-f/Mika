package com.wangzan101.mika.core.search

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 对着真实的 Firecrawl 搜一次,看请求和解析在真服务上也对。没设 FIRECRAWL_API_KEY 时跳过(CI 不设)。
 *
 *   FIRECRAWL_API_KEY=fc-... ./gradlew :core:test --tests '*FirecrawlLiveSearch*' --rerun -i
 */
class FirecrawlLiveSearch {
    @Test fun search() = runBlocking {
        val key = System.getenv("FIRECRAWL_API_KEY")
        assumeTrue("FIRECRAWL_API_KEY not set", !key.isNullOrBlank())
        val result = FirecrawlSearch(key!!, OkHttpClient()).search("Android accessibility service automation", 3)
        println(result)
        assertTrue(result.toString(), result is WebSearchResult.Success && result.hits.isNotEmpty())
    }
}
