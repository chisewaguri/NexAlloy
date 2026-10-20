package io.github.nexalloy.morphe.tiktok.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.nexalloy.callMethod
import io.github.nexalloy.getObjectField
import io.github.nexalloy.patch

val OpenExternalLinks = patch(
    name = "Open profile links externally",
    description = "Opens profile website links in the system browser.",
) {
    SparkThirdRouterOpenFingerprint.hookMethod {
        before {
            val context = it.args[0] as? Context ?: return@before
            val target = it.args[1] ?: return@before
            val params = target.getObjectField("defaultParams") as? Map<*, *>
            val scene = target.getObjectField("seclinkConfig")?.callMethod("getScene")
            if (scene == "bio_url" || params?.get("sec_link_scene") == "bio_url") {
                if (openBrowser(context, target.getObjectField("url") as? String)) it.result = null
            }
        }
    }
}

internal fun externalUrl(source: String?): Uri? {
    var value = source?.trim() ?: return null
    repeat(4) {
        val uri = Uri.parse(value)
        when {
            uri.scheme == null -> value = "https://$value"
            uri.scheme.equals("aweme", ignoreCase = true) ->
                value = uri.getQueryParameter("url") ?: return null
            uri.getQueryParameter("target") != null -> value = uri.getQueryParameter("target")!!
            else -> return uri.takeIf {
                (it.scheme.equals("http", true) || it.scheme.equals("https", true)) &&
                    !it.host.isNullOrEmpty()
            }
        }
    }
    return null
}

private fun openBrowser(context: Context, source: String?): Boolean {
    val uri = externalUrl(source) ?: return false
    val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
