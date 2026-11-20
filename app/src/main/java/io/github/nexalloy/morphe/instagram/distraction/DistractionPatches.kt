package io.github.nexalloy.morphe.instagram.distraction

import android.app.Activity
import de.robv.android.xposed.XC_MethodReplacement
import io.github.nexalloy.PatchExecutor
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.shared.distraction.withoutItems
import io.github.nexalloy.patch
import java.lang.reflect.Modifier

internal val FollowingOnly = patch(
    name = "Following-only home feed",
    description = "Requests the Following feed in place of Instagram's default home feed.",
    use = false,
) {
    val parameters = ::homeRequestParameters.field
    val constructors = parameters.declaringClass.declaredConstructors.filterNot { it.isSynthetic }
    check(constructors.isNotEmpty())
    constructors.forEach { constructor ->
        constructor.hookMethod {
            after {
                if (it.hasThrowable()) return@after
                @Suppress("UNCHECKED_CAST")
                val original = parameters.get(it.thisObject) as? Map<String, Any?> ?: return@after
                val updated = followingFeedParameters(original)
                if (updated !== original) parameters.set(it.thisObject, updated)
            }
        }
    }
}

internal val LimitHomePagination = patch(
    name = "Disable home feed pagination",
    description = "Keeps the first page of the home feed and prevents loading additional pages on scroll.",
    use = false,
) {
    val more = ::homeMoreAvailable.field
    val autoLoad = ::homeAutoLoadMore.field
    HomeResponseFingerprint.hookMethod {
        after {
            val response = it.result ?: return@after
            more.setBoolean(response, false)
            autoLoad.setBoolean(response, false)
        }
    }
}

internal val HideHomeFeed = patch(
    name = "Disable home feed",
    description = "Removes the home feed's posts and stops pagination. Keeps Home available for Stories and DMs.",
    use = false,
) {
    val items = ::homeItems.field
    dependsOn(LimitHomePagination)
    HomeResponseFingerprint.hookMethod {
        after { it.result?.let { response -> items.set(response, ArrayList<Any>()) } }
    }
}

internal val HideExplore = patch(
    name = "Hide Explore recommendations",
    description = "Removes the Explore grid and stops its pagination. Keeps search available.",
    use = false,
) {
    val items = ::exploreItems.field
    val clusters = ::exploreClusters.field
    val more = ::exploreMoreAvailable.field
    ExploreResponseFingerprint.hookMethod {
        after {
            val response = it.result ?: return@after
            items.set(response, ArrayList<Any>())
            clusters.set(response, ArrayList<Any>())
            more.setBoolean(response, false)
        }
    }
}

internal val HideSuggestedContent = patch(
    name = "Hide suggested home content",
    description = "Removes suggested Reels, Stories, hashtags and shops from the home feed.",
    use = false,
) {
    val suggestions = ::feedItemSuggestions.fieldList
    check(suggestions.isNotEmpty())
    filterHomeItems { item -> suggestions.any { it.get(item) != null } }
}

internal val HideSuggestedAccounts = patch(
    name = "Hide suggested accounts on Home",
    description = "Removes suggested account cards from the home feed.",
    use = false,
) {
    val suggestions = ::feedItemAccountSuggestions.fieldList
    check(suggestions.isNotEmpty())
    filterHomeItems { item -> suggestions.any { it.get(item) != null } }
}

internal val HideReels = patch(
    name = "Hide Reels",
    description = "Removes Reels from the tab bar, profile tabs and the home feed. Blocks dedicated Reels launchers.",
    use = false,
) {
    val clips = ::feedItemClips.field
    val media = ::feedItemMedia.field
    val productType = ::mediaProductType.method
    val productTypeOwner = productType.declaringClass
    val mediaData = media.type.takeIf { it != productTypeOwner }?.declaredFields
        ?.single { it.type == productTypeOwner }?.apply { isAccessible = true }
    val tabs = ::navigationTabs.method
    val profileTabs = ProfileTabsFingerprint.method
    val launchers = ::reelsLaunchers.dexMethodList.map { it.toMethod() }
    check(launchers.isNotEmpty())
    val routes = listOf(::clipsUrlHandler.method) + ::dedicatedClipsUrlHandlers.dexMethodList.map { it.toMethod() }

    filterHomeItems { item ->
        clips.get(item) != null || media.get(item)?.let { post ->
            val owner = if (mediaData == null) post else mediaData.get(post)
            owner != null && productType.invoke(owner) == "clips"
        } == true
    }
    launchers.forEach { it.hookMethod(XC_MethodReplacement.returnConstant(null)) }
    routes.forEach { route ->
        val dispatchesToActivity = Modifier.isStatic(route.modifiers)
        route.hookMethod {
            before {
                val receiver = if (dispatchesToActivity) it.args[0] else it.thisObject
                if (receiver is Activity && receiver.javaClass.name in clipsUrlHandlerClasses) {
                    receiver.finish()
                    it.result = null
                }
            }
        }
    }
    tabs.hookMethod {
        after {
            val original = it.result as? List<*> ?: return@after
            val filtered = original.withoutItems { (it as? Enum<*>)?.name == "CLIPS" }
            if (filtered.isNotEmpty()) it.result = filtered
        }
    }
    profileTabs.hookMethod {
        after {
            val original = it.result as? List<*> ?: return@after
            it.result = original.withoutItems { it == "profile_clips" }
        }
    }
}

private fun PatchExecutor.filterHomeItems(remove: (Any) -> Boolean) {
    val items = ::homeItems.field
    HomeResponseFingerprint.hookMethod {
        after {
            val response = it.result ?: return@after
            val original = items.get(response) as? List<*> ?: return@after
            val filtered = original.withoutItems { item -> item != null && remove(item) }
            if (filtered !== original) items.set(response, filtered)
        }
    }
}
