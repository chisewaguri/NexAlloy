package io.github.nexalloy

import io.github.nexalloy.morphe.instagram.distraction.followingFeedParameters
import io.github.nexalloy.morphe.shared.distraction.withoutItems
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class DistractionFiltersTest {
    @Test
    fun followingOnlyChangesTheDefaultFeedSource() {
        assertEquals(mapOf("pagination_source" to "following"), followingFeedParameters(emptyMap()))
        assertEquals(
            mapOf("pagination_source" to "following", "max_id" to "cursor"),
            followingFeedParameters(mapOf("pagination_source" to "feed_recs", "max_id" to "cursor"))
        )
        for (source in listOf("following", "favorites", "unknown")) {
            val params = mapOf("pagination_source" to source)
            assertSame(params, followingFeedParameters(params))
        }
    }

    @Test
    fun filteredListsKeepOrderAndLeaveTheOriginalIntact() {
        val items = listOf("first", "suggestion", "second", null, "suggestion")
        assertSame(items, items.withoutItems { it == "missing" })
        assertEquals(listOf("first", "second", null), items.withoutItems { it == "suggestion" })
        assertEquals(emptyList<String>(), items.withoutItems { true })
        assertEquals(5, items.size)
    }
}
