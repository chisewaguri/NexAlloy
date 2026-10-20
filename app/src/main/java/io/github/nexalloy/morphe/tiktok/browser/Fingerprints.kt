package io.github.nexalloy.morphe.tiktok.browser

import io.github.nexalloy.morphe.Fingerprint

internal object SparkThirdRouterOpenFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "Lcom/bytedance/hybrid/spark/third/router/SparkThirdContext;",
    ),
    strings = listOf("ContainerId", "Context_startActivity_1"),
)
