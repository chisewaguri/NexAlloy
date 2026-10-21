package io.github.nexalloy.morphe.stickerly

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint

private const val SUBSCRIPTION_MODEL =
    "Lcom/snowcorp/stickerly/android/base/domain/payment/SubscriptionModel;"

internal object SubscriptionCacheReadFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = SUBSCRIPTION_MODEL,
    parameters = emptyList(),
    strings = listOf("subscription_model"),
)

internal object SubscriptionResponseMapperFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = SUBSCRIPTION_MODEL,
    parameters = listOf("Lcom/snowcorp/stickerly/android/base/data/serverapi/SubscriptionResponse;"),
)
