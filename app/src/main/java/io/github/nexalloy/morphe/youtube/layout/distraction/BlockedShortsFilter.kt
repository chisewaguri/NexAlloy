package io.github.nexalloy.morphe.youtube.layout.distraction

import app.morphe.extension.shared.patches.components.Filter
import app.morphe.extension.shared.patches.components.StringFilterGroup

internal class BlockedShortsFilter : Filter() {
    init {
        addIdentifierCallbacks(StringFilterGroup(
            null, "shorts_shelf", "inline_shorts", "shorts_grid", "shorts_video_cell", "shorts_pivot_item"
        ))
        addPathCallbacks(StringFilterGroup(null, "reel_carousel.e"))
    }
}
