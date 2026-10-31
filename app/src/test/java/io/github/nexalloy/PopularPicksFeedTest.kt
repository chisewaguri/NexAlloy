package io.github.nexalloy

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.github.nexalloy.morphe.pixiv.PopularPicksFeed
import io.github.nexalloy.morphe.pixiv.PicksFetch
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PopularPicksFeedTest {
    private class QueueExecutor : Executor {
        val work = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { work.add(command) }
        fun next() { work.removeFirst().run() }
    }

    private val executor = QueueExecutor()
    private var time = 0L
    private val feed = PopularPicksFeed(executor) { time }
    private val url = "https://app-api.pixiv.net/v1/search/illust?word=blue%20sky&sort=popular_desc&search_target=partial_match_for_tags&search_ai_type=0"
    private val fetched = mutableListOf<String>()
    private val next = "https://app-api.pixiv.net/v1/search/illust?word=tagged&sort=date_desc&offset=30"

    private fun page(vararg items: Pair<Int, Int>, next: String? = null, key: String = "illusts") = JsonObject().apply {
        add(key, JsonArray().apply {
            items.forEach { (id, count) -> add(JsonObject().apply {
                addProperty("id", id)
                addProperty("total_bookmarks", count)
                addProperty("title", "artwork $id")
            }) }
        })
        add("next_url", next?.let { com.google.gson.JsonPrimitive(it) } ?: JsonNull.INSTANCE)
    }

    private fun load(
        url: String = this.url,
        minimum: Int = 0,
        owner: String = "account",
        fetch: (String) -> JsonObject,
    ): JsonObject {
        val source: PicksFetch = { target, cancellation ->
            cancellation.check()
            fetched.add(target)
            fetch(target)
        }
        return feed.load(url, owner, minimum, source, source) { false }
    }

    private fun JsonObject.ids(key: String = "illusts") = getAsJsonArray(key).map { it.asJsonObject["id"].asInt }
    private fun JsonObject.next() = get("next_url").asString

    @Test
    fun `first screen uses the unrestricted preview and prefetches only one batch`() {
        val first = load { target ->
            if (target.contains("popular-preview")) page(1 to 9000)
            else page(2 to 8000)
        }
        assertEquals(listOf(1), first.ids())
        assertEquals(1, fetched.size)
        assertFalse(fetched.single().contains("start_date"))
        assertEquals(1, executor.work.size)
        executor.next()
        assertTrue(executor.work.isEmpty())
        val second = load(first.next()) { error("Prefetched page should be cached") }
        assertEquals(listOf(2), second.ids())
    }

    @Test
    fun `tag batches combine tiers and rank actual counts without repeating preview works`() {
        var tagged = 0
        val first = load { target ->
            if (target.contains("popular-preview")) page(1 to 10000)
            else if (++tagged == 1) page(1 to 10000, 2 to 1000, next = next)
            else page(2 to 1000, 3 to 9000)
        }
        executor.next()
        val second = load(first.next()) { error("already prefetched") }
        assertEquals(listOf(3, 2), second.ids())
        val query = URLDecoder.decode(URI(fetched[1]).rawQuery, "UTF-8")
        assertTrue(query.contains("(blue sky)"))
        assertTrue(query.contains("1000users入り OR"))
        assertTrue(query.contains("10000users入り"))
        assertTrue(query.contains("sort=date_desc"))
        assertEquals(listOf(1), first.ids())
    }

    @Test
    fun `minimum uses measured counts and never lowers itself to fill a batch`() {
        val result = load(minimum = 5000) { target ->
            if (target.contains("popular-preview")) page(1 to 200)
            else page(2 to 4999, 3 to 5000, 4 to 12000)
        }
        assertEquals(listOf(4, 3), result.ids())
        assertTrue(result["next_url"].isJsonNull)
        val query = URLDecoder.decode(URI(fetched.last()).rawQuery, "UTF-8")
        assertFalse(query.contains("1000users入り"))
        assertTrue(query.contains("5000users入り"))
    }

    @Test
    fun `cached results are immutable and separated by account and minimum`() {
        val first = load { page(1 to 5000) }
        first.getAsJsonArray("illusts").remove(0)
        assertEquals(listOf(1), load { error("should use cache") }.ids())
        assertEquals(listOf(2), load(owner = "other") { page(2 to 5000) }.ids())
        assertEquals(listOf(3), load(minimum = 1000) { page(3 to 5000) }.ids())
    }

    @Test
    fun `expired and foreign cursors never go to the server`() {
        val first = load { page(1 to 5000) }
        assertFailsWith<IOException> { load(first.next(), owner = "other") { error("foreign cursor") } }
        time = 60 * 60 * 1000L
        assertFailsWith<IOException> { load(first.next()) { error("expired cursor") } }
        assertEquals(1, fetched.size)
    }

    @Test
    fun `failed prefetch is retryable and does not consume its server cursor`() {
        var fail = true
        val first = load { target ->
            if (target.contains("popular-preview")) page(1 to 5000)
            else if (fail) { fail = false; throw IOException("offline") }
            else page(2 to 4000)
        }
        executor.next()
        assertFailsWith<IOException> { load(first.next()) { error("cached failure") } }
        val retry = load(first.next()) { page(2 to 4000) }
        assertEquals(listOf(2), retry.ids())
        assertEquals(fetched[1], fetched[2])
    }

    @Test
    fun `novel feeds preserve date and AI filters on normal pagination`() {
        val first = load(url.replace("/illust?", "/novel?") + "&start_date=2020-01-01&end_date=2021-01-01") { target ->
            if (target.contains("popular-preview")) page(1 to 5000, key = "novels")
            else page(2 to 4000, key = "novels")
        }
        executor.next()
        assertEquals(listOf(2), load(first.next()) { error("cached") }.ids("novels"))
        assertTrue(fetched.all { it.contains("start_date=2020-01-01") && it.contains("search_ai_type=0") })
    }

    @Test
    fun `unsafe next URLs and repeated source cursors fail instead of looping`() {
        val first = load { target ->
            if (target.contains("popular-preview")) page(1 to 5000)
            else page(2 to 4000, next = "https://example.com/private")
        }
        executor.next()
        assertFailsWith<IOException> { load(first.next()) { error("cached failure") } }
        assertFalse(fetched.any { it.contains("example.com") })
    }

    @Test
    fun `invalid cursor indices fail instead of replaying page zero`() {
        val first = load { page(1 to 5000) }
        val invalid = first.next().replace("nexalloy_page=1", "nexalloy_page=invalid")
        assertFailsWith<IOException> { load(invalid) { error("invalid cursor") } }
    }

    @Test
    fun `delivered pages remain stable when higher ranked candidates arrive later`() {
        var tagged = 0
        val source: (String) -> JsonObject = { target ->
            if (target.contains("popular-preview")) page(1 to 10000)
            else if (++tagged == 1) page(*Array(60) { (it + 2) to (1000 + it) }, next = next)
            else page(99 to 50000)
        }
        val first = load(fetch = source)
        executor.next()
        val second = load(first.next(), fetch = source)
        val secondIds = second.ids()
        executor.next()
        val third = load(second.next()) { error("cached") }
        assertEquals(99, third.ids().first())
        assertTrue(third.ids().none { it in secondIds })
        assertEquals(secondIds, load(first.next()) { error("retry must be stable") }.ids())
        assertEquals(listOf(1), first.ids())
    }

    @Test
    fun `cache evicts old searches and cancels their queued prefetch`() {
        val first = load { page(1 to 5000) }
        repeat(4) { n -> load(url + "&end_date=202${n + 1}-01-01") { page((n + 2) to 5000) } }
        assertFailsWith<IOException> { load(first.next()) { error("evicted") } }
        executor.next()
        assertEquals(5, fetched.size)
    }

    @Test
    fun `canceling a waiter cancels an in-flight native prefetch`() {
        val worker = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val released = CountDownLatch(1)
        val stopped = AtomicBoolean()
        val activeFeed = PopularPicksFeed(worker)
        val source: PicksFetch = { target, cancellation ->
            if (target.contains("popular-preview")) page(1 to 5000)
            else {
                cancellation.attach { stopped.set(true); released.countDown() }
                started.countDown()
                released.await(5, TimeUnit.SECONDS)
                cancellation.check()
                page(2 to 4000)
            }
        }
        try {
            val first = activeFeed.load(url, "account", 0, source, source) { false }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            var checks = 0
            assertFailsWith<IOException> { activeFeed.load(first.next(), "account", 0, source, source) { ++checks > 2 } }
            assertTrue(stopped.get())
        } finally {
            released.countDown()
            worker.shutdownNow()
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `retry after a sparse first batch continues scanning without lowering the minimum`() {
        var tagged = 0
        val source: (String) -> JsonObject = { target ->
            if (target.contains("popular-preview")) page(1 to 100)
            else {
                tagged++
                if (tagged <= 6) page(tagged + 1 to 100, next = "$next&cursor=$tagged")
                else page(99 to 5000)
            }
        }
        assertFailsWith<IOException> { load(minimum = 5000, fetch = source) }
        val retry = load(minimum = 5000, fetch = source)
        assertEquals(listOf(99), retry.ids())
        assertEquals(1, fetched.count { it.contains("popular-preview") })
        assertEquals(7, tagged)
    }

    @Test
    fun `canceling a waiter preserves a page that prefetch already completed`() {
        val first = load { target ->
            if (target.contains("popular-preview")) page(1 to 5000) else page(2 to 4000)
        }
        executor.next()
        var checks = 0
        val unused: PicksFetch = { _, _ -> error("page is already cached") }
        assertFailsWith<IOException> {
            feed.load(first.next(), "account", 0, unused, unused) { ++checks > 1 }
        }
        assertEquals(listOf(2), load(first.next()) { error("successful page must survive cancellation") }.ids())
    }

    @Test
    fun `ranking stops at sixty candidates and keeps the source overflow for later pages`() {
        var tagged = 0
        val source: (String) -> JsonObject = { target ->
            if (target.contains("popular-preview")) page(1 to 100)
            else {
                val start = tagged++ * 30 + 1
                page(*Array(30) { (start + it) to ((start + it) * 100) }, next = if (tagged < 3) "$next&cursor=$tagged" else null)
            }
        }
        val first = load(fetch = source)
        executor.next()
        var result = load(first.next(), fetch = source)
        assertEquals(61, result.ids().first())
        val ids = first.ids().toMutableList()
        while (true) {
            ids.addAll(result.ids())
            if (result["next_url"].isJsonNull) break
            executor.next()
            result = load(result.next(), fetch = source)
        }
        assertEquals((1..90).toSet(), ids.toSet())
        assertEquals(90, ids.size)
    }
}
