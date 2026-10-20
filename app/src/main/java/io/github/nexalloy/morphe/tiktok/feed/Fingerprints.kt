package io.github.nexalloy.morphe.tiktok.feed

import io.github.nexalloy.morphe.Fingerprint

private const val AWEME = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"

internal object FeedItemListGetItemsFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;",
    name = "getItems",
    returnType = "Ljava/util/List;",
    parameters = emptyList(),
)

internal object AwemeIsAdFingerprint : Fingerprint(
    definingClass = AWEME,
    name = "isAd",
    returnType = "Z",
    parameters = emptyList(),
)

internal object AwemeIsSoftAdFingerprint : Fingerprint(
    definingClass = AWEME,
    name = "isSoftAd",
    returnType = "Z",
    parameters = emptyList(),
)

internal object AwemeIsWithPromotionalMusicFingerprint : Fingerprint(
    definingClass = AWEME,
    name = "isWithPromotionalMusic",
    returnType = "Z",
    parameters = emptyList(),
)

internal object AwemeGetAwemeRawAdFingerprint : Fingerprint(
    definingClass = AWEME,
    name = "getAwemeRawAd",
    returnType = "Lcom/ss/android/ugc/aweme/feed/model/AwemeRawAd;",
    parameters = emptyList(),
)

internal object AwemeExtKtIsPseudoAdFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/feed/model/AwemeExtKt;",
    name = "isPseudoAd",
    returnType = "Z",
    parameters = listOf(AWEME),
)
