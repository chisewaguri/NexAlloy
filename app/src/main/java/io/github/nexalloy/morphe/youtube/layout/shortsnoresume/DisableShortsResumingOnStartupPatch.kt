package io.github.nexalloy.morphe.youtube.layout.shortsnoresume

import app.morphe.extension.youtube.patches.DisableShortsResumingOnStartupPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.insertLiteralOverride
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_03_or_greater
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_30_or_greater
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_41_or_greater
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

@Volatile
internal var blockShortsResume = false

private fun allowShortsResume(original: Boolean) =
    !blockShortsResume && DisableShortsResumingOnStartupPatch.disableShortsResumingOnStartup(original)

internal val ShortsResumeHook = patch {
    dependsOn(VersionCheck)

    if (is_21_03_or_greater) {
        (if (is_21_41_or_greater) UserWasInShortsEvaluateFingerprint
        else if (is_21_30_or_greater) UserWasInShortsEvaluateLegacy2130Fingerprint
        else UserWasInShortsEvaluateLegacy2103Fingerprint).hookMethod(
            scopedHook(
                UserWasInShortsEvaluateAnchorFingerprint.method
            ) {
                after {
                    it.result = allowShortsResume(it.result as Boolean)
                }
            })
    } else {
        // TODO
    }

    insertLiteralOverride(45358360L, ::allowShortsResume)
}

val DisableShortsResumingOnStartup = patch(
    name = "Disable Shorts resuming on startup",
    description = "Adds an option to disable Shorts from resuming on app startup when Shorts were last being watched.",
) {
    dependsOn(ShortsResumeHook)
    PreferenceScreen.SHORTS.addPreferences(
        SwitchPreference("morphe_disable_shorts_resuming_on_startup"),
    )
}
