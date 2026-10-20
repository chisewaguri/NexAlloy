package io.github.nexalloy.morphe.tiktok.core

import android.app.Activity
import android.os.Build
import android.view.Window
import android.view.WindowManager
import de.robv.android.xposed.XposedHelpers
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch

val StopVideoLooping = patch(
    name = "Stop video looping",
    description = "Stops videos at the end instead of replaying them.",
    use = false,
) {
    VideoEngineSetLoopingFingerprint.hookMethod {
        before { it.args[0] = false }
    }
}

val DownloadsWithoutWatermark = patch(
    name = "Downloads without watermark",
    description = "Enables TikTok's video download option without watermark transcoding.",
) {
    val code = AclCommonShareCodeFingerprint.method
    val showType = AclCommonShareShowTypeFingerprint.method
    val transcode = AclCommonShareTranscodeFingerprint.method
    code.hookMethod { before { it.result = 0 } }
    showType.hookMethod { before { it.result = 2 } }
    transcode.hookMethod { before { it.result = 1 } }
}

val DisableScreenCaptureDetection = patch(
    name = "Disable screen capture detection",
    description = "Disables capture detection and secure-window screenshot protection.",
) {
    val added = ClearModeOnDisplayAddedFingerprint.method
    val removed = ClearModeOnDisplayRemovedFingerprint.method
    val setFlags = Window::class.java.getDeclaredMethod(
        "setFlags", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
    )
    val layoutMethods = XposedHelpers.findClass("android.view.WindowManagerGlobal", classLoader)
        .declaredMethods.filter { method ->
            method.name in listOf("addView", "updateViewLayout") &&
                method.parameterTypes.contains(android.view.ViewGroup.LayoutParams::class.java)
        }
    check(layoutMethods.isNotEmpty())
    val captureMethods = if (Build.VERSION.SDK_INT >= 34) {
        val callback = XposedHelpers.findClass("android.app.Activity\$ScreenCaptureCallback", classLoader)
        listOf(
            Activity::class.java.getDeclaredMethod(
                "registerScreenCaptureCallback", java.util.concurrent.Executor::class.java, callback,
            ),
            Activity::class.java.getDeclaredMethod("unregisterScreenCaptureCallback", callback),
        )
    } else emptyList()

    added.hookMethod { before { it.result = null } }
    removed.hookMethod { before { it.result = null } }
    setFlags.hookMethod {
        before {
            it.args[0] = (it.args[0] as Int) and WindowManager.LayoutParams.FLAG_SECURE.inv()
            it.args[1] = (it.args[1] as Int) or WindowManager.LayoutParams.FLAG_SECURE
        }
    }
    layoutMethods.forEach { method ->
        method.hookMethod {
            before {
                it.args.forEachIndexed { index, arg ->
                    val original = arg as? WindowManager.LayoutParams ?: return@forEachIndexed
                    if (original.flags and WindowManager.LayoutParams.FLAG_SECURE == 0) return@forEachIndexed
                    it.args[index] = WindowManager.LayoutParams().apply {
                        copyFrom(original)
                        flags = flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                    }
                }
            }
        }
    }
    captureMethods.forEach { method -> method.hookMethod { before { it.result = null } } }
}
