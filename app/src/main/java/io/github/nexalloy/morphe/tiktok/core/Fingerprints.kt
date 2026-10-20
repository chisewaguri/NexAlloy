package io.github.nexalloy.morphe.tiktok.core

import io.github.nexalloy.morphe.Fingerprint
import org.luckypray.dexkit.query.enums.StringMatchType

internal object VideoEngineSetLoopingFingerprint : Fingerprint(
    definingClass = "Lcom/ss/ttvideoengine/TTVideoEngine;",
    name = "setLooping",
    returnType = "V",
    parameters = listOf("Z"),
)

internal object AclCommonShareCodeFingerprint : Fingerprint(
    name = "getCode",
    returnType = "I",
    parameters = emptyList(),
    custom = { declaredClass { className(".ACLCommonShare", StringMatchType.EndsWith) } },
)

internal object AclCommonShareShowTypeFingerprint : Fingerprint(
    name = "getShowType",
    returnType = "I",
    parameters = emptyList(),
    custom = { declaredClass { className(".ACLCommonShare", StringMatchType.EndsWith) } },
)

internal object AclCommonShareTranscodeFingerprint : Fingerprint(
    name = "getTranscode",
    returnType = "I",
    parameters = emptyList(),
    custom = { declaredClass { className(".ACLCommonShare", StringMatchType.EndsWith) } },
)

internal object ClearModeOnDisplayAddedFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("I"),
    strings = listOf("[onDisplayAdded]"),
    custom = { declaredClass { className(".ClearModePanelComponent", StringMatchType.EndsWith) } },
)

internal object ClearModeOnDisplayRemovedFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("I"),
    strings = listOf("[onDisplayRemoved]"),
    custom = { declaredClass { className(".ClearModePanelComponent", StringMatchType.EndsWith) } },
)
