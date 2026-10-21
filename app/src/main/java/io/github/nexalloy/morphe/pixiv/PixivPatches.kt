package io.github.nexalloy.morphe.pixiv

import android.accounts.Account
import android.accounts.AccountManager
import de.robv.android.xposed.XC_MethodReplacement
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch

val UnlockPremium = patch(
    name = "Unlock premium",
    description = "Unlocks pixiv Premium features that the app checks locally.",
) {
    OAuthUserIsPremiumFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))
    // The signed-in user's premium flag is restored from AccountManager on startup, not from the API.
    AccountManager::class.java.getMethod("getUserData", Account::class.java, String::class.java).hookMethod {
        before { if (it.args[1] == "isPremium") it.result = "true" }
    }
}

val PixivPatches = arrayOf(UnlockPremium)
