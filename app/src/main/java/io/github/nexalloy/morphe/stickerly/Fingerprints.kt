package io.github.nexalloy.morphe.stickerly

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint

private const val SUBSCRIPTION_MODEL =
    "Lcom/snowcorp/stickerly/android/base/domain/payment/SubscriptionModel;"

/**
 * The model class and its constructor are not obfuscated. Both the SharedPreferences
 * cache reader and the server-response mapper build every model through it, so one
 * hook here covers both paths without depending on obfuscated class names.
 */
internal object SubscriptionConstructorFingerprint : Fingerprint(
    definingClass = SUBSCRIPTION_MODEL,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("Z", "Ljava/lang/String;", "Ljava/util/Date;", "Ljava/util/Date;"),
)

/**
 * Optional safeguard for a SubscriptionModel default that the app may have loaded
 * before this patch ran. The class name is obfuscated, so this is matched only by
 * the stable preference key and return type.
 */
internal object SubscriptionCacheReadFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = SUBSCRIPTION_MODEL,
    parameters = emptyList(),
    strings = listOf("subscription_model"),
)
