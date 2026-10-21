package io.github.nexalloy.morphe.pixiv

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.fieldAccess

private const val OAUTH_USER = "Ljp/pxv/android/domain/auth/entity/OAuthUser;"

internal object OAuthUserIsPremiumFingerprint : Fingerprint(
    definingClass = OAUTH_USER,
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(definingClass = OAUTH_USER, name = "isPremium", opcode = Opcode.IGET_BOOLEAN),
    ),
)
