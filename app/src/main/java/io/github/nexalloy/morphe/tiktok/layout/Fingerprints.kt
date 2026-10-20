package io.github.nexalloy.morphe.tiktok.layout

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.accessFlags
import io.github.nexalloy.morphe.parameters
import io.github.nexalloy.morphe.returns

val ShouldShowProgressBarFingerprint = findMethodDirect {
    findClass {
        matcher { usingStrings("homepage_hot", "FeedRecommendFragment") }
    }.findMethod {
        matcher {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL)
            returns("Z")
            parameters("Lcom/ss/android/ugc/aweme/feed/model/Aweme;")
        }
    }.filter { it.opCodes.size <= 20 }.single()
}

internal object SetSeekBarShowTypeFingerprint : Fingerprint(
    strings = listOf("seekbar show type change, change to:"),
)

internal object ProfileHeaderRecommendFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/profile/platform/business/header/business/recommend/assemble/ProfileHeaderRecommendComponent;",
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("recommend_user_card"),
)
