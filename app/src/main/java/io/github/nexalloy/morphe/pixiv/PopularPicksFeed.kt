package io.github.nexalloy.morphe.pixiv

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal typealias PicksFetch = (String, PopularPicksFeed.Cancellation) -> JsonObject

internal class PopularPicksFeed(
    private val executor: Executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Pixiv popular picks").apply { isDaemon = true }
    },
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val sessions = LinkedHashMap<String, Session>(4, 0.75f, true)

    fun matches(url: String): Boolean = route(url) != null

    fun load(
        url: String, owner: String, minimum: Int,
        fetch: PicksFetch, prefetch: PicksFetch, cancelled: () -> Boolean,
    ): JsonObject {
        if (cancelled()) throw IOException("Canceled")
        val route = route(url) ?: throw IOException("Unsupported popular picks search")
        val index = if (route.query.containsKey("nexalloy_picks")) {
            route.query["nexalloy_page"]?.toIntOrNull()?.takeIf { it >= 0 }
                ?: throw IOException("Invalid popular picks page. Refresh the search.")
        } else 0
        val session = synchronized(sessions) {
            val expired = sessions.values.filter { now() - it.accessed >= CACHE_LIFETIME }
            expired.forEach { sessions.remove(it.id); it.cancel() }
            val id = route.query["nexalloy_picks"]
            val session = if (id != null) {
                sessions[id]?.takeIf { it.owner == owner && it.minimum == minimum && it.route.path == route.path }
                    ?: throw IOException("Popular picks expired. Refresh the search.")
            } else {
                sessions.values.firstOrNull {
                    it.owner == owner && it.minimum == minimum && it.route == route && it.hasFirstPage()
                } ?: Session(UUID.randomUUID().toString(), owner, minimum, route).also { sessions[it.id] = it }
            }
            while (sessions.size > MAX_SEARCHES) {
                val oldest = sessions.keys.first()
                sessions.remove(oldest)?.cancel()
            }
            session.accessed = now()
            session
        }
        val (job, created) = session.job(index, fetch)
        if (created) job.task.run()
        val result = try {
            await(job, cancelled)
        } catch (error: Exception) {
            if (error is ExecutionException || error is CancellationException) session.remove(index, job)
            when (error) {
                is ExecutionException -> throw error.cause ?: error
                is InterruptedException -> {
                    job.cancel()
                    Thread.currentThread().interrupt()
                    throw IOException("Canceled", error)
                }
                is CancellationException -> throw IOException("Canceled", error)
                else -> throw error
            }
        }
        if (!result["next_url"].isJsonNull) {
            val (next, new) = session.job(index + 1, prefetch)
            if (new) executor.execute(next.task)
        }
        return result.deepCopy()
    }

    private fun await(job: Job, cancelled: () -> Boolean): JsonObject {
        while (true) {
            if (cancelled() || Thread.currentThread().isInterrupted) {
                job.cancel()
                throw IOException("Canceled")
            }
            try {
                return job.task.get(100, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                continue
            }
        }
    }

    internal class Cancellation {
        @Volatile private var stopped = false
        private var cancelCall: (() -> Unit)? = null

        fun check() { if (stopped || Thread.currentThread().isInterrupted) throw IOException("Canceled") }
        @Synchronized fun attach(action: (() -> Unit)?) {
            cancelCall = action
            if (stopped) action?.invoke()
        }
        @Synchronized fun cancel() { stopped = true; cancelCall?.invoke() }
    }

    private class Job(block: (Cancellation) -> JsonObject) {
        val cancellation = Cancellation()
        val task = FutureTask { try { block(cancellation) } finally { cancellation.attach(null) } }
        // A waiter can cancel after compute commits a page but before FutureTask publishes it.
        fun cancel() { cancellation.cancel() }
    }

    private inner class Session(val id: String, val owner: String, val minimum: Int, val route: Route) {
        var accessed = now()
        private val pages = LinkedHashMap<Int, Job>()
        @Volatile private var nextIndex = 0
        private var state = State(false, route.tagUrl(minimum), emptyList(), emptySet(), emptySet())

        fun hasFirstPage() = synchronized(pages) { nextIndex == 0 || pages.containsKey(0) }
        fun cancel() = synchronized(pages) { pages.values.forEach(Job::cancel) }
        fun remove(index: Int, job: Job) = synchronized(pages) { if (pages[index] === job) pages.remove(index) }

        fun job(index: Int, fetch: PicksFetch): Pair<Job, Boolean> = synchronized(pages) {
            pages[index]?.let { return it to false }
            if (index != nextIndex || index !in 0 until MAX_PAGES) throw IOException("Popular picks page expired. Refresh the search.")
            val job = Job { cancellation -> compute(index, fetch, cancellation) }
            pages[index] = job
            while (pages.size > CACHED_PAGES) pages.remove(pages.keys.first())
            job to true
        }

        @Synchronized private fun compute(index: Int, fetch: PicksFetch, cancellation: Cancellation): JsonObject {
            if (index != nextIndex) throw IOException("Popular picks page expired. Refresh the search.")
            var working = state.copy()
            val candidates = linkedMapOf<Long, JsonObject>()
            val overflow = linkedMapOf<Long, JsonObject>()
            fun addItem(item: JsonObject) {
                val id = item["id"]?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull() ?: return
                val count = item["total_bookmarks"]?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull() ?: return
                if (id <= 0 || id in working.seen || count < minimum || id in candidates || id in overflow) return
                if (candidates.size < BATCH_SIZE * 2) candidates[id] = item else overflow[id] = item
            }
            fun add(body: JsonObject) {
                val items = body[route.itemsKey]?.takeIf { it.isJsonArray }?.asJsonArray
                    ?: throw IOException("Pixiv response has no ${route.itemsKey} array")
                for (element in items) {
                    if (!element.isJsonObject) continue
                    addItem(element.asJsonObject)
                }
            }
            if (!working.previewDone) {
                cancellation.check()
                add(fetch(route.previewUrl(), cancellation))
                working = working.copy(previewDone = true)
            }
            working.buffer.forEach(::addItem)
            var requests = 0
            val previewHasResults = index == 0 && candidates.isNotEmpty()
            while (!previewHasResults && candidates.size < BATCH_SIZE * 2 && working.next != null && requests < MAX_REQUESTS) {
                cancellation.check()
                val source = working.next!!
                if (source in working.visited) throw IOException("Pixiv repeated a popular picks page")
                val body = fetch(source, cancellation)
                add(body)
                val next = body["next_url"]?.takeUnless { it.isJsonNull }?.asString
                if (next != null && (!safeSource(next, route.path) || next == source || next in working.visited)) {
                    throw IOException("Invalid Pixiv search continuation")
                }
                working = working.copy(next = next, visited = working.visited + source)
                requests++
            }
            if (candidates.isEmpty() && working.next != null) {
                // Retry scans the next source pages without relaxing the user's minimum.
                state = working
                throw IOException("No works meet the bookmark minimum in these pages. Retry to search further.")
            }
            val ranked = candidates.values.sortedWith(
                compareByDescending<JsonObject> { it["total_bookmarks"].asInt }.thenByDescending { it["id"].asLong }
            )
            val selected = ranked.take(BATCH_SIZE)
            working = working.copy(
                buffer = ranked.drop(BATCH_SIZE) + overflow.values,
                seen = working.seen + selected.map { it["id"].asLong },
            )
            cancellation.check()
            state = working
            nextIndex++
            val more = nextIndex < MAX_PAGES && (working.next != null || working.buffer.isNotEmpty())
            return JsonObject().apply {
                add(route.itemsKey, JsonArray().also { array -> selected.forEach(array::add) })
                if (more) addProperty("next_url", encode(route.path, mapOf("nexalloy_picks" to id, "nexalloy_page" to nextIndex.toString())))
                else add("next_url", JsonNull.INSTANCE)
            }
        }
    }

    private data class State(
        val previewDone: Boolean, val next: String?, val buffer: List<JsonObject>, val seen: Set<Long>, val visited: Set<String>,
    )

    private data class Route(val path: String, val query: Map<String, String>) {
        val itemsKey get() = if (path.endsWith("/illust")) "illusts" else "novels"
        fun previewUrl() = encode(path.replace("/search/", "/search/popular-preview/"), query.filterKeys { it != "sort" && it != "content_type" })
        fun tagUrl(minimum: Int): String {
            val tiers = TIERS.filter { it >= if (minimum == 0) 1000 else minimum }.ifEmpty { listOf(minimum) }
            val word = "(${query.getValue("word")}) (${tiers.joinToString(" OR ") { "${it}users入り" }})"
            return encode(path, query + mapOf("word" to word, "sort" to "date_desc"))
        }
    }

    private companion object {
        const val CACHE_LIFETIME = 15 * 60 * 1000L
        const val MAX_SEARCHES = 4
        const val CACHED_PAGES = 8
        const val MAX_PAGES = 256
        const val MAX_REQUESTS = 6
        const val BATCH_SIZE = 30
        val TIERS = listOf(100, 500, 1000, 5000, 10000, 20000, 30000, 50000, 100000, 200000, 300000, 500000, 1000000)

        fun safeSource(url: String, path: String): Boolean = try {
            val uri = URI(url)
            uri.scheme == "https" && uri.host == "app-api.pixiv.net" && uri.port in listOf(-1, 443) && uri.userInfo == null && uri.path == path
        } catch (_: Exception) { false }

        fun route(url: String): Route? {
            return try {
                val uri = URI(url)
                if (uri.path !in listOf("/v1/search/illust", "/v1/search/novel") || !safeSource(url, uri.path)) return null
                val query = linkedMapOf<String, String>()
                for (part in uri.rawQuery.orEmpty().split('&')) {
                    val entry = part.split('=', limit = 2)
                    query[URLDecoder.decode(entry[0], "UTF-8")] = URLDecoder.decode(entry.getOrElse(1) { "" }, "UTF-8")
                }
                if (query.containsKey("nexalloy_picks")) return Route(uri.path, query)
                if (query["sort"] != "popular_desc" || query["word"].isNullOrBlank()) return null
                val supported = setOf(
                    "word", "sort", "search_target", "search_ai_type", "start_date", "end_date", "filter",
                    "include_translated_tag_results", "merge_plain_keyword_results",
                )
                if (query.any { (name, value) ->
                        value.isNotEmpty() && name !in supported && when (name) {
                            "content_type" -> value != if (uri.path.endsWith("/illust")) "illust_and_manga_and_ugoira" else "novel"
                            "include_potential_violation_works" -> value != "false"
                            else -> true
                        }
                    }) return null
                val start = query["start_date"]?.takeIf(String::isNotEmpty)?.let(LocalDate::parse)
                val end = query["end_date"]?.takeIf(String::isNotEmpty)?.let(LocalDate::parse)
                if (start != null && end != null && start > end) return null
                Route(uri.path, query.filterValues(String::isNotEmpty))
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: DateTimeParseException) {
                null
            }
        }

        fun encode(path: String, query: Map<String, String>) = "https://app-api.pixiv.net$path?" + query.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
    }
}
