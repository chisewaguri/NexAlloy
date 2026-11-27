package io.github.nexalloy.morphe.youtube.layout.distraction

import android.content.Intent
import android.view.View
import app.morphe.extension.shared.innertube.GuideResponseOuterClass.BrowseEndpoint
import app.morphe.extension.shared.innertube.GuideResponseOuterClass.NavigationEndpoint
import app.morphe.extension.shared.patches.components.Filter
import app.morphe.extension.shared.patches.components.StringFilterGroup
import app.morphe.extension.youtube.shared.NavigationBar
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import io.github.nexalloy.PatchExecutor
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.shared.distraction.withoutItems
import io.github.nexalloy.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.nexalloy.morphe.youtube.interaction.downloads.GeneratedRegistryFingerprint
import io.github.nexalloy.morphe.youtube.interaction.downloads.ProtobufClassParseByteArrayWithRegistryFingerprint
import io.github.nexalloy.morphe.youtube.layout.playlistautoplay.navigationIntentHook
import io.github.nexalloy.morphe.youtube.layout.shortsnoresume.ShortsResumeHook
import io.github.nexalloy.morphe.youtube.layout.shortsnoresume.blockShortsResume
import io.github.nexalloy.morphe.youtube.layout.startpage.GuideMilestoneHook
import io.github.nexalloy.morphe.youtube.layout.startpage.IntentActionFingerprint
import io.github.nexalloy.morphe.youtube.layout.startpage.skipHomeGuideMilestone
import io.github.nexalloy.morphe.youtube.misc.fix.videoactionbar.ProtobufListMakeImmutableFingerprint
import io.github.nexalloy.morphe.youtube.misc.fix.videoactionbar.protobufListMutableCopyMethod
import io.github.nexalloy.morphe.youtube.misc.fix.videoactionbar.watchNextItemSectionField
import io.github.nexalloy.morphe.youtube.misc.fix.videoactionbar.watchNextRowsMethod
import io.github.nexalloy.morphe.youtube.misc.fix.videoactionbar.watchNextSectionIdentifierField
import io.github.nexalloy.morphe.youtube.misc.fix.videoactionbar.watchNextSectionsField
import io.github.nexalloy.morphe.youtube.misc.litho.filter.LithoFilter
import io.github.nexalloy.morphe.youtube.misc.navigation.BrowseFragmentTargetFingerprint
import io.github.nexalloy.morphe.youtube.misc.navigation.NavigationBarHook
import io.github.nexalloy.morphe.youtube.misc.navigation.browserIdMethod
import io.github.nexalloy.morphe.youtube.misc.navigation.hookNavigationButtonCreated
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_20_or_greater
import io.github.nexalloy.morphe.youtube.shared.ShortsPlaybackIntentFingerprint
import io.github.nexalloy.morphe.youtube.shared.ShortsPlaybackIntentFingerprintLegacy
import io.github.nexalloy.patch

internal val BlockShorts = patch(
    name = "Block Shorts",
    description = "Removes Shorts cards and the Shorts tab, and blocks navigation into the Shorts player.",
    use = false,
) {
    dependsOn(VersionCheck, LithoFilter, NavigationBarHook, ShortsResumeHook)
    val playback = if (is_21_20_or_greater) ShortsPlaybackIntentFingerprint
        else ShortsPlaybackIntentFingerprintLegacy
    val playbackMethod = playback.member
    val handleIntent = IntentActionFingerprint.member
    blockShortsResume = true
    playbackMethod.hookMethod(XC_MethodReplacement.returnConstant(null))
    handleIntent.hookMethod {
        priority = XC_MethodHook.PRIORITY_LOWEST
        before {
            val intent = it.args[0] as? Intent ?: return@before
            val uri = intent.data
            val host = uri?.host?.lowercase()
            val shortsLink = (host == "youtube.com" || host?.endsWith(".youtube.com") == true) &&
                uri?.pathSegments?.firstOrNull() == "shorts"
            if (shortsLink || intent.action == "com.google.android.youtube.action.open.shorts") {
                it.args[0] = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            }
        }
    }

    addLithoFilter(BlockedShortsFilter())
    hookNavigationButtonCreated { button, view ->
        if (button == NavigationBar.NavigationButton.SHORTS) view.visibility = View.GONE
    }
}

internal val HideHomeRecommendations = patch(
    name = "Disable Home recommendations",
    description = "Opens Subscriptions when YouTube tries to open the Home feed.",
    use = false,
) {
    dependsOn(GuideMilestoneHook)
    redirectBrowsePagesToSubscriptions(setOf("FEwhat_to_watch"))
    skipHomeGuideMilestone = true
}

internal val HideExploreRecommendations = patch(
    name = "Disable Explore recommendations",
    description = "Opens Subscriptions in place of the Explore, Trending and Movies discovery feeds.",
    use = false,
) {
    redirectBrowsePagesToSubscriptions(setOf("FEexplore", "FEtrending", "FEstorefront"))
}

private fun PatchExecutor.redirectBrowsePagesToSubscriptions(blockedBrowseIds: Set<String>) {
    val getBrowseId = ::browserIdMethod.method
    val parse = ProtobufClassParseByteArrayWithRegistryFingerprint.method
    val registry = GeneratedRegistryFingerprint.method.invoke(null)
    val endpoint = NavigationEndpoint.newBuilder()
        .setBrowseEndpoint(BrowseEndpoint.newBuilder().setBrowseId("FEsubscriptions"))
        .build().toByteArray()

    BrowseFragmentTargetFingerprint.hookMethod {
        before {
            val original = it.args[0] ?: return@before
            if (getBrowseId.invoke(null, original) in blockedBrowseIds) {
                it.args[0] = parse.invoke(null, original, endpoint, registry)
            }
        }
    }
}

internal val HideRelatedVideos = patch(
    name = "Hide related videos",
    description = "Removes the related-items section from watch pages, including its continuation.",
    use = false,
) {
    val sectionsField = ::watchNextSectionsField.field
    val itemSection = ::watchNextItemSectionField.field
    val identifier = ::watchNextSectionIdentifierField.field
    val mutableCopy = ::protobufListMutableCopyMethod.method
    val makeImmutable = ProtobufListMakeImmutableFingerprint.method
    val rows = ::watchNextRowsMethod.member
    dependsOn(LithoFilter)
    rows.hookMethod {
        before {
            val response = it.args[0] ?: return@before
            val sections = sectionsField.get(response) as? List<*> ?: return@before
            val filtered = sections.withoutItems { section ->
                section?.let(itemSection::get)?.let(identifier::get) == "related-items"
            }
            if (filtered !== sections) {
                @Suppress("UNCHECKED_CAST")
                val copy = mutableCopy.invoke(null, sections) as MutableList<Any?>
                copy.clear()
                copy.addAll(filtered)
                makeImmutable.invoke(copy)
                sectionsField.set(response, copy)
            }
        }
    }
    addLithoFilter(object : Filter() {
        init {
            addPathCallbacks(StringFilterGroup(null, "|fullscreen_related_videos"))
        }
    })
}

internal val StopAutoplayRecommendations = patch(
    name = "Stop recommended videos from autoplaying",
    description = "Prevents automatic navigation to recommended videos when a video ends.",
    use = false,
) {
    navigationIntentHook({ it.name == "AUTONAV" })
}
