package io.github.nexalloy

import com.google.gson.JsonParser
import io.github.nexalloy.morphe.pixiv.FreePopularSearch
import io.github.nexalloy.morphe.pixiv.PopularPicksFeed
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FreePopularSearchTest {
    private val work = ArrayDeque<Runnable>()
    private val feed = PopularPicksFeed(Executor { work.add(it) })
    private var minimum = 0
    private val search = FreePopularSearch(requireNotNull(javaClass.classLoader), { minimum }, feed)
    private val url = "https://app-api.pixiv.net/v1/search/illust?word=blue%20sky&sort=popular_desc&search_target=partial_match_for_tags&search_ai_type=0"
    private val requests = mutableListOf<Request>()
    private val artwork = """{"illusts":[{"id":42,"total_bookmarks":9000,"title":"test","meta_pages":[{"image_urls":{"original":"image"}}]}]}"""

    private fun run(url: String = this.url, respond: (Request) -> Pair<Int, String>): Response {
        val request = Request.Builder().url(url).header("Authorization", "Bearer test-token").build()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val next = chain.request()
            requests.add(next)
            val (code, json) = respond(next)
            Response.Builder().request(next).protocol(Protocol.HTTP_1_1).code(code).message("response")
                .header("Content-Length", json.length.toString())
                .body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val wrapped = search.wrapClient(client) { native, next -> (native as OkHttpClient).newCall(next as Request) } as OkHttpClient
        return wrapped.newCall(request).execute()
    }

    @Test
    fun `preview retains complete artwork data without a fabricated date range`() {
        val result = run { 200 to artwork }
        val request = requests.single()
        assertEquals("/v1/search/popular-preview/illust", request.url.encodedPath)
        assertEquals("blue sky", request.url.queryParameter("word"))
        assertEquals("0", request.url.queryParameter("search_ai_type"))
        assertEquals("Bearer test-token", request.header("Authorization"))
        assertNull(request.url.queryParameter("start_date"))
        assertNull(request.url.queryParameter("end_date"))
        assertNull(request.url.queryParameter("sort"))
        assertNull(result.header("Content-Length"))
        val body = JsonParser.parseString(result.body!!.string()).asJsonObject
        assertEquals(JsonParser.parseString(artwork).asJsonObject["illusts"], body["illusts"])
        assertFalse(body["next_url"].asString.contains("test-token"))
    }

    @Test
    fun `prefetch uses original client authentication and normal search pagination`() {
        val first = run { request ->
            200 to if (request.url.encodedPath.contains("popular-preview")) artwork
            else """{"illusts":[{"id":99,"total_bookmarks":8000}],"next_url":null}"""
        }
        val next = JsonParser.parseString(first.body!!.string()).asJsonObject["next_url"].asString
        work.removeFirst().run()
        assertEquals(2, requests.size)
        assertEquals("/v1/search/illust", requests.last().url.encodedPath)
        assertEquals("date_desc", requests.last().url.queryParameter("sort"))
        assertEquals("Bearer test-token", requests.last().header("Authorization"))
        val result = run(next) { error("Prefetch should serve the page from cache") }
        assertEquals(99, JsonParser.parseString(result.body!!.string()).asJsonObject.getAsJsonArray("illusts")[0].asJsonObject["id"].asInt)
    }

    @Test
    fun `normal sorting and unrelated requests remain unchanged`() {
        for (other in listOf(url.replace("popular_desc", "date_desc"), url.replace("app-api.pixiv.net", "example.com"), url.replace("/illust?", "/user?"))) {
            assertFalse(search.matches(Request.Builder().url(other).build()))
            assertEquals("{}", run(other) { 200 to "{}" }.body!!.string())
            assertEquals(other, requests.last().url.toString())
        }
    }

    @Test
    fun `paid filters and gender rankings keep the original route`() {
        for (query in listOf("&bookmark_num_min=100", "&content_type=manga", "&lang=en", "&tool=Photoshop", "&include_potential_violation_works=true")) {
            assertFalse(search.matches(Request.Builder().url(url + query).build()))
        }
        assertFalse(search.matches(Request.Builder().url(url.replace("popular_desc", "popular_male_desc")).build()))
    }

    @Test
    fun `default app parameters still select popular picks`() {
        val defaults = "$url&content_type=illust_and_manga_and_ugoira&include_potential_violation_works=false&filter=for_android&include_translated_tag_results=true&merge_plain_keyword_results=true"
        assertTrue(search.matches(Request.Builder().url(defaults).build()))
        run(defaults) { 200 to artwork }.close()
        assertEquals("for_android", requests.single().url.queryParameter("filter"))
    }

    @Test
    fun `server errors preserve status and body`() {
        val error = """{"error":{"message":"rate limited"}}"""
        val result = run { 429 to error }
        assertEquals(429, result.code)
        assertEquals(error, result.body!!.string())
        assertEquals(1, requests.size)
    }

    @Test
    fun `network failures propagate without wrapping or advancing pagination`() {
        val failure = IOException("connection lost")
        assertSame(failure, assertFailsWith<IOException> { run { throw failure } })
        assertEquals(1, requests.size)
    }

    @Test
    fun `malformed preview payloads fail before prefetch`() {
        for (payload in listOf("not json", """{"illusts":{}}""", """{"error":{"message":"unavailable"}}""")) {
            assertFailsWith<IOException> { run { 200 to payload } }
        }
        assertTrue(work.isEmpty())
    }

    @Test
    fun `invalid dates and post requests keep the original path`() {
        assertFalse(search.matches(Request.Builder().url("$url&start_date=not-a-date").build()))
        assertFalse(search.matches(Request.Builder().url("$url&start_date=2026-12-01&end_date=2026-01-01").build()))
        assertFalse(search.matches(Request.Builder().url(url).post("".toRequestBody()).build()))
    }

    @Test
    fun `cloned clients retain connection and authentication settings`() {
        val client = OkHttpClient.Builder().addInterceptor { it.proceed(it.request()) }.build()
        val wrapped = search.wrapClient(client) { native, request -> (native as OkHttpClient).newCall(request as Request) } as OkHttpClient
        assertSame(client.dispatcher, wrapped.dispatcher)
        assertSame(client.connectionPool, wrapped.connectionPool)
        assertSame(client.authenticator, wrapped.authenticator)
        assertEquals(1, client.interceptors.size)
        assertEquals(2, wrapped.interceptors.size)
    }
}
