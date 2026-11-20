package io.github.nexalloy.morphe.instagram

import io.github.nexalloy.morphe.instagram.distraction.FollowingOnly
import io.github.nexalloy.morphe.instagram.distraction.HideExplore
import io.github.nexalloy.morphe.instagram.distraction.HideHomeFeed
import io.github.nexalloy.morphe.instagram.distraction.HideReels
import io.github.nexalloy.morphe.instagram.distraction.HideSuggestedAccounts
import io.github.nexalloy.morphe.instagram.distraction.HideSuggestedContent
import io.github.nexalloy.morphe.instagram.distraction.LimitHomePagination
import io.github.nexalloy.revanced.meta.MetaPatches

val InstagramPatches = MetaPatches + arrayOf(
    HideReels,
    HideExplore,
    HideSuggestedContent,
    HideSuggestedAccounts,
    FollowingOnly,
    LimitHomePagination,
    HideHomeFeed,
)
