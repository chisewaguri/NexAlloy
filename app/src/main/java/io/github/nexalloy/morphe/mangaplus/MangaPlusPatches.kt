package io.github.nexalloy.morphe.mangaplus

import io.github.nexalloy.patch

val UnlockDeluxe = patch(
    name = "Unlock Deluxe",
    description = "Treats the account as Deluxe. Chapters the server refuses still stay locked.",
) {
    PlanTypeParserFingerprint.hookMethod {
        before { it.args[0] = "deluxe" }
    }
}

val MangaPlusPatches = arrayOf(UnlockDeluxe)
