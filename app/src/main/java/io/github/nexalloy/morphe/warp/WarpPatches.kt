package io.github.nexalloy.morphe.warp

import de.robv.android.xposed.XC_MethodReplacement
import io.github.nexalloy.patch
import java.lang.reflect.Constructor

val SpoofWarpPlus = patch(
    name = "Spoof WARP+ Unlimited UI",
    description = "Shows the account as WARP+ Unlimited. The tunnel itself stays on your real plan.",
) {
    AccountDataConstructorFingerprint.hookMethod {
        before {
            val state = (it.method as Constructor<*>).parameterTypes[1]
            it.args[1] = state.getField("UNLIMITED").get(null)
        }
    }
}

val DisableAnalytics = patch(
    name = "Disable analytics",
    description = "Stops 1.1.1.1 from sending usage events to Firebase.",
) {
    AnalyticsBundleFingerprint.hookMethod(XC_MethodReplacement.DO_NOTHING)
    val unitClass = classLoader.loadClass("kotlin.Unit")
    val unit = unitClass.declaredFields.first { it.type == unitClass }.get(null)
    ::firebaseDispatchMethods.dexMethodList.forEach { method ->
        method.hookMethod(XC_MethodReplacement.returnConstant(unit))
    }
}

val DisableSslPinning = patch(
    name = "Disable SSL pinning",
    description = "Allows inspecting the app's API traffic with a user-installed certificate.",
    use = false,
) {
    CertificatePinnerCheckFingerprint.hookMethod(XC_MethodReplacement.DO_NOTHING)
}

val WarpPatches = arrayOf(SpoofWarpPlus, DisableAnalytics, DisableSslPinning)
