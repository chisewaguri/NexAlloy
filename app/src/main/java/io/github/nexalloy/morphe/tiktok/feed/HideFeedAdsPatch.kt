package io.github.nexalloy.morphe.tiktok.feed

import io.github.nexalloy.patch

val HideFeedAds = patch(
    name = "Hide For You feed ads",
    description = "Filters ads and promotional music from For You feed item lists.",
) {
    val isAd = AwemeIsAdFingerprint.method
    val isSoftAd = AwemeIsSoftAdFingerprint.method
    val isWithPromotionalMusic = AwemeIsWithPromotionalMusicFingerprint.method
    val getAwemeRawAd = AwemeGetAwemeRawAdFingerprint.method
    val isPseudoAd = AwemeExtKtIsPseudoAdFingerprint.method
    FeedItemListGetItemsFingerprint.hookMethod {
        after {
            val items = it.result as? List<*> ?: return@after
            val filtered = items.filterNot { item ->
                item != null && (
                    isAd.invoke(item) == true ||
                        isSoftAd.invoke(item) == true ||
                        isWithPromotionalMusic.invoke(item) == true ||
                        getAwemeRawAd.invoke(item) != null ||
                        isPseudoAd.invoke(null, item) == true
                    )
            }
            if (filtered.size != items.size) it.result = filtered
        }
    }
}
