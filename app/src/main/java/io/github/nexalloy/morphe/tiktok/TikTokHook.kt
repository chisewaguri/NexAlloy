package io.github.nexalloy.morphe.tiktok

import io.github.nexalloy.morphe.tiktok.browser.OpenExternalLinks
import io.github.nexalloy.morphe.tiktok.feed.HideFeedAds
import io.github.nexalloy.morphe.tiktok.core.DisableScreenCaptureDetection
import io.github.nexalloy.morphe.tiktok.core.DownloadsWithoutWatermark
import io.github.nexalloy.morphe.tiktok.core.StopVideoLooping
import io.github.nexalloy.morphe.tiktok.layout.HideSuggestedAccounts
import io.github.nexalloy.morphe.tiktok.layout.ShowSeekbar

val TikTokPatches = arrayOf(
    HideFeedAds,
    DownloadsWithoutWatermark,
    DisableScreenCaptureDetection,
    StopVideoLooping,
    ShowSeekbar,
    OpenExternalLinks,
    HideSuggestedAccounts,
)
