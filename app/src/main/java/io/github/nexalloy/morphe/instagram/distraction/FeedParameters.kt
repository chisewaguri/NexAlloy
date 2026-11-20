package io.github.nexalloy.morphe.instagram.distraction

internal fun followingFeedParameters(parameters: Map<String, Any?>): Map<String, Any?> {
    val source = parameters["pagination_source"]
    if (source != null && source != "feed_recs") return parameters
    return parameters + ("pagination_source" to "following")
}
