package io.github.nexalloy.morphe.stickerly

import de.robv.android.xposed.XposedHelpers
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch
import java.lang.reflect.Method
import java.util.Date

val UnlockPlus = patch(
    name = "Unlock Plus",
    description = "Unlocks Sticker.ly Plus features that the app checks locally.",
) {
    // Every SubscriptionModel comes through this constructor, including cache reads
    // and server responses mapped into the model.
    SubscriptionConstructorFingerprint.hookMethod {
        before {
            if (it.args[0] == true && it.args[1] == "plus") return@before
            it.args[0] = true
            it.args[1] = "plus"
            it.args[2] = Date(0)
            it.args[3] = Date(4102444800000)
        }
    }
    // Covers a SubscriptionModel default that was loaded before this patch ran.
    SubscriptionCacheReadFingerprint.memberOrNull?.hookMethod {
        before {
            it.result = XposedHelpers.newInstance(
                (it.method as Method).returnType, true, "plus", Date(0), Date(4102444800000),
            )
        }
    }
}

val StickerlyPatches = arrayOf(UnlockPlus)
