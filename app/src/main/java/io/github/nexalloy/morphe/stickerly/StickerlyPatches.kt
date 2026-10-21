package io.github.nexalloy.morphe.stickerly

import de.robv.android.xposed.XposedHelpers
import io.github.nexalloy.patch
import java.lang.reflect.Method
import java.util.Date

val UnlockPlus = patch(
    name = "Unlock Plus",
    description = "Unlocks Sticker.ly Plus features that the app checks locally.",
) {
    listOf(SubscriptionCacheReadFingerprint, SubscriptionResponseMapperFingerprint).forEach { fingerprint ->
        fingerprint.hookMethod {
            before {
                it.result = XposedHelpers.newInstance(
                    (it.method as Method).returnType, true, "plus", Date(0), Date(4102444800000),
                )
            }
        }
    }
}

val StickerlyPatches = arrayOf(UnlockPlus)
