package io.github.nexalloy.morphe.pixiv

import android.accounts.Account
import android.accounts.AccountManager
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch

const val POPULAR_PICKS_MIN_BOOKMARKS = "pixiv_popular_min_bookmarks"

val UnlockPremium = patch(
    name = "Unlock premium",
    description = "Unlocks pixiv Premium features that the app checks locally. " +
        "Server-side features, including full popularity sorting, still require a Premium subscription.",
) {
    OAuthUserIsPremiumFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))
    // The signed-in user's premium flag is restored from AccountManager on startup, not from the API.
    AccountManager::class.java.getMethod("getUserData", Account::class.java, String::class.java).hookMethod {
        before { if (it.args[1] == "isPremium") it.result = "true" }
    }
}

val FreePopularSearchPatch = patch(
    name = "Free popular search",
    description = "Shows Pixiv's preview first, then popular tagged works in stable batches ranked by bookmark count. " +
        "Supports keyword, AI and date filters. Untagged works may be missed. " +
        "Other filters and gender rankings use normal search.",
    use = false,
) {
    dependsOn(UnlockPremium)
    val preferences = xposed.getRemotePreferences(lpparam.packageName)
    val search = FreePopularSearch(classLoader, minimumBookmarks = {
        preferences?.getString(POPULAR_PICKS_MIN_BOOKMARKS, "0")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    })
    OkHttpNewCallFingerprint.hookMethod {
        before { param ->
            if (search.matches(param.args[0])) {
                val newCall = param.method
                val client = search.wrapClient(param.thisObject) { originalClient, request ->
                    XposedBridge.invokeOriginalMethod(newCall, originalClient, arrayOf(request))!!
                }
                param.result = XposedBridge.invokeOriginalMethod(newCall, client, param.args)
            }
        }
    }
}

val PixivPatches = arrayOf(UnlockPremium, FreePopularSearchPatch)
