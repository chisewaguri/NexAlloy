package io.github.nexalloy.morphe.tiktok.layout

import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch

val ShowSeekbar = patch(
    name = "Show seekbar",
    description = "Shows the native seekbar on short videos.",
) {
    val predicate = ::ShouldShowProgressBarFingerprint.method
    val showType = SetSeekBarShowTypeFingerprint.method
    predicate.hookMethod {
        before { if (it.args[0] != null) it.result = true }
    }
    showType.hookMethod {
        before {
            val index = it.args.lastIndex
            if (it.args[index] == 3 || it.args[index] == 4) it.args[index] = 0
        }
    }
}

val HideSuggestedAccounts = patch(
    name = "Hide profile recommendations",
    description = "Hides recommended-account cards on profiles.",
    use = false,
) {
    ProfileHeaderRecommendFingerprint.hookMethod(returnConstant(null))
}
